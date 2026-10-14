// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import net.whiteravens.dietapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** What an account signs in with. */
data class Account(val email: String, val password: String, val displayName: String)

/**
 * The diet-app instance at the app's own address, which CI starts before the screens'
 * tests. What a test needs there before it opens a screen is made through the API, as the
 * web application would make it.
 */
object Instance {
    private val client = OkHttpClient()
    private val json = "application/json".toMediaType()

    /** A new account, opened the way somebody signing up opens one. */
    fun newAccount(): Account {
        val id = UUID.randomUUID().toString().take(8)
        val account = Account(
            email = "screens-$id@example.test",
            password = "Screens-Test-$id-Passw0rd",
            displayName = "Screens $id",
        )
        val body = JSONObject()
            .put("email", account.email)
            .put("password", account.password)
            .put("displayName", account.displayName)
            .toString()
            .toRequestBody(json)
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "auth/register").post(body).build()
        client.newCall(request).execute().use { response ->
            check(response.code == 201) { "Signing up answered ${response.code}: ${response.body.string()}" }
        }
        return account
    }
}
