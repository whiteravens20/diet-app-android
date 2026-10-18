// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.ChangeEmailRequest
import net.whiteravens.dietapp.data.remote.ChangePasswordRequest
import net.whiteravens.dietapp.data.remote.DeleteAccountRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.FileApiService
import net.whiteravens.dietapp.data.remote.UpdateUserSettingsRequest
import net.whiteravens.dietapp.data.remote.WeightUnitRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.SessionUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the API ends the account's file once all of it is written. */
private val WHOLE = "\"complete\":true}".toByteArray()

/** The account's file stopped before its end: what arrived is not the account's data. */
class IncompleteExportException : IOException("The account's file did not arrive whole.")

/** Session user, settings and profiles — thin, network-only (no offline cache). */
@Singleton
class UserRepository @Inject constructor(
    private val api: DietApiService,
    private val files: FileApiService,
    private val json: Json,
) {
    private val aiModes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The account's AI mode each time an answer about the account says it, from the moment it is
     * followed: a screen that read the account already shows AI turned on or off in the account's
     * settings as soon as it is.
     */
    val aiModeChanges: Flow<String> = aiModes.asSharedFlow()

    suspend fun me(): SessionUser = heard(apiCall(json) { api.me() }.toDomain())

    /** Gives the account the name it shows. */
    suspend fun rename(displayName: String): SessionUser =
        heard(apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(displayName = displayName)) }.toDomain())

    /** Chooses the language of the account's mails and of the web application, `en` or `pl`; this app follows the phone's. */
    suspend fun chooseLanguage(locale: String): SessionUser =
        heard(apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(locale = locale)) }.toDomain())

    /**
     * Chooses what AI the account has: `none`, `admin` (the instance's provider) or `byok` (its own
     * providers), which is refused while it has none.
     */
    suspend fun chooseAiMode(mode: String): SessionUser =
        heard(apiCall(json) { api.updateSettings(UpdateUserSettingsRequest(aiMode = mode)) }.toDomain())

    /** Chooses the unit the account's weights are shown and typed in, `kg` or `lb`; null gives the choice back to where the phone is. */
    suspend fun chooseWeightUnit(unit: String?): SessionUser =
        heard(apiCall(json) { api.chooseWeightUnit(WeightUnitRequest(unit)) }.toDomain())

    /** Changes the password from [current] to [new]; the API then ends every session of the account, the phone's too. */
    suspend fun changePassword(current: String, new: String) =
        apiCall(json) { api.changePassword(ChangePasswordRequest(current, new)) }

    /** Asks for [newEmail] as the account's address, with [currentPassword]: the change is made once the link mailed there is followed. */
    suspend fun changeEmail(newEmail: String, currentPassword: String) =
        apiCall(json) { api.changeEmail(ChangeEmailRequest(newEmail, currentPassword)) }

    /**
     * Writes everything the account holds into [into], one JSON file as the API writes it, piece by
     * piece. A file that does not end the way the API ends a whole one is refused
     * ([IncompleteExportException]), so that one cut short is not taken for the account's data.
     */
    suspend fun export(into: OutputStream) {
        val file = apiCall(json) { files.exportAccount() }
        withContext(Dispatchers.IO) {
            file.use { answer ->
                val input = answer.byteStream()
                val buffer = ByteArray(8 * 1024)
                var end = ByteArray(0)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    into.write(buffer, 0, read)
                    end = (end + buffer.copyOf(read)).takeLast(WHOLE.size).toByteArray()
                }
                if (!end.contentEquals(WHOLE)) throw IncompleteExportException()
            }
        }
    }

    /** Deletes the account and everything it holds, for [currentPassword]; its sessions end with it, the phone's too. */
    suspend fun deleteAccount(currentPassword: String) =
        apiCall(json) { api.deleteAccount(DeleteAccountRequest(currentPassword)) }

    /** Sends the mail that confirms the account's address again. */
    suspend fun resendVerification() = apiCall(json) { api.resendVerification() }

    suspend fun profiles(): List<Profile> =
        apiCall(json) { api.profiles() }.map { it.toDomain() }

    suspend fun calorieTarget(profileId: String): CalorieTarget =
        apiCall(json) { api.calories(profileId) }.let(CalorieCalculationDto::toDomain)

    /** Tells whoever follows the account's AI mode what [user] says it is. */
    private fun heard(user: SessionUser): SessionUser = user.also { aiModes.tryEmit(it.aiMode) }
}
