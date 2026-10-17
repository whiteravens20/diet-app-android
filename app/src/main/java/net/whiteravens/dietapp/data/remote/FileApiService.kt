// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming

/**
 * The routes whose answer is a file the user keeps. A file is as long as the account makes it and
 * comes as fast as the line allows, so these calls have no limit on the whole call; one that stops
 * coming is given up by the read timeout. Held to the API's contract as [DietApiService] is.
 */
interface FileApiService {
    /**
     * Everything the account holds, as one JSON file the API writes piece by piece, read as it
     * arrives; asked for a few times an hour at most.
     */
    @Streaming
    @GET("users/me/export")
    suspend fun exportAccount(): ResponseBody
}
