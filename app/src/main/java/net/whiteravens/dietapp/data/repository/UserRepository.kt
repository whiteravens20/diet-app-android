// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.ChangeEmailRequest
import net.whiteravens.dietapp.data.remote.ChangePasswordRequest
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
) {
    suspend fun me(): SessionUser = apiCall(json) { api.me() }.toDomain()

    /** Gives the account the name it shows. */
    suspend fun rename(displayName: String): SessionUser =
        apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(displayName = displayName)) }.toDomain()

    /** Chooses the language of the account's mails and of the web application, `en` or `pl`; this app follows the phone's. */
    suspend fun chooseLanguage(locale: String): SessionUser =
        apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(locale = locale)) }.toDomain()

    /** Chooses the unit the account's weights are shown and typed in, `kg` or `lb`; null gives the choice back to where the phone is. */
    suspend fun chooseWeightUnit(unit: String?): SessionUser =
        apiCall(json) { api.chooseWeightUnit(WeightUnitRequest(unit)) }.toDomain()

    /** Changes the password from [current] to [new]; the API then ends every session of the account, the phone's too. */
    suspend fun changePassword(current: String, new: String) =
        apiCall(json) { api.changePassword(ChangePasswordRequest(current, new)) }

    /** Asks for [newEmail] as the account's address, with [currentPassword]: the change is made once the link mailed there is followed. */
    suspend fun changeEmail(newEmail: String, currentPassword: String) =
        apiCall(json) { api.changeEmail(ChangeEmailRequest(newEmail, currentPassword)) }

    /** Sends the mail that confirms the account's address again. */
    suspend fun resendVerification() = apiCall(json) { api.resendVerification() }

    suspend fun profiles(): List<Profile> =
        apiCall(json) { api.profiles() }.map { it.toDomain() }

    suspend fun calorieTarget(profileId: String): CalorieTarget =
        apiCall(json) { api.calories(profileId) }.let(CalorieCalculationDto::toDomain)
}
