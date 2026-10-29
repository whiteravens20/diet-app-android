// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.content.Context
import kotlinx.serialization.Serializable
import net.whiteravens.dietapp.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * What the workflow that builds an APK publishes beside it (`version.json`): the build's version,
 * the API it was checked against, and the file with its SHA-256 and size, which a download is held to.
 */
@Serializable
data class VersionFile(
    val versionCode: Int,
    val versionName: String,
    val apiVersion: String? = null,
    val apk: String,
    val sha256: String,
    val size: Long,
)

/** Where this build asks for its newest version ([versionFile]), and the addresses it may download a build from. */
data class UpdateChannel(val versionFile: String, val downloadsFrom: String) {
    /** The address of [file], named beside the version file, or null where it would lie outside [downloadsFrom]. */
    fun addressOf(file: String): String? =
        versionFile.toHttpUrlOrNull()?.resolve(file)?.toString()?.takeIf { downloadsFrom.isNotEmpty() && it.startsWith(downloadsFrom) }

    companion object {
        /**
         * The file of a debug build's files folder that names the version file a test serves, in the
         * place of the channel a debug build does not have. A build handed out never reads it.
         */
        const val TEST_CHANNEL = "update-channel"

        /** This build's channel, or null for a build that asks nowhere. */
        fun of(context: Context): UpdateChannel? {
            if (BuildConfig.DEBUG) {
                val named = File(context.filesDir, TEST_CHANNEL).takeIf { it.isFile }?.readText()?.trim()
                if (!named.isNullOrEmpty()) return UpdateChannel(named, named.substringBeforeLast('/') + "/")
            }
            return BuildConfig.UPDATES_URL.takeIf { it.isNotEmpty() }?.let { UpdateChannel(it, BuildConfig.UPDATES_FROM) }
        }
    }
}
