// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.domain.AccountNotification
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's unread notifications the phone has not shown yet, and those it showed that are read
 * since, anywhere. Each is shown once, known by its id and the time it was made. What the phone keeps
 * of them is the unread ones alone: one read anywhere is never shown again, and the list stays as
 * short as what is unread.
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

    /**
     * Reads what is unread, and gives back what the phone has not shown yet, keeping all of it as shown, and the keys of
     * what it showed that is unread no more. A list as long as the API gives may leave the oldest unread out: one shown
     * older than its last is neither said to be read nor forgotten.
     */
    suspend fun read(): Read = oneAtATime.withLock {
        val unread = notifications.unread()
        val shown = store.shown()
        val now = unread.map(::keyOf).toSet()
        val oldest = unread.minOfOrNull { it.createdAt }
        val unknown = if (unread.size < MOST_UNREAD_LISTED || oldest == null) {
            emptySet()
        } else {
            (shown - now).filterTo(mutableSetOf()) { key -> madeAt(key)?.isBefore(oldest) != false }
        }
        store.keepShown(now + unknown)
        Read(fresh = unread.filter { keyOf(it) !in shown }, gone = shown - now - unknown)
    }

    /** What a read found: the unread notifications not shown yet, and the keys of those shown that are read since. */
    data class Read(val fresh: List<AccountNotification>, val gone: Set<String>)

    companion object {
        /** As many unread notifications as the API lists at most (`MAX_UNREAD_LISTED`), the newest first. */
        const val MOST_UNREAD_LISTED = 50

        fun keyOf(notification: AccountNotification): String = "${notification.id}@${notification.createdAt}"

        /** When the notification of [key] was made, as [keyOf] wrote it; null for a key that does not say. */
        private fun madeAt(key: String): Instant? = runCatching { Instant.parse(key.substringAfter('@')) }.getOrNull()
    }
}
