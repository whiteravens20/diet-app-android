// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import net.whiteravens.dietapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/** What an account signs in with, and the session its sign-up opened, for asking the API about it. */
data class Account(val email: String, val password: String, val displayName: String, val accessToken: String)

/**
 * The diet-app instance at the app's own address, which CI starts before the screens'
 * tests. What a test needs there before it opens a screen is made through the API, as the
 * web application would make it, and what a screen did is read back the same way. Every
 * request says the day it is on the phone, as the app's own do.
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

    /** Whether the instance sends mail, as its configuration says. */
    fun mailsSent(): Boolean = call("GET", "config").getJSONObject("email").getBoolean("enabled")

    /** The profiles of [account], as the API keeps them. */
    fun profiles(account: Account): List<JSONObject> {
        val list = JSONArray(send("GET", "profiles", null, account.accessToken, 200, null))
        return (0 until list.length()).map { list.getJSONObject(it) }
    }

    /** A profile of [account], as the API keeps it. */
    fun profile(account: Account, profileId: String): JSONObject = call("GET", "profiles/$profileId", token = account.accessToken)

    /** The ids of a profile's plans, as the API lists them. */
    fun planIds(account: Account, profileId: String): List<String> {
        val items = call("GET", "meal-plans?profileId=$profileId", token = account.accessToken).getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it).getString("id") }
    }

    /** How many times a plan has been changed. */
    fun planRevision(account: Account, planId: String): Int = call("GET", "meal-plans/$planId", token = account.accessToken).getInt("revision")

    /** What a plan was made with: its options as the API keeps them. */
    fun planOptions(account: Account, planId: String): JSONObject =
        call("GET", "meal-plans/$planId", token = account.accessToken).getJSONObject("options")

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

    /** The title of the first meal of a plan's first day, and the names of its lines as planned. */
    fun firstMeal(account: Account, planId: String): Pair<String, List<String>> {
        val meal = call("GET", "meal-plans/$planId", token = account.accessToken)
            .getJSONArray("days").getJSONObject(0).getJSONArray("meals").getJSONObject(0)
        val lines = meal.getJSONArray("ingredients")
        return meal.getJSONObject("recipe").getString("title") to
            (0 until lines.length()).map { lines.getJSONObject(it).getString("name") }
    }

    /** Whether the first meal of a plan's first day is marked eaten. */
    fun firstMealEaten(account: Account, planId: String): Boolean =
        !call("GET", "meal-plans/$planId", token = account.accessToken)
            .getJSONArray("days").getJSONObject(0).getJSONArray("meals").getJSONObject(0).isNull("eatenAt")

    /** A meal of a plan's first day that is the catalogue's and not eaten: its title, and the name of its first line. */
    fun uneatenMeal(account: Account, planId: String): Pair<String, String> {
        val meals = call("GET", "meal-plans/$planId", token = account.accessToken).getJSONArray("days").getJSONObject(0).getJSONArray("meals")
        val meal = (0 until meals.length()).map { meals.getJSONObject(it) }.first { it.getString("source") == "CATALOGUE" && it.isNull("eatenAt") }
        return meal.getJSONObject("recipe").getString("title") to meal.getJSONArray("ingredients").getJSONObject(0).getString("name")
    }

    /** The names of the meals of the user's own on a plan's first day. */
    fun ownMeals(account: Account, planId: String): List<String> {
        val meals = call("GET", "meal-plans/$planId", token = account.accessToken).getJSONArray("days").getJSONObject(0).getJSONArray("meals")
        return (0 until meals.length()).map { meals.getJSONObject(it) }.filter { it.getString("source") == "USER_CUSTOM" }.map { it.getString("customName") }
    }

    /** Whether a plan's menu leaves more than a fifth of a pack that does not keep once opened. */
    fun wastesAPack(account: Account, planId: String): Boolean {
        val open = call("GET", "meal-plans/$planId", token = account.accessToken).getJSONObject("packs").getJSONArray("open")
        return (0 until open.length()).any { open.getJSONObject(it).getBoolean("wasted") }
    }

    /** How many swaps the API finds that would use up what a plan opens; nothing is written by asking. */
    fun useUpSwaps(account: Account, planId: String): Int =
        call("POST", "meal-plans/$planId/use-up", JSONObject(), token = account.accessToken).getJSONArray("suggestions").length()

    /** A plan of [days] days for [profileId] from [start], made as the app makes one. */
    fun newPlan(account: Account, profileId: String, days: Int = 2, start: LocalDate = LocalDate.now()): String =
        call(
            "POST",
            "meal-plans/generate",
            JSONObject().put("profileId", profileId).put("startDate", start.toString()).put("durationDays", days),
            token = account.accessToken,
            expect = 201,
        ).getString("id")

    /** The daily calorie target the API computes for [profileId], in whole kilocalories. */
    fun dailyTarget(account: Account, profileId: String): Int =
        call("GET", "profiles/$profileId/calories", token = account.accessToken).getDouble("dailyTarget").roundToInt()

    /** The first recipe of the catalogue in [language]: its title, and the name of its first ingredient. */
    fun aRecipe(account: Account, language: String): Pair<String, String> {
        val first = call("GET", "recipes?page=1", token = account.accessToken, language = language)
            .getJSONArray("items").getJSONObject(0)
        val recipe = call("GET", "recipes/${first.getString("id")}", token = account.accessToken, language = language)
        return recipe.getString("title") to recipe.getJSONArray("ingredients").getJSONObject(0).getString("name")
    }

    /** The rows of the shopping list of [planId] in [language], by name, and whether each is ticked. */
    fun shoppingRows(account: Account, planId: String, language: String): Map<String, Boolean> {
        val lists = JSONArray(send("GET", "shopping-lists?planId=$planId", null, account.accessToken, 200, language))
        val groups = lists.getJSONObject(0).getJSONArray("groups")
        return (0 until groups.length()).flatMap { g ->
            val items = groups.getJSONObject(g).getJSONArray("items")
            (0 until items.length()).map { items.getJSONObject(it).let { item -> item.getString("name") to item.getBoolean("checked") } }
        }.toMap()
    }

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        expect: Int = 200,
        language: String? = null,
    ): JSONObject = JSONObject(send(method, path, body, token, expect, language))

    private fun send(method: String, path: String, body: JSONObject?, token: String?, expect: Int, language: String?): String {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + path)
            .method(method, body?.toString()?.toRequestBody(json))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .apply { if (language != null) header("Accept-Language", language) }
            .header("X-Client-Date", LocalDate.now().toString())
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            check(response.code == expect) { "$method $path answered ${response.code}: $text" }
            return text
        }
    }
}
