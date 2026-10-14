// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import net.whiteravens.dietapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** What an account signs in with, and the session its sign-up opened, for asking the API about it. */
data class Account(val email: String, val password: String, val displayName: String, val accessToken: String)

/**
 * The diet-app instance at the app's own address, which CI starts before the screens'
 * tests. What a test needs there before it opens a screen is made through the API, as the
 * web application would make it, and what a screen did is read back the same way.
 */
object Instance {
    private val client = OkHttpClient()
    private val json = "application/json".toMediaType()

    /** A new account, opened the way somebody signing up opens one. */
    fun newAccount(): Account {
        val id = UUID.randomUUID().toString().take(8)
        val email = "screens-$id@example.test"
        val password = "Screens-Test-$id-Passw0rd"
        val displayName = "Screens $id"
        val answer = call(
            "POST",
            "auth/register",
            JSONObject().put("email", email).put("password", password).put("displayName", displayName),
            expect = 201,
        )
        val accessToken = answer.getJSONObject("tokens").getString("accessToken")
        return Account(email, password, displayName, accessToken)
    }

    /**
     * Switches two-step sign-in on for [account], as the web application's settings do, with the
     * code an authenticator app would show; answers the recovery codes the API gives once.
     */
    fun switchSecondStepOn(account: Account): List<String> {
        val setup = call(
            "POST",
            "users/me/second-factor/setup",
            JSONObject().put("currentPassword", account.password),
            token = account.accessToken,
        )
        val codes = call(
            "POST",
            "users/me/second-factor",
            JSONObject().put("enrolment", setup.getString("enrolment")).put("code", totp(setup.getString("secret"))),
            token = account.accessToken,
            expect = 201,
        ).getJSONArray("recoveryCodes")
        return (0 until codes.length()).map { codes.getString(it) }
    }

    /** A profile of [account] with what the web application's form asks for, and the rest left to the API. */
    fun newProfile(account: Account, name: String = "Me"): String =
        call(
            "POST",
            "profiles",
            JSONObject().put("name", name).put("age", 30).put("sex", "male").put("heightCm", 180).put("weightKg", 80),
            token = account.accessToken,
            expect = 201,
        ).getString("id")

    /** The ids of a profile's plans, as the API lists them. */
    fun planIds(account: Account, profileId: String): List<String> {
        val items = call("GET", "meal-plans?profileId=$profileId", token = account.accessToken).getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it).getString("id") }
    }

    /** A plan's first day and how long the plan is: what its card in the list is titled with. */
    fun planStart(account: Account, planId: String): Pair<String, Int> {
        val plan = call("GET", "meal-plans/$planId", token = account.accessToken)
        return plan.getString("startDate") to plan.getInt("durationDays")
    }

    /** The recipe of each meal of a plan's first day, in the order the day lists them. */
    fun firstDayRecipes(account: Account, planId: String): List<String?> {
        val meals = call("GET", "meal-plans/$planId", token = account.accessToken)
            .getJSONArray("days").getJSONObject(0).getJSONArray("meals")
        return (0 until meals.length()).map { meals.getJSONObject(it).optJSONObject("recipe")?.getString("id") }
    }

    private fun call(method: String, path: String, body: JSONObject? = null, token: String? = null, expect: Int = 200): JSONObject {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + path)
            .method(method, body?.toString()?.toRequestBody(json))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            check(response.code == expect) { "$method $path answered ${response.code}: $text" }
            return JSONObject(text)
        }
    }
}
