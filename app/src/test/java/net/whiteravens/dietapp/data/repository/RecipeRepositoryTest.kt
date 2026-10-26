// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.RecipeDraftAsk
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

/** The recipe library asked for as narrowed, and one's own recipes, against a local server answering what the backend answered. */
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
        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
        return RecipeRepository(AppModule.api(client, json), AppModule.modelApi(client, json), json)
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

    @Test
    fun `one's own recipes are listed at their own address, suggested for the shared library and deleted`() {
        server.enqueue(page())
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val recipes = repository()

        runBlocking {
            recipes.mine(page = 1)
            recipes.submit("mine")
            recipes.delete("mine")
        }

        assertEquals("/api/recipes/mine?page=1", server.takeRequest().path)
        val submitted = server.takeRequest()
        assertEquals("POST", submitted.method)
        assertEquals("/api/recipes/mine/submit", submitted.path)
        val deleted = server.takeRequest()
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/recipes/mine", deleted.path)
    }

    @Test
    fun `one's own recipe made by swapping an ingredient is told apart from one the instance's model wrote, which the curators have already`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("recipes-mine")))

        val mine = runBlocking { repository().mine(page = 1) }

        assertEquals(listOf(true, false), mine.items.map { it.fromSwap })
        assertEquals(listOf(false, true), mine.items.map { it.submitted })
    }

    @Test
    fun `a draft is asked for with what was chosen, the switches only where they differ from the API's own, and the recipe written is read`() {
        // The shape the API answers with: the recipe as the engine worked it out, and the model that wrote it.
        val written = """{"recipe":${fixture("recipe")},"aiMeta":{"provider":"openrouter","model":"m","failoverChain":[],"usedDeterministicFallback":false,"fallbackReason":null,"reason":null}}"""
        repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(written)) }
        val recipes = repository()

        val recipe = runBlocking { recipes.draft(RecipeDraftAsk(profileId = "ada", dietType = "vegan", mealType = "lunch", kcalTarget = 500)) }
        runBlocking { recipes.draft(RecipeDraftAsk(profileId = "ada", dietType = "vegan", useFavoriteIngredients = true, respectExclusions = false, addToFavorites = false)) }

        val first = server.takeRequest()
        assertEquals("/api/recipes/drafts/from-prompt", first.path)
        assertEquals("""{"profileId":"ada","mealType":"lunch","dietType":"vegan","kcalTarget":500}""", first.body.readUtf8())
        assertEquals(
            """{"profileId":"ada","dietType":"vegan","useFavoriteIngredients":true,"respectExclusions":false,"addToFavorites":false}""",
            server.takeRequest().body.readUtf8(),
        )
        assertEquals(json.parseToJsonElement(fixture("recipe")).jsonObject.getValue("id").jsonPrimitive.content, recipe.id)
    }
}
