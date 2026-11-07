// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AddWaterRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.WaterDayDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.WaterDay
import net.whiteravens.dietapp.domain.WaterEntry
import net.whiteravens.dietapp.domain.WaterPoint
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The water a profile drinks, by day of its calendar: a day read, an amount added, an entry
 * removed. An addition or a removal goes through the queue of changes, and waits there for a
 * network where there is none: an addition with a key that has the server write it once however
 * often it is sent.
 */
@Singleton
class WaterRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The water of [profileId]'s [day], with the seven days that end with it. */
    suspend fun day(profileId: String, day: LocalDate): WaterDay =
        apiCall(json) { api.water(profileId, day.toString()) }.toDomain()

    /** Adds [ml] to [profileId]'s [day]: the day as the API then has it, or kept to be sent in its turn. */
    suspend fun add(profileId: String, day: LocalDate, ml: Int): Submitted<WaterDay> {
        val queued = QueuedChange.WaterAdded(profileId, day.toString(), ml, UUID.randomUUID().toString())
        return queue.submit(queued) { send(queued) }
    }

    /** Sends water added; answers its day as the API then has it. */
    suspend fun send(change: QueuedChange.WaterAdded): WaterDay =
        apiCall(json) { api.addWater(change.key, AddWaterRequest(change.profileId, change.day, change.ml)) }.toDomain()

    /** Removes [entry], of [profileId]'s [day], or keeps its removal to be sent in its turn. */
    suspend fun delete(profileId: String, day: LocalDate, entry: WaterEntry): Submitted<WaterDay> {
        val queued = QueuedChange.WaterDeleted(profileId, entry.id, day.toString(), entry.ml)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends an entry's removal; answers its day as the API then has it. */
    suspend fun send(change: QueuedChange.WaterDeleted): WaterDay =
        apiCall(json) { api.deleteWater(change.id) }.toDomain()
}

private fun WaterDayDto.toDomain() = WaterDay(
    day = LocalDate.parse(day),
    ml = ml,
    target = target,
    entries = entries,
    last = last?.let { WaterEntry(it.id, it.ml, Instant.parse(it.at)) },
    days = days.map { WaterPoint(LocalDate.parse(it.day), it.ml) },
)
