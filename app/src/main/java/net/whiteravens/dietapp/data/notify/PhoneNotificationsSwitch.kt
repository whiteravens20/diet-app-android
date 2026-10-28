// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import net.whiteravens.dietapp.notify.NotificationsWorker
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.ui.common.ReadingAnew
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's notifications on the phone, turned on and off: whether they are on, the periodic
 * work that reads them, as often as Android lets such work run, and a read whenever the application
 * comes to the front. Signing out turns them off and forgets what was shown.
 */
@Singleton
class PhoneNotificationsSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PhoneNotificationsStore,
) {
    val on: Flow<Boolean> = store.on

    /** Reads asked for on coming to the front, held to the screens' pace then. */
    private val reading = ReadingAnew()

    /** Both the work and a read asked for at once wait for a network. */
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun turnOn() {
        store.turn(true)
        PhoneNotifier.ensureChannel(context)
        schedule()
    }

    suspend fun turnOff() {
        store.turn(false)
        WorkManager.getInstance(context).run {
            cancelUniqueWork(WORK)
            cancelUniqueWork(NOW)
        }
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** Where the notifications are on, keeps their work scheduled as this version asks it to be: at every start. */
    suspend fun keepScheduled() {
        if (store.isOn()) schedule()
    }

    /**
     * On coming to the front, where the notifications are on: the work's read at once, rather than
     * when Android lets the work run, at most every [ReadingAnew.AT_MOST_EVERY_MILLIS].
     */
    suspend fun readNow() {
        if (!store.isOn() || !reading.due(cameToTheFront = true)) return
        reading.begun()
        val request = OneTimeWorkRequestBuilder<NotificationsWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** At signing out: off, the account's notifications taken off the phone, and what was shown forgotten. */
    suspend fun forget() {
        turnOff()
        store.clear()
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationsWorker>(EVERY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        // The run of the moment keeps its schedule; a version that asks otherwise is taken at the next run.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        /** The name the periodic work is known by, one for the application. */
        const val WORK = "account-notifications"

        /** The name of the read asked for on coming to the front, one at a time. */
        const val NOW = "account-notifications-now"

        /** The shortest period Android runs periodic work at. */
        const val EVERY_MINUTES = 15L
    }
}
