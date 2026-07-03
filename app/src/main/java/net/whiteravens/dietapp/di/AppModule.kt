package net.whiteravens.dietapp.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.auth.AuthInterceptor
import net.whiteravens.dietapp.data.auth.TokenAuthenticator
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.DietApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the token-free OkHttp/Retrofit pair. [AuthApiService] must ride this
 * one — a refresh call through the authenticated client would recurse.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Unauthenticated

/** Hilt graph: networking, JSON, auth plumbing and the offline cache database. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // `ignoreUnknownKeys` tolerates fields the platform adds before we mirror
    // them; `explicitNulls = false` keeps unset optional fields out of PATCH
    // bodies instead of sending `null` (which the Zod schemas reject).
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
    fun baseOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
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

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): DietApiService =
        retrofit(client, json).create(DietApiService::class.java)

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "diet-app-cache.db").build()

    @Provides
    fun cacheDao(db: CacheDatabase) = db.cacheDao()
}
