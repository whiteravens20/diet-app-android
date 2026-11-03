// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.NewOwnProduct
import net.whiteravens.dietapp.domain.OwnProductState
import net.whiteravens.dietapp.domain.ProductSource
import net.whiteravens.dietapp.testing.clientHeaders
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The products of the account's own, as they go to the API and come back. */
class ProductRepositoryTest {
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

    private fun repository(): ProductRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return ProductRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun answer(status: Int, body: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val per100 = """{"calories":245,"protein":8,"fat":null,"carbs":45,"fiber":null,"sugars":null,"salt":null,"saturatedFat":null}"""

    private fun own(state: String = "own", reason: String? = null, code: String? = "5900512320359") =
        """{"id":"p","code":${code?.let { "\"$it\"" }},"source":"own","name":"Chleb","brand":null,"pack":null,"unit":"g","packAmount":null,""" +
            """"servingAmount":null,"per100":$per100,"energyWorkedOut":false,"doubtful":false,"changedAt":null,"state":"$state",""" +
            """"reason":${reason?.let { "\"$it\"" }},"sentAt":null,"updatedAt":"2026-11-02T10:00:00.000Z"}"""

    private val written = NewOwnProduct(
        code = null, name = "Chleb", brand = null, pack = null, unit = "g", packAmount = null, servingAmount = null,
        calories = 245.0, protein = 8.0, fat = null, saturatedFat = null, carbs = null, sugars = null, fiber = null, salt = null,
    )

    @Test
    fun `reads a list with what became of each, as the account's own`() {
        server.enqueue(answer(200, """{"items":[${own()},${own("sent")},${own("rejected", "Wrong.", code = null)}]}"""))

        val items = runBlocking { repository().mine() }

        assertEquals(listOf(OwnProductState.OWN, OwnProductState.SENT, OwnProductState.REJECTED), items.map { it.state })
        assertEquals(ProductSource.OWN, items[0].product.source)
        assertEquals(245.0, items[0].product.calories)
        assertEquals("Wrong.", items[2].reason)
        assertNull(items[2].product.code)
        assertEquals("/api/products/own", server.takeRequest().path)
    }

    @Test
    fun `writes only what is known, and what is left out the API reads as not known`() {
        server.enqueue(answer(201, own()))

        runBlocking { repository().write(written) }

        val asked = server.takeRequest()
        assertEquals("POST", asked.method)
        assertEquals("/api/products/own", asked.path)
        assertEquals(
            json.parseToJsonElement("""{"name":"Chleb","unit":"g","per100":{"calories":245.0,"protein":8.0}}"""),
            json.parseToJsonElement(asked.body.readUtf8()),
        )
    }

    @Test
    fun `changes, sends, takes back and removes the product it is told to`() {
        repeat(3) { server.enqueue(answer(200, own())) }
        server.enqueue(MockResponse().setResponseCode(204))
        val products = repository()

        runBlocking {
            products.change("p", written)
            products.send("p")
            products.withdraw("p")
            products.remove("p")
        }

        val asked = List(4) { server.takeRequest().let { "${it.method} ${it.path}" } }
        assertEquals(
            listOf(
                "PATCH /api/products/own/p",
                "POST /api/products/own/p/send",
                "POST /api/products/own/p/withdraw",
                "DELETE /api/products/own/p",
            ),
            asked,
        )
    }

    @Test
    fun `refuses in the API's words a code the base knows`() {
        server.enqueue(answer(409, """{"statusCode":409,"error":"PRODUCT_CODE_KNOWN","message":"Known."}"""))

        try {
            runBlocking { repository().write(written) }
            fail("A refusal goes on as a refusal.")
        } catch (refused: ApiException) {
            assertEquals("PRODUCT_CODE_KNOWN", refused.code)
        }
    }

    @Test
    fun `reads the product of the base with where it comes from`() {
        server.enqueue(
            answer(
                200,
                """{"id":"b","code":"5900512320359","source":"instance","name":"Mleko","unit":"ml","per100":$per100,"energyWorkedOut":false,"doubtful":false}""",
            ),
        )

        val product = runBlocking { repository().inTheBase("5900512320359") }

        assertEquals(ProductSource.INSTANCE, product.source)
        assertEquals("/api/products/code/5900512320359", server.takeRequest().path)
    }

    @Test
    fun `keeps the file as the API gives it`() {
        val file = """{"license":"ODbL-1.0","products":[]}"""
        server.enqueue(answer(200, file))

        val text = runBlocking { repository().additions() }

        assertEquals(json.parseToJsonElement(file), json.parseToJsonElement(text))
        assertTrue(server.takeRequest().path!!.endsWith("/products/additions"))
    }
}
