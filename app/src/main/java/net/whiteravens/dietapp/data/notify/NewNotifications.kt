// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.domain.AccountNotification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's unread notifications the phone has not shown yet. Each is shown once, known by its
 * id and the time it was made. What the phone keeps of them is the unread ones alone: one read
 * anywhere is never shown again, and the list stays as short as what is unread.
 */
@Singleton
class NewNotifications @Inject constructor(
    private val notifications: NotificationRepository,
    private val store: PhoneNotificationsStore,
) {
    /**
     * One read at a time. The periodic work and the read on coming to the front can run at the same
     * moment: two reads at once would both give what neither had kept yet, and an older answer coming
     * last would take back what a newer one kept.
     */
    private val oneAtATime = Mutex()

    /** Reads what is unread, and gives back what the phone has not shown yet, keeping all of it as shown. */
    suspend fun read(): List<AccountNotification> = oneAtATime.withLock {
        val unread = notifications.unread()
        val shown = store.shown()
        store.keepShown(unread.map(::keyOf).toSet())
        unread.filter { keyOf(it) !in shown }
    }

    companion object {
        fun keyOf(notification: AccountNotification): String = "${notification.id}@${notification.createdAt}"
    }
}
