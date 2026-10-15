// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server a request goes to where that is not the one in use, given to Retrofit as the
 * request's tag (`@Tag`): a session's own server, which revokes it after the server was changed.
 */
data class ServerAddress(val baseUrl: String)

/** The base Retrofit builds every request on, which [BaseUrlInterceptor] replaces: it names no place. */
const val PLACEHOLDER_BASE = "https://server.invalid/api/"

/**
 * Re-targets every request at the server saved in [ServerConfigStore], or at the one its
 * [ServerAddress] tag names. Retrofit builds requests on [PLACEHOLDER_BASE], so this swaps that
 * prefix for the server's and keeps the endpoint path + query intact; with no server chosen yet,
 * a request goes nowhere and fails as a connection would. Runs on both OkHttp clients (it sits on
 * the shared base client).
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val config: ServerConfigStore,
) : Interceptor {

    private val placeholder = PLACEHOLDER_BASE.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val stored = request.tag(ServerAddress::class.java)?.baseUrl ?: runBlocking { config.current() }
        val base = stored.toHttpUrlOrNull() ?: throw IOException("No server is chosen")

        val endpointSegments = request.url.pathSegments
            .drop(placeholder.pathSegments.count { it.isNotEmpty() })
        val rewritten = base.newBuilder()
            .apply { endpointSegments.forEach { addPathSegment(it) } }
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
