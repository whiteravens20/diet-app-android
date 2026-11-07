// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.WaterRepository
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.ui.common.attempt

/**
 * A press of an amount on a reminder of water, without opening the application: the water goes to
 * the reminded profile's day through the queue of changes, kept to be sent without a network; the
 * reminder is then read the same way, and says what was added before it goes. A press the server
 * refuses says so, and keeps the amounts.
 */
class WaterReminderReceiver : BroadcastReceiver() {
    /** What the press takes from the application's graph: Android makes the receiver itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun water(): WaterRepository

        fun notifications(): NotificationRepository

        @ApplicationScope
        fun application(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val press = WaterPress.of(intent) ?: return
        val app = context.applicationContext
        val parts = EntryPointAccessors.fromApplication(app, Parts::class.java)
        // Held open till the press is answered: the application may be started for it alone.
        val pending = goAsync()
        parts.application().launch {
            try {
                if (attempt { parts.water().add(press.reminder.profileId, press.reminder.day, press.ml) }.isSuccess) {
                    attempt { parts.notifications().read(press.reminder.notificationId) }
                    PhoneNotifier.waterAdded(app, press)
                } else {
                    PhoneNotifier.waterNotAdded(app, press)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
