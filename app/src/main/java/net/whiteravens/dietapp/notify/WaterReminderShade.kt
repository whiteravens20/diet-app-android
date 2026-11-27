// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the phone shows of the reminders of water. Once water is added to a profile's day, on the card, from the
 * list of notifications, by a shortcut of the icon or by the reminder itself, the reminder of that day goes, as the
 * server then reads it: who just drank is not told to drink. A reminder of another day stays.
 */
@Singleton
class WaterReminderShade @Inject constructor(@ApplicationContext private val context: Context) {
    /** Takes down what the phone shows of [profileId]'s reminder of water for [day]. */
    fun takeDown(profileId: String, day: LocalDate) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { PhoneNotifier.remindsOfWater(it.notification, profileId, day) }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
