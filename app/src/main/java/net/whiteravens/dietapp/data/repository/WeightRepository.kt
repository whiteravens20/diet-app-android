// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.LogWeightRequest
import net.whiteravens.dietapp.data.remote.WeightDayDto
import net.whiteravens.dietapp.data.remote.WeightEntryDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.domain.WeightPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The weights a profile logged: those of a span read, also by day, one logged, one removed. The API
 * keeps kilograms. A weight logged or removed goes through the queue of changes, and waits there for
 * a network where there is none: one logged is sent with the moment it was taken, and with a key
 * that has the server log it once however often it is sent.
 */
@Singleton
class WeightRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The weights of [profileId] taken since [since], the newest first. */
    suspend fun entries(profileId: String, since: Instant): List<WeightEntry> =
        apiCall(json) { api.weights(profileId, since.toString()) }.map { it.toDomain() }

    /** The weights of [profileId] by day where the phone is ([zone]), from the day [since] falls on, the oldest first. */
    suspend fun days(profileId: String, since: Instant, zone: ZoneId): List<WeightPoint> =
        apiCall(json) { api.weightDays(profileId, since.toString(), zone.id) }.map { it.toDomain() }

    /** Logs [kg] for [profileId], taken now: the weight as the API keeps it, or kept to be sent in its turn. */
    suspend fun log(profileId: String, kg: Double): Submitted<WeightEntry> {
        val queued = QueuedChange.WeightLogged(profileId, kg, Instant.now().toString(), UUID.randomUUID().toString())
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a weight logged; answers it as the API keeps it. */
    suspend fun send(change: QueuedChange.WeightLogged): WeightEntry =
        apiCall(json) { api.logWeight(change.key, LogWeightRequest(change.profileId, change.kg, change.recordedAt)) }.toDomain()

    /** Removes the weight [id] of [profileId], or keeps its removal to be sent in its turn. */
    suspend fun delete(profileId: String, id: String): Submitted<Unit> {
        val queued = QueuedChange.WeightDeleted(profileId, id)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a weight's removal. */
    suspend fun send(change: QueuedChange.WeightDeleted) = apiCall(json) { api.deleteWeight(change.id) }
}

private fun WeightEntryDto.toDomain() = WeightEntry(id = id, kg = kg, recordedAt = Instant.parse(recordedAt))

private fun WeightDayDto.toDomain() = WeightPoint(date = LocalDate.parse(date), kg = kg, mean = mean)
