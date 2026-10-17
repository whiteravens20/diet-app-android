// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.SecondFactorConfirmRequest
import net.whiteravens.dietapp.data.remote.SecondFactorProofRequest
import net.whiteravens.dietapp.data.remote.SecondFactorSetupRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.SecondFactorSetup
import net.whiteravens.dietapp.domain.SecondFactorState
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's two-step sign-in: whether it is on, a secret to set an authenticator app up with,
 * the first code that switches it on, new recovery codes, and switching it off. A code is sent
 * without the spaces typed in it, as the sign-in's second step sends one.
 */
@Singleton
class SecondFactorRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    suspend fun state(): SecondFactorState = apiCall(json) { api.secondFactor() }.let {
        SecondFactorState(enabled = it.enabled, since = it.since?.let(Instant::parse), recoveryCodesLeft = it.recoveryCodesLeft)
    }

    /** A secret for an authenticator app, handed out for the account's password; it counts once [begin] has a first code of it. */
    suspend fun propose(currentPassword: String): SecondFactorSetup =
        apiCall(json) { api.proposeSecondFactor(SecondFactorSetupRequest(currentPassword)) }.let {
            SecondFactorSetup(secret = it.secret, uri = it.uri, enrolment = it.enrolment)
        }

    /** Switches two-step sign-in on with the app's first [code]; answers the recovery codes, given this once. */
    suspend fun begin(enrolment: String, code: String): List<String> =
        apiCall(json) { api.beginSecondFactor(SecondFactorConfirmRequest(enrolment, code.withoutSpaces())) }.recoveryCodes

    /** New recovery codes in the place of the ones the account had, for the password and a [code]; given this once. */
    suspend fun newRecoveryCodes(currentPassword: String, code: String): List<String> =
        apiCall(json) { api.newRecoveryCodes(SecondFactorProofRequest(currentPassword, code.withoutSpaces())) }.recoveryCodes

    /** Switches two-step sign-in off, for the password and a [code]. */
    suspend fun end(currentPassword: String, code: String) =
        apiCall(json) { api.endSecondFactor(SecondFactorProofRequest(currentPassword, code.withoutSpaces())) }

    private fun String.withoutSpaces() = filterNot(Char::isWhitespace)
}
