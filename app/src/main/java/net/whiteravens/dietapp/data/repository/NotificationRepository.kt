// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.NotificationDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.NotificationAbout
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notifications of the account's profiles: those not read yet, one read, all read. One read goes
 * through the queue of changes, and waits there for a network where there is none; all are read
 * with a network alone, as the server would read those that came after them too.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The notifications not read yet, as the API orders them. */
    suspend fun unread(): List<AccountNotification> = apiCall(json) { api.unreadNotifications() }.map { it.toDomain() }

    /** Marks [id] read, or keeps that to be sent in its turn; marking it again changes nothing. */
    suspend fun read(id: String): Submitted<Unit> {
        val queued = QueuedChange.NotificationRead(id)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a notification's reading. */
    suspend fun send(change: QueuedChange.NotificationRead) = apiCall(json) { api.readNotification(change.id) }

    /** Marks every notification of the account read; answers how many there were. */
    suspend fun readAll(): Int = apiCall(json) { api.readAllNotifications() }.updated
}

internal fun NotificationDto.toDomain() = AccountNotification(
    id = id,
    profileId = profileId,
    about = when (type) {
        // The API writes 0 days for a profile that has logged no weight yet.
        "weight_reminder" -> NotificationAbout.WeightReminder(
            ((payload as? JsonObject)?.get("daysSinceLastEntry") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
        )
        "preferences_review" -> NotificationAbout.PreferencesReview
        else -> NotificationAbout.Other(type)
    },
    createdAt = Instant.parse(createdAt),
)
