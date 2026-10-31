// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.auth.AuthInterceptor
import net.whiteravens.dietapp.data.auth.KeystoreSealer
import net.whiteravens.dietapp.data.auth.Sealer
import net.whiteravens.dietapp.data.auth.TokenAuthenticator
import net.whiteravens.dietapp.data.config.LocalNetworkAccess
import net.whiteravens.dietapp.data.config.PhoneLocalNetworkAccess
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.remote.ClientHeaders
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.data.remote.PLACEHOLDER_BASE
import net.whiteravens.dietapp.data.remote.RefreshApiService
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the token-free OkHttp/Retrofit pair. [AuthApiService] must ride this
 * one — a refresh call through the authenticated client would recurse.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Unauthenticated

/**
 * Marks the scope of the work a screen starts that has to be finished after the screen is gone:
 * a change of server, whose sign-out takes the screen away.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Hilt graph: networking, JSON, auth plumbing and the offline cache database. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // `ignoreUnknownKeys` tolerates fields the platform adds before we mirror
    // them; `explicitNulls = false` reads a field an answer leaves out as null.
    // What the app sends is written by [requestJson].
    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @Unauthenticated
    fun baseOkHttp(baseUrlInterceptor: BaseUrlInterceptor, clientHeaders: ClientHeaders): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(clientHeaders)
            // A call that takes longer is given up, so that nothing waits on a server for ever.
            .callTimeout(30, TimeUnit.SECONDS)
            // An answer that stops coming for this long is given up.
            .readTimeout(10, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC, not BODY — response bodies and auth headers carry tokens.
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

    @Provides
    @Singleton
    fun authedOkHttp(
        @Unauthenticated base: OkHttpClient,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        base.newBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

    @Provides
    @Singleton
    fun authApi(@Unauthenticated client: OkHttpClient, json: Json): AuthApiService =
        retrofit(client, json).create(AuthApiService::class.java)

    /** The refresh, on a client of its own, so that it never waits for the requests that wait for it. */
    @Provides
    @Singleton
    fun refreshApi(@Unauthenticated client: OkHttpClient, json: Json): RefreshApiService =
        retrofit(client.newBuilder().dispatcher(Dispatcher()).build(), json).create(RefreshApiService::class.java)

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): DietApiService =
        retrofit(client, json).create(DietApiService::class.java)

    /** The routes a model may take part in, on the authenticated client given the time a model may take ([modelClient]). */
    @Provides
    @Singleton
    fun modelApi(client: OkHttpClient, json: Json): ModelApiService =
        retrofit(modelClient(client), json).create(ModelApiService::class.java)

    /**
     * [client] given the time a model may take: the API may wait a minute for one before it answers,
     * and says nothing until then, so the call is given two minutes, and its answer as long to begin.
     */
    fun modelClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder().callTimeout(2, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()

    /**
     * What the app sends: a field with a default is left out while it holds it, so an optional field
     * the app leaves unset is not sent, and a field without one is always sent, null as null, as the
     * API asks of a field it requires that may be empty (a profile's `PUT`).
     */
    fun requestJson(json: Json): Json = Json(json) { explicitNulls = true }

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(ReadAndWrite(read = json, write = requestJson(json)))
            .build()

    /** Reads answers with one [Json] and writes requests with another. */
    private class ReadAndWrite(read: Json, write: Json) : Converter.Factory() {
        private val read = read.asConverterFactory(JSON)
        private val write = write.asConverterFactory(JSON)

        override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit) =
            read.responseBodyConverter(type, annotations, retrofit)

        override fun requestBodyConverter(
            type: Type,
            parameterAnnotations: Array<out Annotation>,
            methodAnnotations: Array<out Annotation>,
            retrofit: Retrofit,
        ) = write.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
    }

    private val JSON = "application/json".toMediaType()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether the app may reach the phone's local network, as Android tells it. */
    @Provides
    fun localNetwork(phone: PhoneLocalNetworkAccess): LocalNetworkAccess = phone

    /** The tokens are kept sealed under a key of the device's keystore. */
    @Provides
    fun sealer(keystore: KeystoreSealer): Sealer = keystore

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CacheDatabase = CacheDatabase.open(context)

    @Provides
    fun cacheDao(db: CacheDatabase) = db.cacheDao()

    @Provides
    fun pendingChangeDao(db: CacheDatabase) = db.pendingChanges()

    @Provides
    fun keptReadsDao(db: CacheDatabase) = db.keptReads()
}
