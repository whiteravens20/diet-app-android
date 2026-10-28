// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.notify.NewNotifications
import net.whiteravens.dietapp.data.notify.PhoneNotificationsStore
import net.whiteravens.dietapp.data.repository.UserRepository

/**
 * Reads the account's unread notifications in the background, as often as Android lets periodic
 * work run and whenever the application comes to the front, and shows on the phone those it has not
 * shown yet. Nothing while the notifications are off or nobody is signed in; a read that fails waits
 * for the next run.
 */
class NotificationsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** What the work takes from the application's graph: WorkManager makes the worker itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun store(): PhoneNotificationsStore

        fun tokens(): TokenStore

        fun newNotifications(): NewNotifications

        fun users(): UserRepository
    }

    override suspend fun doWork(): Result {
        val parts = EntryPointAccessors.fromApplication(applicationContext, Parts::class.java)
        if (!parts.store().isOn() || parts.tokens().session.first() == null) return Result.success()
        val fresh = try {
            parts.newNotifications().read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.success()
        }
        if (fresh.isEmpty()) return Result.success()
        // A name that cannot be read leaves the notification under the application's.
        val names = try {
            parts.users().profiles().associate { it.id to it.name }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        PhoneNotifier.show(applicationContext, fresh, names)
        return Result.success()
    }
}
