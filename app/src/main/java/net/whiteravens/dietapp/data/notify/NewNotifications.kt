// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.domain.AccountNotification
import javax.inject.Inject

/**
 * The account's unread notifications the phone has not shown yet. Each is shown once, known by its
 * id and the time it was made. What the phone keeps of them is the unread ones alone: one read
 * anywhere is never shown again, and the list stays as short as what is unread.
 */
class NewNotifications @Inject constructor(
    private val notifications: NotificationRepository,
    private val store: PhoneNotificationsStore,
) {
    /** Reads what is unread, and gives back what the phone has not shown yet, keeping all of it as shown. */
    suspend fun read(): List<AccountNotification> {
        val unread = notifications.unread()
        val shown = store.shown()
        store.keepShown(unread.map(::keyOf).toSet())
        return unread.filter { keyOf(it) !in shown }
    }

    companion object {
        fun keyOf(notification: AccountNotification): String = "${notification.id}@${notification.createdAt}"
    }
}
