// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import net.whiteravens.dietapp.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/** What an account signs in with, and the session its sign-up opened (its tokens and id), for asking the API about it. */
data class Account(
    val email: String,
    val password: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
)

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
        val tokens = answer.getJSONObject("tokens")
        return Account(email, password, displayName, tokens.getString("accessToken"), tokens.getString("refreshToken"), answer.getString("sessionId"))
    }

    /** Signs [account] in with [password] as another app would, and answers the access token of the session that opens. */
    fun signIn(account: Account, password: String = account.password): String =
        call("POST", "auth/login", JSONObject().put("email", account.email).put("password", password))
            .getJSONObject("tokens").getString("accessToken")

    /** The open sessions of the account [token] is of, each id with its label. */
    fun sessions(token: String): Map<String, String?> {
        val open = JSONArray(send("GET", "users/me/sessions", null, token, 200, null))
        return (0 until open.length()).map { open.getJSONObject(it) }
            .associate { session -> session.getString("id") to (if (session.isNull("label")) null else session.getString("label")) }
    }

    /** Whether [account] signs in with a second step, as its sign-up's session reads it. */
    fun secondFactorOn(account: Account): Boolean =
        call("GET", "users/me/second-factor", token = account.accessToken).getBoolean("enabled")

    /** Whether [account] signs in with its password: false once the account is gone. */
    fun signsIn(account: Account): Boolean =
        exchange("POST", "auth/login", JSONObject().put("email", account.email).put("password", account.password), null, null).first == 200

    /** Whether [token] is still the access token of an open session. */
    fun stillSignedIn(token: String): Boolean = exchange("GET", "users/me", null, token, null).first == 200

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

    /** The account's way of using a model: `none` where it has none to ask. */
    fun aiMode(account: Account): String = call("GET", "users/me", token = account.accessToken).getString("aiMode")

    /** The account as the API keeps it: its name, the language of its mails, what it chose. */
    fun me(account: Account): JSONObject = call("GET", "users/me", token = account.accessToken)

    /** The unit [account] chose for weights, `kg` or `lb`, or null while it goes by where the phone is. */
    fun weightUnit(account: Account): String? =
        call("GET", "users/me", token = account.accessToken).let { if (it.isNull("weightUnit")) null else it.getString("weightUnit") }

    /** Chooses the unit [account]'s weights are shown in, as the web application's settings do. */
    fun chooseWeightUnit(account: Account, unit: String) {
        call("PATCH", "users/me", JSONObject().put("weightUnit", unit), token = account.accessToken)
    }

    /** Logs [kg] for a profile of [account], taken at noon of [day] (UTC). */
    fun logWeight(account: Account, profileId: String, kg: Double, day: LocalDate) {
        call(
            "POST",
            "weights",
            JSONObject().put("profileId", profileId).put("kg", kg).put("recordedAt", "${day}T12:00:00.000Z"),
            token = account.accessToken,
            expect = 201,
        )
    }

    /** The weights a profile of [account] logged, in kilograms, the newest first. */
    fun weights(account: Account, profileId: String): List<Double> {
        val list = JSONArray(send("GET", "weights?profileId=$profileId", null, account.accessToken, 200, null))
        return (0 until list.length()).map { list.getJSONObject(it).getDouble("kg") }
    }

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

    /** The first [count] recipes of the catalogue in [language]: their ids and titles. */
    fun recipes(account: Account, count: Int, language: String): List<Pair<String, String>> {
        val items = call("GET", "recipes?page=1", token = account.accessToken, language = language).getJSONArray("items")
        return (0 until minOf(count, items.length())).map { items.getJSONObject(it).let { recipe -> recipe.getString("id") to recipe.getString("title") } }
    }

    /** The first recipe of the catalogue for [mealType], in [language]: its id and title. */
    fun aRecipeFor(account: Account, mealType: String, language: String): Pair<String, String> {
        val first = call("GET", "recipes?page=1&mealType=$mealType", token = account.accessToken, language = language).getJSONArray("items").getJSONObject(0)
        return first.getString("id") to first.getString("title")
    }

    /** A favourite set of [profileId] called [label], with the recipe [slots] names for each meal, as the web application keeps one. */
    fun newFavoriteSet(account: Account, profileId: String, label: String, slots: Map<String, String>): String =
        call(
            "POST",
            "favorite-sets",
            JSONObject().put("profileId", profileId).put("label", label).put("slots", JSONObject(slots)),
            token = account.accessToken,
            expect = 201,
        ).getString("id")

    /** The recipe each day of a plan has for [mealType], in the order of the days; null for a day without one. */
    fun recipesFor(account: Account, planId: String, mealType: String): List<String?> {
        val days = call("GET", "meal-plans/$planId", token = account.accessToken).getJSONArray("days")
        return (0 until days.length()).map { d ->
            val meals = days.getJSONObject(d).getJSONArray("meals")
            (0 until meals.length()).map(meals::getJSONObject).firstOrNull { it.getString("mealType") == mealType }?.optJSONObject("recipe")?.getString("id")
        }
    }

    /** The favourite sets of [profileId], the one made last first. */
    fun favoriteSets(account: Account, profileId: String): List<JSONObject> {
        val list = JSONArray(send("GET", "favorite-sets?profileId=$profileId", null, account.accessToken, 200, null))
        return (0 until list.length()).map { list.getJSONObject(it) }
    }

    /** The ingredients of a recipe's lines, in the order the recipe lists them. */
    fun recipeIngredients(account: Account, recipeId: String): List<String> {
        val lines = call("GET", "recipes/$recipeId", token = account.accessToken).getJSONArray("ingredients")
        return (0 until lines.length()).map { lines.getJSONObject(it).getString("ingredientId") }
    }

    /** The ingredients a profile's preferences hold in the list called [list] (`favoriteIngredientIds`, `excludedIngredientIds`, …). */
    fun preferredIngredients(account: Account, profileId: String, list: String): List<String> {
        val ids = profile(account, profileId).getJSONObject("preferences").getJSONArray(list)
        return (0 until ids.length()).map(ids::getString)
    }

    /** Marks [recipeId] for [profileId] as [sentiment], under [tags], as the web application marks one. */
    fun mark(account: Account, profileId: String, recipeId: String, sentiment: String, tags: List<String> = emptyList()) {
        call(
            "POST",
            "favorites",
            JSONObject().put("profileId", profileId).put("recipeId", recipeId).put("sentiment", sentiment).put("tags", JSONArray(tags)),
            token = account.accessToken,
            expect = 201,
        )
    }

    /** The recipes [profileId] marked, by the recipe's id: how each is marked, and its tags. */
    fun marks(account: Account, profileId: String): Map<String, Pair<String, List<String>>> {
        val list = JSONArray(send("GET", "favorites?profileId=$profileId", null, account.accessToken, 200, null))
        return (0 until list.length()).map { list.getJSONObject(it) }.associate { mark ->
            val tags = mark.getJSONArray("tags")
            mark.getJSONObject("recipe").getString("id") to (mark.getString("sentiment") to (0 until tags.length()).map(tags::getString))
        }
    }

    /** The ingredient of the catalogue called [english] in English: its id, and its name in [language]. */
    fun ingredient(account: Account, english: String, language: String): Pair<String, String> {
        val found = JSONArray(send("GET", "ingredients?search=${URLEncoder.encode(english, "UTF-8")}", null, account.accessToken, 200, "en"))
        val id = (0 until found.length()).map(found::getJSONObject).first { it.getString("name") == english }.getString("id")
        return id to JSONArray(send("GET", "ingredients?ids=$id", null, account.accessToken, 200, language)).getJSONObject(0).getString("name")
    }

    /** Puts [quantity] of an ingredient, in [unit], into a profile's pantry, best before [bestBefore], as the web application's form puts it in. */
    fun stock(account: Account, profileId: String, ingredientId: String, quantity: Double, unit: String, bestBefore: LocalDate) {
        call(
            "POST",
            "inventory",
            JSONObject().put("profileId", profileId).put("ingredientId", ingredientId).put("quantity", quantity).put("unit", unit)
                .put("bestBefore", bestBefore.toString()),
            token = account.accessToken,
            expect = 201,
        )
    }

    /** A profile's pantry, by the ingredient of each row: its quantity in the row's unit, the unit, and its day or null. */
    fun pantry(account: Account, profileId: String): Map<String, Triple<Double, String, String?>> {
        val rows = JSONArray(send("GET", "inventory?profileId=$profileId", null, account.accessToken, 200, null))
        return (0 until rows.length()).map(rows::getJSONObject).associate { row ->
            row.getJSONObject("ingredient").getString("id") to
                Triple(row.getDouble("quantity"), row.getString("unit"), if (row.isNull("bestBefore")) null else row.getString("bestBefore"))
        }
    }

    /** The ids of the shopping lists of [planId], the newest first. */
    fun shoppingListIds(account: Account, planId: String): List<String> {
        val lists = JSONArray(send("GET", "shopping-lists?planId=$planId", null, account.accessToken, 200, null))
        return (0 until lists.length()).map { lists.getJSONObject(it).getString("id") }
    }

    /** How many things the API finds that the shopping list [listId] could do without; nothing is written by asking. */
    fun listProposals(account: Account, listId: String): Int =
        call("POST", "shopping-lists/$listId/proposals", JSONObject(), token = account.accessToken).getJSONArray("proposals").length()

    /** What is at home of the row called [name] on the newest shopping list of [planId] in [language]. */
    fun shoppingRowAtHome(account: Account, planId: String, name: String, language: String): Double {
        val lists = JSONArray(send("GET", "shopping-lists?planId=$planId", null, account.accessToken, 200, language))
        val groups = lists.getJSONObject(0).getJSONArray("groups")
        return (0 until groups.length()).flatMap { g -> groups.getJSONObject(g).getJSONArray("items").let { items -> (0 until items.length()).map(items::getJSONObject) } }
            .first { it.getString("name") == name }.getDouble("alreadyHaveQuantity")
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

    /**
     * Asks the instance, and waits as a person would where it says to: every test signs up from the
     * same address, which the instance lets open ten accounts a minute, and a quick run of tests
     * opens more. A refusal for that is asked again after the wait it names, a few times.
     */
    private fun send(method: String, path: String, body: JSONObject?, token: String?, expect: Int, language: String?): String {
        val (code, text) = exchange(method, path, body, token, language, waitWhenHeldUp = expect != 429)
        check(code == expect) { "$method $path answered $code: $text" }
        return text
    }

    /** The status and the body of an answer, a hold-up waited out unless [waitWhenHeldUp] is false. */
    private fun exchange(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        language: String?,
        waitWhenHeldUp: Boolean = true,
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + path)
            .method(method, body?.toString()?.toRequestBody(json))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .apply { if (language != null) header("Accept-Language", language) }
            .header("X-Client-Date", LocalDate.now().toString())
            .build()
        repeat(HELD_UP_TRIES) {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.code == 429 && waitWhenHeldUp) {
                    Thread.sleep(1_000L * (response.header("Retry-After")?.toLongOrNull() ?: 10L).coerceIn(1L, 90L))
                    return@repeat
                }
                return response.code to text
            }
        }
        error("$method $path was held up $HELD_UP_TRIES times")
    }

    /** How many times a request the instance holds up is asked. */
    private const val HELD_UP_TRIES = 4
}
