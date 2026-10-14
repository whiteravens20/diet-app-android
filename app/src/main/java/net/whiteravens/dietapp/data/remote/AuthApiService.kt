// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    /** Revokes the refresh token. 204; the access token simply expires. */
    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)
}
