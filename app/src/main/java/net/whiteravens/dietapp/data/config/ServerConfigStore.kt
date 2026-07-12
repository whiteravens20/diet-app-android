package net.whiteravens.dietapp.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.whiteravens.dietapp.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverDataStore by preferencesDataStore(name = "server")

/**
 * The API base URL as a device setting. The compiled-in default
 * (`BuildConfig.API_BASE_URL`) applies until the user saves an override —
 * set once from the login screen or later from the profile screen. All
 * traffic follows it via [net.whiteravens.dietapp.data.remote.BaseUrlInterceptor].
 */
@Singleton
class ServerConfigStore @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.serverDataStore

    /** Effective base URL — the saved override, or the build-time default. */
    val baseUrl: Flow<String> = store.data.map { prefs ->
        prefs[BASE_URL] ?: BuildConfig.API_BASE_URL
    }

    suspend fun current(): String = baseUrl.first()

    /** Persists a base URL previously validated with [normalize]. */
    suspend fun save(url: String) {
        store.edit { prefs -> prefs[BASE_URL] = url }
    }

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")

        /**
         * Validates user input into a canonical base URL (trailing slash so
         * Retrofit's relative paths resolve under it), or null when it is not
         * an http(s) URL.
         */
        fun normalize(input: String): String? {
            val trimmed = input.trim().let { if (it.endsWith("/")) it else "$it/" }
            val url = trimmed.toHttpUrlOrNull() ?: return null
            return url.toString()
        }
    }
}
