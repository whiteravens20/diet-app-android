// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.auth.PendingRevocation
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.PasswordResetRequestDto
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.data.remote.RegisterRequest
import net.whiteravens.dietapp.data.remote.SecondFactorSignInRequest
import net.whiteravens.dietapp.data.remote.ServerAddress
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.SignInResult
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session lifecycle. Sign-in persists the token pair; from then on the
 * authenticated client attaches and refreshes tokens transparently. Sign-out
 * ends the session on the device at once ([SessionEnd]: the offline cache, then
 * the tokens) and revokes it on the server; a revocation that cannot reach the
 * server is kept and tried again at the next start ([retryRevocation]), on the
 * server that issued the session, whichever the app talks to by then.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val tokens: TokenStore,
    private val sessionEnd: SessionEnd,
    private val servers: ServerConfigStore,
    private val json: Json,
) {
    /** Reactive sign-in state — drives the auth vs. main navigation graph. */
    val isSignedIn: Flow<Boolean> = tokens.session.map { it != null }

    /**
     * Signs in with a password, and with [checkToken], the anti-abuse check's answer, where the
     * server asks for the check. An account with two-step sign-in is answered with a second step.
     */
    suspend fun signIn(email: String, password: String, checkToken: String? = null): SignInResult {
        val answer = apiCall(json) { authApi.login(LoginRequest(email, password, checkToken)) }
        answer.secondFactor?.let { return SignInResult.SecondStep(it.challenge) }
        tokens.save(checkNotNull(answer.tokens) { "A sign-in answered neither a session nor a second step" }, answer.sessionId)
        return SignInResult.SignedIn
    }

    /** Answers a sign-in's second step with a code from the authenticator app or a recovery code. */
    suspend fun answerSecondStep(challenge: String, code: String) {
        val response = apiCall(json) {
            authApi.loginSecondFactor(SecondFactorSignInRequest(challenge, code.filterNot(Char::isWhitespace)))
        }
        tokens.save(response.tokens, response.sessionId)
    }

    suspend fun register(email: String, password: String, displayName: String, checkToken: String? = null) {
        val response = apiCall(json) { authApi.register(RegisterRequest(email, password, displayName, checkToken)) }
        tokens.save(response.tokens, response.sessionId)
    }

    /**
     * Asks for a mail with a link to set a new password for [email], with the anti-abuse check's
     * answer where the server asks for the check. The answer is the same whether the address has an
     * account or not.
     */
    suspend fun requestPasswordReset(email: String, checkToken: String? = null) {
        apiCall(json) { authApi.requestPasswordReset(PasswordResetRequestDto(email, checkToken)) }
    }

    suspend fun signOut() {
        // Kept before anything else, so that a revocation cut short is still tried again.
        tokens.current()?.let { session -> tokens.setPendingRevocation(PendingRevocation(session.refreshToken, servers.current())) }
        sessionEnd.end()
        retryRevocation()
    }

    /**
     * Leaves a session the server has ended already, as a change of the password ends it: what the
     * phone keeps of it goes, and there is nothing left to revoke.
     */
    suspend fun leave() = sessionEnd.end()

    /** Revokes on the server a session signed out of on this device, if one is still waiting for it. */
    suspend fun retryRevocation() {
        val pending = tokens.pendingRevocation() ?: return
        try {
            authApi.logout(ServerAddress(pending.server), RefreshRequest(pending.refreshToken))
            tokens.setPendingRevocation(null)
        } catch (e: HttpException) {
            // The server refused it: the token is no good any more, which is what was wanted.
            tokens.setPendingRevocation(null)
        } catch (e: IOException) {
            // Still no server: tried again at the next start.
        }
    }
}
