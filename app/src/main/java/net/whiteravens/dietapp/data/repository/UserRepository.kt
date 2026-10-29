// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.UpdateUserSettingsRequest
import net.whiteravens.dietapp.data.remote.WeightUnitRequest
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
    private val appearance: AppearanceStore,
) {
    private val aiModes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The account's AI mode each time an answer about the account says it, from the moment it is
     * followed: a screen that read the account already shows AI turned on or off in the account's
     * settings as soon as it is.
     */
    val aiModeChanges: Flow<String> = aiModes.asSharedFlow()

    suspend fun me(): SessionUser = heard(apiCall(json) { api.me() }.toDomain())

    /**
     * Chooses what AI the account has: `none`, `admin` (the instance's provider) or `byok` (its own
     * providers), which is refused while it has none.
     */
    suspend fun chooseAiMode(mode: String): SessionUser =
        heard(apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(aiMode = mode)) }.toDomain())

    /** Chooses the unit the account's weights are shown and typed in, `kg` or `lb`; null gives the choice back to where the phone is. */
    suspend fun chooseWeightUnit(unit: String?): SessionUser =
        heard(apiCall(json) { api.chooseWeightUnit(WeightUnitRequest(unit)) }.toDomain())

    /** Sends the mail that confirms the account's address again. */
    suspend fun resendVerification() = apiCall(json) { api.resendVerification() }

    suspend fun profiles(): List<Profile> =
        apiCall(json) { api.profiles() }.map { it.toDomain() }

    suspend fun calorieTarget(profileId: String): CalorieTarget =
        apiCall(json) { api.calories(profileId) }.let(CalorieCalculationDto::toDomain)

    /** Tells whoever follows the account's AI mode, and the application's colours, what [user] says they are. */
    private suspend fun heard(user: SessionUser): SessionUser {
        aiModes.tryEmit(user.aiMode)
        appearance.heard(user.palette)
        return user
    }
}
