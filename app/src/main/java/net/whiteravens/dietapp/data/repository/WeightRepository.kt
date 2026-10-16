// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.LogWeightRequest
import net.whiteravens.dietapp.data.remote.WeightEntryDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.WeightEntry
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The weights a profile logged: those of a span read, one logged, one removed. The API keeps kilograms. */
@Singleton
class WeightRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    /** The weights of [profileId] taken since [since], the newest first. */
    suspend fun entries(profileId: String, since: Instant): List<WeightEntry> =
        apiCall(json) { api.weights(profileId, since.toString()) }.map { it.toDomain() }

    /** Logs [kg] for [profileId], taken now; answers the weight as the API keeps it. */
    suspend fun log(profileId: String, kg: Double): WeightEntry =
        apiCall(json) { api.logWeight(LogWeightRequest(profileId, kg)) }.toDomain()

    suspend fun delete(id: String) = apiCall(json) { api.deleteWeight(id) }
}

private fun WeightEntryDto.toDomain() = WeightEntry(id = id, kg = kg, recordedAt = Instant.parse(recordedAt))
