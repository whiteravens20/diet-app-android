// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.R
import okhttp3.Interceptor
import okhttp3.Response
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What every request says of the app, on every client, signing in and refreshing included:
 * which app, version and system it is (the account's list of sessions names a session after
 * the name before the slash and the system), the language the app's texts resolved to, which
 * the API answers in and keeps as the account's, and the day it is for the user, which the API
 * counts days by.
 */
@Singleton
class ClientHeaders @Inject constructor(@ApplicationContext private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept-Language", context.getString(R.string.api_language))
                .header("X-Client-Date", LocalDate.now().toString())
                .build(),
        )

    companion object {
        /** `DietApp-Android/1.0 (Android 15)`. */
        val userAgent: String get() = "DietApp-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"
    }
}
