package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.UpdateUserSettingsRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.SessionUser
import javax.inject.Inject
import javax.inject.Singleton

/** Session user, settings and profiles — thin, network-only (no offline cache). */
@Singleton
class UserRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    suspend fun me(): SessionUser = apiCall(json) { api.me() }.toDomain()

    suspend fun updateSettings(
        displayName: String? = null,
        locale: String? = null,
        theme: String? = null,
    ): SessionUser = apiCall(json) {
        api.updateSettings(UpdateUserSettingsRequest(displayName = displayName, locale = locale, theme = theme))
    }.toDomain()

    suspend fun profiles(): List<Profile> =
        apiCall(json) { api.profiles() }.map { it.toDomain() }

    suspend fun calorieTarget(profileId: String): CalorieTarget =
        apiCall(json) { api.calories(profileId) }.let(CalorieCalculationDto::toDomain)
}
