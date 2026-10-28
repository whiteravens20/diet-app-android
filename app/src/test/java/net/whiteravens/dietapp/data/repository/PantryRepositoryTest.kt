// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** A profile's pantry read, something put in, a row changed and one taken out, against a local server answering as the backend did. */
class PantryRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val profileId = "0c4b8a1e-5f43-4a52-9a43-3c1b2d0e7f61"
    private val garlic = PantryRow(
        "2823592c-99e3-4fa3-ac3d-e34e12f9f5e4", "2dad81f3-0d9d-44e1-9ef3-d275bc7782ee", "Garlic", "spices", 3.0, "clove", LocalDate.parse("2026-11-17"),
    )
    private val egg = PantryRow(
        "e96344dd-d122-420f-a6f6-86fde556fb0d", "e8ca52e7-16a4-41fa-b861-966769b3944e", "Large egg", "dairy", 6.0, "piece", LocalDate.parse("2026-11-02"),
    )
    private val oats = PantryRow(
        "db4c9d12-5829-4bbc-88bb-54564b57cc45", "a0fb65dc-13be-471d-a952-7a43a14b19f1", "Rolled oats", "grains", 212.0, "g", LocalDate.parse("2027-05-16"),
    )
    private val milk = PantryRow("bf795e4d-a979-40bd-8844-17c79108810d", "e37a806f-6e15-443b-982b-ee6957bc2729", "Whole milk", "dairy", 1000.0, "ml", null)

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): PantryRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return PantryRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    private fun sentBody() = json.parseToJsonElement(server.takeRequest().body.readUtf8())

    @Test
    fun `a profile's pantry is asked for by the profile, and its rows read in the API's order, as the API shows each`() {
        server.enqueue(answer(200, "pantry"))

        val rows = runBlocking { repository().rows(profileId) }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/inventory", asked.encodedPath)
        assertEquals(profileId, asked.queryParameter("profileId"))
        // The oats are kept to the tenth of a gram and shown to the gram; the garlic is counted in cloves.
        assertEquals(listOf(egg, milk, oats, garlic), rows)
    }

    @Test
    fun `something put in is sent with its profile, amount, unit and day, and read back as the row it is kept in`() {
        server.enqueue(answer(201, "pantry-row-added"))

        val kept = runBlocking { repository().add(profileId, egg.ingredientId, 6.0, "piece", LocalDate.parse("2026-11-02")) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/inventory", sent.requestUrl!!.encodedPath)
        assertEquals(
            json.parseToJsonElement("""{"profileId":"$profileId","ingredientId":"${egg.ingredientId}","quantity":6.0,"unit":"piece","bestBefore":"2026-11-02"}"""),
            json.parseToJsonElement(sent.body.readUtf8()),
        )
        assertEquals(egg, kept)
    }

    @Test
    fun `something put in with no day says none, so that a row it adds to keeps its own`() {
        server.enqueue(answer(201, "pantry-row-added"))

        runBlocking { repository().add(profileId, egg.ingredientId, 2.0, "piece", null) }

        assertEquals(json.parseToJsonElement("""{"profileId":"$profileId","ingredientId":"${egg.ingredientId}","quantity":2.0,"unit":"piece"}"""), sentBody())
    }

    @Test
    fun `a change of a row sends what changed alone, a day taken away as null, and reads the row back as the API keeps it`() {
        repeat(3) { server.enqueue(answer(200, "pantry-row-changed")) }
        val pantry = repository()

        val kept = runBlocking { pantry.change(egg.id, PantryChange(quantity = 4.5, newDay = true, bestBefore = null)) }
        runBlocking { pantry.change(egg.id, PantryChange(quantity = 2.0)) }
        runBlocking { pantry.change(egg.id, PantryChange(newDay = true, bestBefore = LocalDate.parse("2026-10-30"))) }

        val first = server.takeRequest()
        assertEquals("PATCH", first.method)
        assertEquals("/api/inventory/${egg.id}", first.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"quantity":4.5,"bestBefore":null}"""), json.parseToJsonElement(first.body.readUtf8()))
        assertEquals(json.parseToJsonElement("""{"quantity":2.0}"""), sentBody())
        assertEquals(json.parseToJsonElement("""{"bestBefore":"2026-10-30"}"""), sentBody())
        assertEquals(egg.copy(shown = 4.5, bestBefore = null), kept)
    }

    @Test
    fun `more than a row may hold, and a unit the ingredient cannot be kept in, are refused with their codes`() {
        server.enqueue(answer(400, "pantry-quantity-too-large"))
        server.enqueue(answer(400, "pantry-unit-not-convertible"))
        val pantry = repository()

        val tooMuch = runCatching { runBlocking { pantry.change(garlic.id, PantryChange(quantity = 2000.0)) } }.exceptionOrNull() as ApiException
        val notThatUnit = runCatching { runBlocking { pantry.add(profileId, milk.ingredientId, 2.0, "piece", null) } }.exceptionOrNull() as ApiException

        assertEquals(400 to "QUANTITY_TOO_LARGE", tooMuch.statusCode to tooMuch.code)
        assertEquals(400 to "UNIT_NOT_CONVERTIBLE", notThatUnit.statusCode to notThatUnit.code)
    }

    @Test
    fun `a row is taken out by its id, and one that is not there is refused with its code`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "pantry-row-unknown"))
        val pantry = repository()

        runBlocking { pantry.remove(milk.id) }
        val refused = runCatching { runBlocking { pantry.remove(milk.id) } }.exceptionOrNull() as ApiException

        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/inventory/${milk.id}", sent.requestUrl!!.encodedPath)
        assertEquals(404 to "INVENTORY_ITEM_NOT_FOUND", refused.statusCode to refused.code)
    }

    @Test
    fun `the catalogue is searched by the words typed, each ingredient in the units the API keeps it in, the first of them offered first`() {
        server.enqueue(answer(200, "ingredients-search-egg"))
        server.enqueue(answer(200, "ingredients-search-milk"))
        val pantry = repository()

        val eggs = runBlocking { pantry.ingredients("large egg") }
        val milks = runBlocking { pantry.ingredients("whole milk") }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/ingredients", asked.encodedPath)
        assertEquals("large egg", asked.queryParameter("search"))
        assertEquals("whole milk", server.takeRequest().requestUrl!!.queryParameter("search"))
        // Counted: in pieces alone.
        assertEquals(listOf(StockableIngredient(egg.ingredientId, "Large egg", listOf("piece"), "piece", "piece")), eggs)
        // Measured in millilitres, and weighed where a millilitre has a weight: millilitres first.
        assertEquals(listOf(StockableIngredient(milk.ingredientId, "Whole milk", listOf("ml", "g"), "ml", "piece")), milks)
    }
}
