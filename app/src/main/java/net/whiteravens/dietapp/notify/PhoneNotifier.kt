// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.whiteravens.dietapp.MainActivity
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.notify.NewNotifications
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.ui.common.notificationText

/**
 * The account's notifications as the phone shows them: one channel, one notification for each, in
 * the words of the application's list, which a tap opens. Nothing is marked read on the phone.
 */
object PhoneNotifier {
    /** The one channel, which the user can silence in the system's settings. */
    const val CHANNEL = "account-notifications"

    /** What a tap asks the application for: its list of notifications. */
    const val OPEN_NOTIFICATIONS = "net.whiteravens.dietapp.action.OPEN_NOTIFICATIONS"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.phone_notifications_channel))
            .setDescription(context.getString(R.string.phone_notifications_channel_about))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Whether the phone lets the application show notifications: its permission, and its notifications not blocked. */
    fun allowed(context: Context): Boolean = permitted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The permission Android 13 and later ask the user for; earlier versions grant it with the installation. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The intent a tap gives the application. */
    fun opening(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(OPEN_NOTIFICATIONS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Shows [fresh], each as the profile it is for ([names], by id) and what it says. */
    fun show(context: Context, fresh: List<AccountNotification>, names: Map<String, String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val tap = PendingIntent.getActivity(context, 0, opening(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val manager = NotificationManagerCompat.from(context)
        fresh.forEach { notification ->
            val text = notificationText(context.resources, notification.about)
            val shown = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(names[notification.profileId] ?: context.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setWhen(notification.createdAt.toEpochMilli())
                .setShowWhen(true)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            manager.notify(NewNotifications.keyOf(notification).hashCode(), shown)
        }
    }
}
