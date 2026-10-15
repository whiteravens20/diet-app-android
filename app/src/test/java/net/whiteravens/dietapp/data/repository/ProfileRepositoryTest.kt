// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.PreferencesChange
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A profile as the app makes, keeps and previews it, against a local server answering what a running backend answered. */
class ProfileRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): ProfileRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
        return ProfileRepository(AppModule.api(client, json), json)
    }

    private fun answer(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val figures = ProfileFigures(
        name = "Ala", age = 30, sex = null, heightCm = 168.0, weightKg = 64.5, targetWeightKg = null,
        activityLevel = "light", dietType = "balanced", weeklyLossTarget = null, manualCalorieTarget = null,
        mealCount = 4, weightReminderCadence = "weekly", customMacroSplit = null, allergens = listOf("gluten"),
    )

    @Test
    fun `a new profile is sent with its choices of none, and its allergens alone of the preferences`() {
        server.enqueue(answer(fixture("profile"), 201))

        runBlocking { repository().create(figures, limits = null, acknowledgeRisk = null) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/profiles", sent.path)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(JsonNull, body["weeklyLossTarget"])
        assertEquals(JsonNull, body["sex"])
        assertFalse("acknowledgeRisk" in body)
        val preferences = body.getValue("preferences").jsonObject
        assertEquals(setOf("allergens"), preferences.keys)
        assertEquals(listOf("gluten"), preferences.getValue("allergens").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `a new profile made in the form is sent with the limits on repeats it chose`() {
        server.enqueue(answer(fixture("profile"), 201))

        runBlocking { repository().create(figures, PlanLimits(1, 2, 2500, 0), acknowledgeRisk = true) }

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("true", body.getValue("acknowledgeRisk").jsonPrimitive.content)
        val preferences = body.getValue("preferences").jsonObject
        assertEquals("1", preferences.getValue("maxConsecutiveDaysSameMeal").jsonPrimitive.content)
        assertEquals("2", preferences.getValue("maxTimesPerWeekSameMeal").jsonPrimitive.content)
        assertEquals("2500", preferences.getValue("maxWeeklyAmountPerIngredient").jsonPrimitive.content)
        assertEquals("0", preferences.getValue("inventoryBiasResetEvery").jsonPrimitive.content)
    }

    @Test
    fun `the account's profiles are read whole, their preferences with them`() {
        server.enqueue(answer(fixture("profiles")))

        val list = runBlocking { repository().profiles() }

        assertEquals("/api/profiles", server.takeRequest().path)
        assertTrue(list.isNotEmpty())
        assertEquals(3000, list.first().preferences.maxWeeklyAmountPerIngredient)
    }

    @Test
    fun `a change of preferences sends what it sets and nothing else`() {
        server.enqueue(answer(fixture("profile")))

        runBlocking { repository().updatePreferences("p", PreferencesChange(favoriteIngredientIds = listOf("a", "b"))) }

        val sent = server.takeRequest()
        assertEquals("PATCH", sent.method)
        assertEquals("/api/profiles/p/preferences", sent.path)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(setOf("favoriteIngredientIds"), body.keys)
    }

    @Test
    fun `ingredients are named by their ids in one call, and none are asked for when there are none`() {
        server.enqueue(answer("""[{"id":"a","name":"Apple","category":"fruits"},{"id":"b","name":"Bean","category":"legumes"}]"""))

        val none = runBlocking { repository().ingredientsOf(emptyList()) }
        val named = runBlocking { repository().ingredientsOf(listOf("a", "b")) }

        assertEquals(emptyList<IngredientChoice>(), none)
        assertEquals(listOf("Apple", "Bean"), named.map { it.name })
        assertEquals("/api/ingredients?ids=a%2Cb", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a profile kept anew keeps its preferences as they were, its allergens as the form has them`() {
        server.enqueue(answer(fixture("profile")))
        server.enqueue(answer(fixture("profile")))
        val kept = runBlocking { repository().profile("p") }

        runBlocking { repository().update(kept.id, kept.figures.copy(allergens = listOf("milk")), kept.preferences, acknowledgeRisk = null) }

        server.takeRequest()
        val sent = server.takeRequest()
        assertEquals("PUT", sent.method)
        val preferences = json.parseToJsonElement(sent.body.readUtf8()).jsonObject.getValue("preferences").jsonObject
        assertEquals(listOf("milk"), preferences.getValue("allergens").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(kept.preferences.maxWeeklyAmountPerIngredient.toString(), preferences.getValue("maxWeeklyAmountPerIngredient").jsonPrimitive.content)
    }

    @Test
    fun `the preview of typed figures gives their target and one for each pace, slowest first`() {
        val calculation = fixture("profile-calories")
        server.enqueue(answer("""{"calculation":$calculation,"paces":[$calculation,$calculation,$calculation,$calculation,$calculation]}"""))

        val question = CalorieQuestion(
            age = 30, sex = null, heightCm = 168.0, weightKg = 64.5, activityLevel = "light", dietType = "balanced",
            customMacroSplit = null, weeklyLossTarget = null, manualCalorieTarget = null, acknowledgeRisk = false,
        )

        val preview = runBlocking { repository().preview(question) }

        val sent = server.takeRequest()
        assertEquals("/api/profiles/calories", sent.path)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(JsonNull, body["weeklyLossTarget"])
        assertEquals("false", body.getValue("acknowledgeRisk").jsonPrimitive.content)
        assertEquals(5, preview.paces.size)
        assertEquals(preview.calculation.dailyTarget, preview.paces.first().dailyTarget, 0.0)
    }
}
