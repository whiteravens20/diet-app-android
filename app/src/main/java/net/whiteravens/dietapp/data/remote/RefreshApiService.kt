// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The refresh of the token pair. The authenticator calls it synchronously, on a client of its
 * own: requests waiting for a new token hold the slots of their own client, and a refresh that
 * needed one of those slots would wait for ever.
 */
interface RefreshApiService {
    /** Rotates the token pair; the old refresh token is invalidated server-side. */
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<AuthResponse>
}
