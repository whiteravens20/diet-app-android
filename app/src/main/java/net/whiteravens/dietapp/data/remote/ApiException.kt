package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
) : Exception(message) {

    companion object {
        /** Parses the error body of a failed call; falls back to a synthetic code. */
        fun from(e: HttpException, json: Json): ApiException {
            val parsed = runCatching {
                e.response()?.errorBody()?.string()?.let { json.decodeFromString<ApiErrorDto>(it) }
            }.getOrNull()
            return if (parsed != null) {
                ApiException(parsed.statusCode, parsed.error, parsed.message, parsed.issues.orEmpty())
            } else {
                ApiException(e.code(), "HTTP_${e.code()}", e.message())
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
