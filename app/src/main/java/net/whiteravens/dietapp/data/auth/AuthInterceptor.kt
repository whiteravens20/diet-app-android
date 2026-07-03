package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <accessToken>` plus `Accept-Language`, the
 * header the platform's F14 locale resolution reads for non-web clients. Runs
 * only on the authenticated client — auth endpoints stay token-free.
 */
@Singleton
class AuthInterceptor @Inject constructor(private val tokens: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
        // OkHttp interceptors are synchronous by contract; the DataStore read is
        // a fast local disk hit and we are already off the main thread here.
        runBlocking { tokens.current() }?.let { session ->
            builder.header("Authorization", "Bearer ${session.accessToken}")
        }
        return chain.proceed(builder.build())
    }
}
