// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FavoriteFilter
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A profile's marks asked for as narrowed, and a mark made, changed and taken off, against a local server answering as the backend does. */
class FavoriteRepositoryTest {
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

    private fun repository(): FavoriteRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return FavoriteRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    /** A captured recipe as the API marks it for a profile: the mark's own fields, then the recipe. */
    private fun marks(sentiment: String, tags: List<String>): MockResponse {
        val recipe = json.parseToJsonElement(fixture("recipe")).jsonObject
        val mark = JsonObject(
            mapOf(
                "id" to json.parseToJsonElement("\"8f8a4c1e-5b1d-4c62-9d0e-2b7f3d6a1c90\""),
                "tags" to json.parseToJsonElement(tags.joinToString(",", "[", "]") { "\"$it\"" }),
                "sentiment" to json.parseToJsonElement("\"$sentiment\""),
                "markedAt" to json.parseToJsonElement("\"2026-10-16T10:00:00.000Z\""),
                "recipe" to recipe,
            ),
        )
        return MockResponse().setHeader("Content-Type", "application/json").setBody("[$mark]")
    }

    @Test
    fun `the marks are asked for narrowed and ordered as the filter says, and the API's own order is not sent`() {
        repeat(2) { server.enqueue(marks("often", listOf("quick"))) }
        val favorites = repository()

        runBlocking {
            favorites.marked("ada", FavoriteFilter(search = " soup ", mealType = "lunch", sentiment = "often", tag = "quick", sort = "calories"))
            favorites.marked("ada", FavoriteFilter(search = "  "))
        }

        val narrowed = server.takeRequest().requestUrl!!
        assertEquals("/api/favorites", narrowed.encodedPath)
        assertEquals(
            mapOf("profileId" to "ada", "search" to "soup", "mealType" to "lunch", "sentiment" to "often", "tag" to "quick", "sort" to "calories"),
            narrowed.queryParameterNames.associateWith { narrowed.queryParameter(it) },
        )
        val every = server.takeRequest().requestUrl!!
        assertEquals(setOf("profileId"), every.queryParameterNames)
    }

    @Test
    fun `a mark is read with its recipe's title, meals and energy, how it is marked and the profile's tags`() {
        server.enqueue(marks("often", listOf("quick", "cheap")))
        val recipe = json.parseToJsonElement(fixture("recipe")).jsonObject

        val marked = runBlocking { repository().marked("ada") }

        assertEquals(
            listOf(
                MarkedRecipe(
                    recipeId = "137d1883-d5f4-4431-b596-2459b5a20507",
                    title = recipe.getValue("title").jsonPrimitive.content,
                    mealTypes = listOf("lunch", "dinner"),
                    sentiment = "often",
                    dietTags = recipe.getValue("dietTags").jsonArray.map { it.jsonPrimitive.content },
                    tags = listOf("quick", "cheap"),
                    calories = 326.0,
                ),
            ),
            marked,
        )
    }

    @Test
    fun `a mark is sent whole, how the recipe is marked with its tags, and taken off by the profile and the recipe`() {
        // The shape the API answers a mark with.
        server.enqueue(
            MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody(
                """{"id":"8f8a4c1e-5b1d-4c62-9d0e-2b7f3d6a1c90","profileId":"ada","recipeId":"soup","tags":["quick"],"sentiment":"avoid","createdAt":"2026-10-16T10:00:00.000Z"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))
        val favorites = repository()

        runBlocking {
            favorites.mark("ada", "soup", "avoid", listOf("quick"))
            favorites.unmark("ada", "soup")
        }

        val marked = server.takeRequest()
        assertEquals("POST", marked.method)
        assertEquals("/api/favorites", marked.path)
        assertEquals(
            json.parseToJsonElement("""{"profileId":"ada","recipeId":"soup","tags":["quick"],"sentiment":"avoid"}"""),
            json.parseToJsonElement(marked.body.readUtf8()),
        )
        val unmarked = server.takeRequest()
        assertEquals("DELETE", unmarked.method)
        assertEquals("/api/favorites/ada/soup", unmarked.path)
    }
}
