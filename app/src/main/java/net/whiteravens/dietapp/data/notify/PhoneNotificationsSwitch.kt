// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import net.whiteravens.dietapp.notify.NotificationsWorker
import net.whiteravens.dietapp.notify.PhoneNotifier
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's notifications on the phone, turned on and off: whether they are on, and the periodic
 * work that reads them, as often as Android lets such work run. Signing out turns them off and
 * forgets what was shown.
 */
@Singleton
class PhoneNotificationsSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PhoneNotificationsStore,
) {
    val on: Flow<Boolean> = store.on

    suspend fun turnOn() {
        store.turn(true)
        PhoneNotifier.ensureChannel(context)
        schedule()
    }

    suspend fun turnOff() {
        store.turn(false)
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** Where the notifications are on, keeps their work scheduled as this version asks it to be: at every start. */
    suspend fun keepScheduled() {
        if (store.isOn()) schedule()
    }

    /** At signing out: off, the account's notifications taken off the phone, and what was shown forgotten. */
    suspend fun forget() {
        turnOff()
        store.clear()
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationsWorker>(EVERY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // The run of the moment keeps its schedule; a version that asks otherwise is taken at the next run.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        /** The name the periodic work is known by, one for the application. */
        const val WORK = "account-notifications"

        /** The shortest period Android runs periodic work at. */
        const val EVERY_MINUTES = 15L
    }
}
