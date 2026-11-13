// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.notify

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import net.whiteravens.dietapp.MainActivity
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.notify.NewNotifications
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.NotificationAbout
import net.whiteravens.dietapp.domain.WATER_AMOUNTS_ML
import net.whiteravens.dietapp.ui.common.holdLetters
import net.whiteravens.dietapp.ui.common.notificationText
import java.time.LocalDate

/**
 * The account's notifications as the phone shows them: one channel, one notification for each, in
 * the words of the application's list, which a tap opens. A reminder of water carries the amounts
 * of the water card, which add water without opening the application ([WaterReminderReceiver]),
 * and a tap on it opens its profile's water on the dashboard; both read the reminder.
 */
object PhoneNotifier {
    /** The one channel, which the user can silence in the system's settings. */
    const val CHANNEL = "account-notifications"

    /** What a tap asks the application for: its list of notifications. */
    const val OPEN_NOTIFICATIONS = "net.whiteravens.dietapp.action.OPEN_NOTIFICATIONS"

    /** What a tap on a reminder of water asks the application for: the profile's water on the dashboard, the reminder read. */
    const val OPEN_WATER = "net.whiteravens.dietapp.action.OPEN_WATER"

    /** What a press of an amount on a reminder of water asks [WaterReminderReceiver] for. */
    const val ADD_WATER = "net.whiteravens.dietapp.action.ADD_WATER"

    /** The profile a reminder of water is for, and the reminder, as its intents carry them. */
    const val EXTRA_PROFILE = "profileId"
    const val EXTRA_NOTIFICATION = "notificationId"

    /** How long a reminder of water says what a press added before it goes, as the web's does. */
    private const val ADDED_SHOWN_MS = 4_000L

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

    /** The intent a tap on a reminder of water gives the application. */
    fun openingWater(context: Context, profileId: String, notificationId: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(OPEN_WATER)
        // A pending intent is told apart by its data, not by its extras: each reminder its own.
        .setData("dietapp://water/$notificationId".toUri())
        .putExtra(EXTRA_PROFILE, profileId)
        .putExtra(EXTRA_NOTIFICATION, notificationId)
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
            val key = NewNotifications.keyOf(notification).hashCode()
            val name = names[notification.profileId]
            val about = notification.about
            val shown = if (about is NotificationAbout.WaterReminder) {
                waterReminder(context, WaterReminderShown(notification.id, key, notification.profileId, name, about.day, notification.createdAt.toEpochMilli()), text)
            } else {
                shownAs(context, name, text, notification.createdAt.toEpochMilli(), tap).build()
            }
            manager.notify(key, shown)
        }
    }

    /** The water [press] added, said for a moment before the reminder goes. */
    fun waterAdded(context: Context, press: WaterPress) {
        val reminder = press.reminder
        val amount = "${press.ml}\u00A0${context.getString(R.string.unit_ml)}"
        val text = context.getString(R.string.notifications_water_added, amount, reminder.name ?: context.getString(R.string.app_name))
        val said = shownAs(context, reminder.name, text, reminder.at, openWater(context, reminder))
            .setTimeoutAfter(ADDED_SHOWN_MS)
            .build()
        post(context, reminder.key, said)
    }

    /** A press the server refused: said, with the amounts kept to press again. */
    fun waterNotAdded(context: Context, press: WaterPress) =
        post(context, press.reminder.key, waterReminder(context, press.reminder, context.getString(R.string.water_add_failed)))

    /** Shows [shown] in the place of the notification [key], where the phone still lets the application show notifications. */
    private fun post(context: Context, key: Int, shown: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(key, shown)
    }

    /** A reminder of water saying [text], with an amount of the water card on each of its buttons. */
    private fun waterReminder(context: Context, reminder: WaterReminderShown, text: String): Notification =
        shownAs(context, reminder.name, text, reminder.at, openWater(context, reminder)).apply {
            WATER_AMOUNTS_ML.forEach { ml ->
                val press = PendingIntent.getBroadcast(context, reminder.key, reminder.press(ml).intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                addAction(0, context.getString(R.string.water_add_ml, ml), press)
            }
        }.build()

    private fun openWater(context: Context, reminder: WaterReminderShown): PendingIntent = PendingIntent.getActivity(
        context,
        reminder.key,
        openingWater(context, reminder.profileId, reminder.notificationId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * A notification of the profile [name] (the application's where it is not known) saying [text], made [at], opening
     * [opens]. The shade lays its words out as a screen does: in Polish its words of one letter are held to the next.
     */
    private fun shownAs(context: Context, name: String?, text: String, at: Long, opens: PendingIntent): NotificationCompat.Builder {
        val locale = context.resources.configuration.locales[0]
        val said = holdLetters(text, locale)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name?.let { holdLetters(it, locale) } ?: context.getString(R.string.app_name))
            .setContentText(said)
            .setStyle(NotificationCompat.BigTextStyle().bigText(said))
            .setWhen(at)
            .setShowWhen(true)
            .setContentIntent(opens)
            .setAutoCancel(true)
            // What a press of an amount says in its place comes quietly.
            .setOnlyAlertOnce(true)
    }
}

/** A reminder of water as the phone shows it under [key]: the reminder [notificationId], the profile it is for and its [day], made [at]. */
data class WaterReminderShown(
    val notificationId: String,
    val key: Int,
    val profileId: String,
    val name: String?,
    val day: LocalDate,
    val at: Long,
) {
    fun press(ml: Int) = WaterPress(this, ml)
}

/** A press of [ml] on [reminder], as its intent carries it to [WaterReminderReceiver]. */
data class WaterPress(val reminder: WaterReminderShown, val ml: Int) {
    fun intent(context: Context): Intent = Intent(context, WaterReminderReceiver::class.java)
        .setAction(PhoneNotifier.ADD_WATER)
        // A pending intent is told apart by its data, not by its extras: each amount of each reminder its own.
        .setData("dietapp://water/${reminder.notificationId}/$ml".toUri())
        .putExtra(PhoneNotifier.EXTRA_NOTIFICATION, reminder.notificationId)
        .putExtra(PhoneNotifier.EXTRA_PROFILE, reminder.profileId)
        .putExtra(KEY, reminder.key)
        .putExtra(NAME, reminder.name)
        .putExtra(DAY, reminder.day.toString())
        .putExtra(AT, reminder.at)
        .putExtra(ML, ml)

    companion object {
        private const val KEY = "key"
        private const val NAME = "name"
        private const val DAY = "day"
        private const val AT = "at"
        private const val ML = "ml"

        /** The press an intent carries; null for one that carries none. */
        fun of(intent: Intent): WaterPress? {
            if (intent.action != PhoneNotifier.ADD_WATER) return null
            val reminder = WaterReminderShown(
                notificationId = intent.getStringExtra(PhoneNotifier.EXTRA_NOTIFICATION) ?: return null,
                key = intent.getIntExtra(KEY, 0),
                profileId = intent.getStringExtra(PhoneNotifier.EXTRA_PROFILE) ?: return null,
                name = intent.getStringExtra(NAME),
                day = intent.getStringExtra(DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
                at = intent.getLongExtra(AT, 0L),
            )
            return WaterPress(reminder, intent.getIntExtra(ML, 0).takeIf { it > 0 } ?: return null)
        }
    }
}
