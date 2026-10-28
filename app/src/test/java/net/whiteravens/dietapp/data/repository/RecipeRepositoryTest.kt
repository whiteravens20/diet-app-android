// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.OwnRecipe
import net.whiteravens.dietapp.domain.OwnRecipeLine
import net.whiteravens.dietapp.domain.RecipeDraftAsk
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipeProblem
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun answer(name: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    private val pancakes = OwnRecipe(
        title = "Oat pancakes",
        description = "Soft pancakes of oats, milk and eggs.",
        steps = listOf("Blend the oats with the milk and the eggs.", "Fry small pancakes on a dry pan."),
        servings = 2,
        mealTypes = listOf("breakfast"),
        prepMinutes = 10,
        cookMinutes = 15,
        difficulty = "easy",
        cuisine = null,
        lines = listOf(
            OwnRecipeLine(OATS, 80.0, "g"),
            OwnRecipeLine(MILK, 300.0, "ml", "warm"),
            OwnRecipeLine(EGG, 2.0, "piece"),
        ),
    )

    @Test
    fun `the ingredients a recipe written by hand can name are found by a part of their name, with the units each is given in`() {
        server.enqueue(answer("ingredients-search-egg"))

        val found = runBlocking { repository().ingredients("egg") }

        assertEquals("/api/ingredients?search=egg", server.takeRequest().path)
        assertEquals(listOf(LineIngredient(EGG, "Large egg", listOf("g", "piece"), "piece")), found)
    }

    @Test
    fun `what a serving holds is asked for without the notes, and a recipe is written and edited as the API takes it`() {
        server.enqueue(answer("own-recipe-preview"))
        server.enqueue(answer("own-recipe-created", 201))
        server.enqueue(answer("own-recipe-updated"))
        val recipes = repository()

        val preview = runBlocking { recipes.preview(2, pancakes.lines) }
        val written = runBlocking { recipes.write(pancakes) }
        val edited = runBlocking { recipes.edit(written.id, pancakes.copy(servings = 3)) }

        val asked = server.takeRequest()
        assertEquals("POST", asked.method)
        assertEquals("/api/recipes/preview", asked.path)
        assertEquals(
            """{"servings":2,"ingredients":[{"ingredientId":"$OATS","quantity":80.0,"unit":"g"},{"ingredientId":"$MILK","quantity":300.0,"unit":"ml"},""" +
                """{"ingredientId":"$EGG","quantity":2.0,"unit":"piece"}]}""",
            asked.body.readUtf8(),
        )
        assertEquals(325.0, preview.perServing.calories, 0.0)
        assertEquals(listOf("dairy", "eggs", "gluten"), preview.allergens)
        assertEquals(listOf("vegetarian"), preview.dietTags)

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/recipes", sent.path)
        // A cuisine and a note that are none are left out, as the API takes them to be.
        assertEquals(
            """{"title":"Oat pancakes","description":"Soft pancakes of oats, milk and eggs.",""" +
                """"steps":["Blend the oats with the milk and the eggs.","Fry small pancakes on a dry pan."],"servings":2,"mealTypes":["breakfast"],""" +
                """"prepMinutes":10,"cookMinutes":15,"difficulty":"easy","ingredients":[{"ingredientId":"$OATS","quantity":80.0,"unit":"g"},""" +
                """{"ingredientId":"$MILK","quantity":300.0,"unit":"ml","note":"warm"},{"ingredientId":"$EGG","quantity":2.0,"unit":"piece"}]}""",
            sent.body.readUtf8(),
        )
        assertEquals("1a2ede14-e07f-41bb-8f0f-17d3f2f80b36", written.id)
        assertTrue(written.own)

        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/recipes/1a2ede14-e07f-41bb-8f0f-17d3f2f80b36", put.path)
        // The new version is a recipe of its own.
        assertEquals("2d5c2e66-3704-4d3a-9dd7-991b87d6f20b", edited.id)
        assertEquals(3, edited.servings)
    }

    @Test
    fun `a recipe of the library is nobody's own, and one of the account's is its own however it was made`() {
        server.enqueue(page())
        server.enqueue(answer("recipes-mine"))
        val recipes = repository()

        val library = runBlocking { recipes.search() }
        val mine = runBlocking { recipes.mine() }

        assertFalse(library.items.any { it.own })
        assertTrue(mine.items.all { it.own })
    }

    @Test
    fun `a recipe to edit keeps its amounts, each line with its ingredient's units, asked for in one call`() {
        server.enqueue(answer("own-recipe-created"))
        server.enqueue(answer("ingredients-of-own-recipe"))

        val toEdit = runBlocking { repository().toEdit("1a2ede14-e07f-41bb-8f0f-17d3f2f80b36") }

        assertEquals("/api/recipes/1a2ede14-e07f-41bb-8f0f-17d3f2f80b36", server.takeRequest().path)
        assertEquals("/api/ingredients?ids=$OATS%2C$MILK%2C$EGG", server.takeRequest().path)
        assertEquals(pancakes, toEdit.recipe)
        assertEquals(
            listOf(
                LineIngredient(OATS, "Rolled oats", listOf("g"), "piece"),
                LineIngredient(MILK, "Whole milk", listOf("ml", "g"), "piece"),
                LineIngredient(EGG, "Large egg", listOf("g", "piece"), "piece"),
            ),
            toEdit.ingredients,
        )
    }

    @Test
    fun `an ingredient the catalogue no longer names keeps the unit its line is in`() {
        server.enqueue(answer("own-recipe-created"))
        // The catalogue answers for the milk alone.
        server.enqueue(answer("ingredients-search-milk"))

        val toEdit = runBlocking { repository().toEdit("1a2ede14-e07f-41bb-8f0f-17d3f2f80b36") }

        assertEquals(
            listOf(listOf("g"), listOf("ml", "g"), listOf("piece")),
            toEdit.ingredients.map { it.units },
        )
        assertEquals(listOf("Rolled oats", "Whole milk", "Large egg"), toEdit.ingredients.map { it.name })
    }

    @Test
    fun `a refused recipe says the rules it would break, or the recipe of one's own it would repeat`() {
        server.enqueue(answer("own-recipe-invalid", 400))
        server.enqueue(answer("own-recipe-already-yours", 409))
        server.enqueue(answer("own-recipe-refused", 400))
        val recipes = repository()

        fun refused() = runCatching { runBlocking { recipes.write(pancakes) } }.exceptionOrNull() as ApiException
        val invalid = refused()
        val repeated = refused()
        val outOfBounds = refused()

        assertEquals(listOf(RecipeProblem("titles.en", "markup")), recipes.problemsOf(invalid))
        assertNull(recipes.alreadyYours(invalid))
        assertEquals("1a2ede14-e07f-41bb-8f0f-17d3f2f80b36", recipes.alreadyYours(repeated))
        assertNull(recipes.problemsOf(repeated))
        assertNull(recipes.problemsOf(outOfBounds))
        assertNull(recipes.alreadyYours(outOfBounds))
        assertEquals(
            mapOf(
                "servings" to FieldRefusal.TooBig(12.0, true),
                "steps" to FieldRefusal.TooSmall(1.0, true),
                "prepMinutes" to FieldRefusal.TooBig(480.0, true),
            ),
            outOfBounds.fieldRefusals,
        )
    }

    private companion object {
        const val OATS = "a0fb65dc-13be-471d-a952-7a43a14b19f1"
        const val MILK = "e37a806f-6e15-443b-982b-ee6957bc2729"
        const val EGG = "e8ca52e7-16a4-41fa-b861-966769b3944e"
    }
}
