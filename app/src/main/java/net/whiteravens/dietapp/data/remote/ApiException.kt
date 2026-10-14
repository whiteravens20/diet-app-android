// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

/**
 * Typed form of the shared `ApiError` envelope. UIs translate from the stable
 * [code] (e.g. `EMAIL_TAKEN`, `INVALID_CREDENTIALS`) — never from [message],
 * which is English server prose.
 */
class ApiException(
    val statusCode: Int,
    val code: String,
    override val message: String,
    val issues: List<ApiErrorIssueDto> = emptyList(),
    /** How many seconds to wait before asking again, where the refusal said (`Retry-After`). */
    val retryAfterSeconds: Int? = null,
    /** What the code has more to say, where it has any. */
    val details: JsonObject? = null,
) : Exception(message) {

    /** The fields a refused form named, each once, in the order the refusal names them. */
    val refusedFields: List<String>
        get() = if (code == VALIDATION_FAILED) issues.map { it.path }.filter { it.isNotEmpty() }.distinct() else emptyList()

    companion object {
        /** The code given to an answer that is not the API's own refusal (a proxy's page, an HTML error). */
        const val NOT_THE_API = "HTTP_"

        /** The code of a request whose fields were refused; [issues] name them. */
        const val VALIDATION_FAILED = "VALIDATION_FAILED"

        /** Parses the error body of a failed call; falls back to a synthetic code. */
        fun from(e: HttpException, json: Json): ApiException {
            val parsed = runCatching {
                e.response()?.errorBody()?.string()?.let { json.decodeFromString<ApiErrorDto>(it) }
            }.getOrNull()
            val wait = e.response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull()
            return if (parsed != null) {
                ApiException(parsed.statusCode, parsed.error, parsed.message, parsed.issues.orEmpty(), wait, parsed.details)
            } else {
                ApiException(e.code(), "$NOT_THE_API${e.code()}", e.message(), retryAfterSeconds = wait)
            }
        }
    }
}

/**
 * Runs an API call, rethrowing HTTP failures as [ApiException] so callers deal
 * in stable error codes. IO failures (offline) pass through untouched — the
 * offline-first repositories treat those as "serve the cache".
 */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw ApiException.from(e, json)
    }
