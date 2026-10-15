// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Tag

/**
 * Endpoints that take no session: signing in and out, and what the instance tells
 * anybody. Bound to the plain OkHttp client — no bearer interceptor, no
 * authenticator. The refresh is [RefreshApiService]'s; everything session-scoped
 * lives on [DietApiService].
 */
interface AuthApiService {

    /** What the instance tells anybody before signing in, its version among it. */
    @GET("config")
    suspend fun config(): PublicConfigDto

    /** Whether the API at [server], an address typed for it, is up and reaches its database. */
    @GET("health")
    suspend fun health(@Tag server: ServerAddress): HealthDto

    /** A session, or for an account with two-step sign-in a challenge for [loginSecondFactor]. */
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponseDto

    /** The second step of signing in: the challenge [login] answered, with a code. */
    @POST("auth/login/second-factor")
    suspend fun loginSecondFactor(@Body body: SecondFactorSignInRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    /** Answered 202 whether the address has an account or not. */
    @POST("auth/password-reset/request")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequestDto)

    /**
     * Revokes the refresh token on [server], the one that issued it, which may no longer be the
     * one in use. 204; the access token simply expires.
     */
    @POST("auth/logout")
    suspend fun logout(@Tag server: ServerAddress, @Body body: RefreshRequest)
}
