// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import net.whiteravens.dietapp.data.auth.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Has the changes waiting sent by WorkManager: once there is a network, one run at a time, a run
 * asked for while one goes on coming after it, and a run that met no network or a server that asked
 * to wait tried again later.
 */
@Singleton
class ChangesWork @Inject constructor(@ApplicationContext private val context: Context) {
    fun sendSoon() {
        val request = OneTimeWorkRequestBuilder<ChangesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    companion object {
        /** The name the work is known by, one for the application. */
        const val NAME = "changes-waiting"
    }
}

/** Sends the changes waiting, as the screens that made them would; nothing while nobody is signed in. */
class ChangesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** What the work takes from the application's graph: WorkManager makes the worker itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun queue(): ChangeQueue

        fun sender(): ChangeSender

        fun tokens(): TokenStore
    }

    override suspend fun doWork(): Result {
        val parts = EntryPointAccessors.fromApplication(applicationContext, Parts::class.java)
        if (parts.tokens().session.first() == null) return Result.success()
        return when (parts.queue().drain(parts.sender()::send)) {
            Drained.EMPTY -> Result.success()
            Drained.OFFLINE, Drained.LATER -> Result.retry()
        }
    }
}
