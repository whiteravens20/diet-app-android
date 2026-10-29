// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Why getting a newer build stopped. */
enum class UpdateFailure {
    /** The version file names a file outside the channel's addresses: nothing was fetched. */
    NOT_FROM_CHANNEL,

    /** The download failed or was cut short. */
    DOWNLOAD,

    /** The file downloaded is not the one the version file describes. */
    CHECKSUM,

    /** The file is another application, or this one signed with another key. */
    NOT_OURS,

    /** The system's installer refused it. */
    INSTALL,

    /** The user said no to the system's question. */
    CANCELLED,
}

/** Where getting a newer build stands. */
sealed interface UpdateStep {
    data object Idle : UpdateStep

    data class Downloading(val done: Long, val of: Long) : UpdateStep

    data object Checking : UpdateStep

    /** The file is with the system's installer, which asks the user. */
    data object Handed : UpdateStep

    data class Failed(val why: UpdateFailure, val detail: String? = null) : UpdateStep
}

internal class UpdateException(val why: UpdateFailure, message: String? = null) : Exception(message)

/** Downloads a build into a file, held to the size and the SHA-256 its version file gives. */
internal class ApkDownload(private val client: OkHttpClient) {
    /** Fetches [address] into [into]; a file that is not the one described is deleted, and the failure thrown. */
    fun fetch(address: String, size: Long, sha256: String, into: File, progress: (Long) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            client.newCall(Request.Builder().url(address).build()).execute().use { response ->
                if (!response.isSuccessful) throw UpdateException(UpdateFailure.DOWNLOAD, "HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    into.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            // More than the version file says is another file, whatever follows.
                            if (total > size) throw UpdateException(UpdateFailure.CHECKSUM)
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            progress(total)
                        }
                        if (total < size) throw UpdateException(UpdateFailure.DOWNLOAD, "$total of $size bytes")
                    }
                }
            }
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(sha256, ignoreCase = true)) throw UpdateException(UpdateFailure.CHECKSUM)
        } catch (failure: UpdateException) {
            into.delete()
            throw failure
        } catch (failure: IOException) {
            into.delete()
            throw UpdateException(UpdateFailure.DOWNLOAD, failure.message)
        }
    }
}

/** Whether a downloaded file is this application, signed with the key this one is signed with. */
internal class ApkCheck(private val context: Context) {
    fun isOurs(apk: File): Boolean {
        val packages = context.packageManager
        val archive = packages.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (archive.packageName != context.packageName) return false
        val signedAs = signers(archive)
        return signedAs.isNotEmpty() && signedAs == signers(packages.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
    }

    private fun signers(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
}

/**
 * Downloads a newer build, checks it and hands it to the system's installer, which always asks the
 * user before it installs. The file is kept in the application's cache, and deleted once the system
 * has it, and when the user puts the build off.
 */
@Singleton
class UpdateInstaller @Inject constructor(@ApplicationContext private val context: Context) {
    private val download = ApkDownload(OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build())
    private val check = ApkCheck(context)
    private val folder get() = File(context.cacheDir, "updates")

    private val _step = MutableStateFlow<UpdateStep>(UpdateStep.Idle)
    val step: StateFlow<UpdateStep> = _step.asStateFlow()

    /** Whether the phone lets this application install applications. */
    fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system's page where this application is allowed to install applications. */
    fun allowance(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Downloads the build [version] names, checks it and hands it to the system's installer. */
    suspend fun get(version: VersionFile) {
        if (_step.value is UpdateStep.Downloading || _step.value == UpdateStep.Checking) return
        _step.value = UpdateStep.Downloading(0, version.size)
        _step.value = runCatching { withContext(Dispatchers.IO) { fetchAndHand(version) } }.fold(
            onSuccess = { UpdateStep.Handed },
            onFailure = { failure ->
                if (failure is UpdateException) UpdateStep.Failed(failure.why, failure.message) else UpdateStep.Failed(UpdateFailure.INSTALL, failure.message)
            },
        )
    }

    private fun fetchAndHand(version: VersionFile) {
        val channel = UpdateChannel.of(context) ?: throw UpdateException(UpdateFailure.NOT_FROM_CHANNEL)
        val address = channel.addressOf(version.apk) ?: throw UpdateException(UpdateFailure.NOT_FROM_CHANNEL)
        folder.deleteRecursively()
        folder.mkdirs()
        val apk = File(folder, "update.apk")
        download.fetch(address, version.size, version.sha256, apk) { done -> _step.value = UpdateStep.Downloading(done, version.size) }
        _step.value = UpdateStep.Checking
        if (!check.isOurs(apk)) {
            apk.delete()
            throw UpdateException(UpdateFailure.NOT_OURS)
        }
        hand(apk)
        apk.delete()
    }

    /** Writes [apk] into an install session and commits it; the system answers [MainActivity] with [STATUS]. */
    private fun hand(apk: File) {
        val installer = context.packageManager.packageInstaller
        abandonSessions()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val answer = Intent(context, MainActivity::class.java).setAction(STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // Mutable, for the system writes the session's status into it.
                val pending = PendingIntent.getActivity(context, id, answer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        } catch (failure: Exception) {
            installer.abandonSession(id)
            throw UpdateException(UpdateFailure.INSTALL, failure.message)
        }
    }

    /**
     * What the system answered of a session, sent to the activity: the question it asks the user,
     * which [ask] starts, or the end of it. A build installed ends the application, which starts anew.
     */
    fun answered(status: Intent, ask: (Intent) -> Unit) {
        when (status.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val question = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    status.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    status.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                question?.let(ask)
            }
            PackageInstaller.STATUS_SUCCESS -> _step.value = UpdateStep.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _step.value = UpdateStep.Failed(UpdateFailure.CANCELLED)
            else -> _step.value = UpdateStep.Failed(UpdateFailure.INSTALL, status.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    /** Back to the start, with no file and no session left behind. */
    fun reset() {
        folder.deleteRecursively()
        abandonSessions()
        _step.value = UpdateStep.Idle
    }

    /** A session handed over before and never answered, such as one whose question was left open, goes. */
    private fun abandonSessions() {
        val installer = context.packageManager.packageInstaller
        installer.mySessions.forEach { session -> runCatching { installer.abandonSession(session.sessionId) } }
    }

    companion object {
        /** The action of the intent the system answers an install session with. */
        const val STATUS = "net.whiteravens.dietapp.UPDATE_STATUS"
    }
}
