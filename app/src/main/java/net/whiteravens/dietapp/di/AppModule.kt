package net.whiteravens.dietapp.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.remote.DietApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/** Hilt graph: networking, JSON and the offline cache database. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder().build()
    // Phase 3: add an auth interceptor that attaches the access token and
    // refreshes it on 401, mirroring the web client.

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): DietApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DietApiService::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "diet-app-cache.db").build()

    @Provides
    fun cacheDao(db: CacheDatabase) = db.cacheDao()
}
