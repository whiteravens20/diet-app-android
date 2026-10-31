// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.local.CachedProfile
import net.whiteravens.dietapp.data.local.KeptReadsDao
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.UpdateUserSettingsRequest
import net.whiteravens.dietapp.data.remote.WeightUnitRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.KeptProfiles
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.SessionUser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Session user, settings and profiles: network-only, but for what is kept of the profiles for a start without a network. */
@Singleton
class UserRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val appearance: AppearanceStore,
    private val kept: KeptReadsDao,
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

    /** The account's profiles, as the API keeps them; what a screen needs of them is kept for a start without a network. */
    suspend fun profiles(): List<Profile> {
        val read = apiCall(json) { api.profiles() }.map { it.toDomain() }
        val now = System.currentTimeMillis()
        kept.replaceProfiles(
            read.mapIndexed { position, profile ->
                CachedProfile(profile.id, position, profile.name, profile.dietType, profile.mealCount, profile.weightKg, profile.targetWeightKg, now)
            },
        )
        return read
    }

    /** The account's profiles as they were last read, and when; null before they were ever read on this phone. */
    suspend fun keptProfiles(): KeptProfiles? {
        val rows = kept.profiles().takeIf { it.isNotEmpty() } ?: return null
        return KeptProfiles(rows.map { Profile(it.id, it.name, it.dietType, it.mealCount, it.weightKg, it.targetWeightKg) }, rows.first().syncedAt)
    }

    /** The account's profiles, as they were last read where the server cannot be reached and they were read before. */
    suspend fun profilesOrKept(): List<Profile> = try {
        profiles()
    } catch (e: IOException) {
        keptProfiles()?.profiles ?: throw e
    }

    suspend fun calorieTarget(profileId: String): CalorieTarget =
        apiCall(json) { api.calories(profileId) }.let(CalorieCalculationDto::toDomain)

    /** Tells whoever follows the account's AI mode, and the application's colours, what [user] says they are. */
    private suspend fun heard(user: SessionUser): SessionUser {
        aiModes.tryEmit(user.aiMode)
        appearance.heard(user.palette)
        return user
    }
}
