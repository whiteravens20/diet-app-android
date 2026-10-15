// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The recipe library asked for as narrowed, against a local server answering what the backend answered. */
class RecipeRepositoryTest {
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

    private fun repository(): RecipeRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return RecipeRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun page() = MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("recipes"))

    @Test
    fun `every filter is sent as the API takes it, the lists parted by commas, and the profile only with its marks or favourites`() {
        repeat(3) { server.enqueue(page()) }
        val filter = RecipeFilter(
            search = " soup ",
            dietType = "vegan",
            mealType = "lunch",
            difficulty = "easy",
            maxCalories = 600,
            maxPrepMinutes = 20,
            cuisine = "italian",
            minProtein = 30.0,
            maxFat = 20.5,
            maxCarbs = 50.0,
            ingredients = listOf(IngredientChoice("a", "Oats", "grains"), IngredientChoice("b", "Milk", "dairy")),
            withoutAllergens = listOf("gluten", "dairy"),
            profileId = "ada",
        )
        val recipes = repository()

        runBlocking {
            recipes.search(filter, page = 2)
            recipes.search(RecipeFilter(profileId = "ada", marked = "often"))
            recipes.search(RecipeFilter(profileId = "ada", favoriteIngredients = true))
        }

        val narrowed = server.takeRequest().requestUrl!!
        assertEquals("/api/recipes", narrowed.encodedPath)
        assertEquals(
            mapOf(
                "search" to "soup", "dietType" to "vegan", "mealType" to "lunch", "difficulty" to "easy", "maxCalories" to "600",
                "maxPrepMinutes" to "20", "cuisine" to "italian", "minProtein" to "30.0", "maxFat" to "20.5", "maxCarbs" to "50.0",
                "ingredientIds" to "a,b", "withoutAllergens" to "gluten,dairy", "page" to "2",
            ),
            narrowed.queryParameterNames.associateWith { narrowed.queryParameter(it) },
        )
        assertNull(narrowed.queryParameter("profileId"))
        val marked = server.takeRequest().requestUrl!!
        assertEquals("ada", marked.queryParameter("profileId"))
        assertEquals("often", marked.queryParameter("marked"))
        val favourites = server.takeRequest().requestUrl!!
        assertEquals("true", favourites.queryParameter("favoriteIngredients"))
        assertNull(favourites.queryParameter("marked"))
    }
}
