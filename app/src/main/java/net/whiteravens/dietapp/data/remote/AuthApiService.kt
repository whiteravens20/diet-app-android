package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Unauthenticated auth endpoints. Bound to the plain OkHttp client — no bearer
 * interceptor, no authenticator — so a token refresh can never recurse into
 * itself. Everything session-scoped lives on [DietApiService] instead.
 */
interface AuthApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    /** Rotates the token pair; the old refresh token is invalidated server-side. */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    /** Revokes the refresh token. 204; the access token simply expires. */
    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)
}
