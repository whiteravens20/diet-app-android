package net.whiteravens.dietapp.data.remote

import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.config.ServerConfigStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-targets every request at the server saved in [ServerConfigStore].
 * Retrofit is built against the compiled-in default base, so this swaps that
 * prefix for the stored one and keeps the endpoint path + query intact. Runs
 * on both OkHttp clients (it sits on the shared base client).
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val config: ServerConfigStore,
) : Interceptor {

    private val compiledBase = BuildConfig.API_BASE_URL.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val stored = runBlocking { config.current() }
        if (stored == BuildConfig.API_BASE_URL) return chain.proceed(request)
        // A stored value that no longer parses falls back to the compiled base.
        val base = stored.toHttpUrlOrNull() ?: return chain.proceed(request)

        val endpointSegments = request.url.pathSegments
            .drop(compiledBase.pathSegments.count { it.isNotEmpty() })
        val rewritten = base.newBuilder()
            .apply { endpointSegments.forEach { addPathSegment(it) } }
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
