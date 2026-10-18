// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.domain.ConnectionTest
import net.whiteravens.dietapp.domain.ProviderSetup
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The account's own AI providers listed, saved, removed and tested, against a local server answering as the backend did. */
class AiRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val ollama = AiProviderConfig(
        "73a9027d-1cfc-4a2a-8eda-36b5a8720e2d", "ollama", "mock-queue", 0, enabled = true,
        hasKey = false, keyUnreadable = false, baseUrl = "http://mockai:11434",
    )
    private val openai = AiProviderConfig(
        "9aeea55b-07b7-4823-a0c8-f9123d718571", "openai", "gpt-4.1-mini", 2, enabled = true,
        hasKey = true, keyUnreadable = false, baseUrl = null,
    )

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): AiRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
        return AiRepository(AppModule.api(client, json), AppModule.modelApi(client, json), json)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    private fun sent(request: RecordedRequest) = json.parseToJsonElement(request.body.readUtf8())

    @Test
    fun `the providers are read as the API lists them, the one asked first first, and a key that can no longer be read is said so`() {
        server.enqueue(answer(200, "ai-providers"))
        server.enqueue(answer(200, "ai-providers-key-unreadable"))
        server.enqueue(answer(200, "ai-providers-none"))
        val ai = repository()

        val listed = runBlocking { ai.providers() }
        val unreadable = runBlocking { ai.providers() }
        val none = runBlocking { ai.providers() }

        assertEquals("/api/ai/providers", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(listOf(ollama, openai), listed)
        assertEquals(listOf(ollama, openai.copy(keyUnreadable = true)), unreadable)
        assertEquals(emptyList<AiProviderConfig>(), none)
    }

    @Test
    fun `a provider is saved with its key, an Ollama server with its address and no key, and the one the account had of a kind is replaced`() {
        server.enqueue(answer(200, "ai-provider-saved"))
        server.enqueue(answer(200, "ai-provider-ollama-saved"))
        server.enqueue(answer(200, "ai-provider-replaced"))
        val ai = repository()

        val saved = runBlocking { ai.save(ProviderSetup("openai", "gpt-4o-mini", "sk-typed-on-the-phone", null, 1)) }
        val local = runBlocking { ai.save(ProviderSetup("ollama", "mock-queue", null, "http://mockai:11434", 0)) }
        val replaced = runBlocking { ai.save(ProviderSetup("openai", "gpt-4.1-mini", "sk-typed-again", null, 2)) }

        val first = server.takeRequest()
        assertEquals("PUT", first.method)
        assertEquals("/api/ai/providers", first.requestUrl!!.encodedPath)
        assertEquals(
            json.parseToJsonElement("""{"provider":"openai","model":"gpt-4o-mini","apiKey":"sk-typed-on-the-phone","priority":1,"enabled":true}"""),
            sent(first),
        )
        assertEquals(
            json.parseToJsonElement("""{"provider":"ollama","model":"mock-queue","baseUrl":"http://mockai:11434","priority":0,"enabled":true}"""),
            sent(server.takeRequest()),
        )
        assertEquals(openai.copy(model = "gpt-4o-mini", priority = 1), saved)
        assertEquals(ollama, local)
        // The account has one configuration of each provider: saving another changes that one.
        assertEquals(saved.id, replaced.id)
        assertEquals(openai, replaced)
    }

    @Test
    fun `an Ollama server the instance does not allow is refused with its code`() {
        server.enqueue(answer(400, "ai-provider-host-not-allowed"))

        val refused = runCatching { runBlocking { repository().save(ProviderSetup("ollama", "llama3.1:8b", null, "http://192.0.2.10:11434", 0)) } }
            .exceptionOrNull() as ApiException

        assertEquals("OLLAMA_HOST_NOT_ALLOWED", refused.code)
    }

    @Test
    fun `a provider is removed by its id, and one gone already is refused with its code`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "ai-provider-unknown"))
        val ai = repository()

        runBlocking { ai.remove(openai.id) }
        val gone = runCatching { runBlocking { ai.remove(ollama.id) } }.exceptionOrNull() as ApiException

        val removal = server.takeRequest()
        assertEquals("DELETE", removal.method)
        assertEquals("/api/ai/providers/${openai.id}", removal.requestUrl!!.encodedPath)
        assertEquals("AI_CONFIG_NOT_FOUND", gone.code)
    }

    @Test
    fun `a connection is tested with the key or the address alone and the model where one is typed, and the provider's answer is read as it came`() {
        server.enqueue(answer(201, "ai-test-connected"))
        server.enqueue(answer(201, "ai-test-host-not-allowed"))
        server.enqueue(answer(201, "ai-test-anthropic-without-a-model"))
        val ai = repository()

        val connected = runBlocking { ai.test(ProviderSetup("ollama", "mock-queue", null, "http://mockai:11434", 0)) }
        val notAllowed = runBlocking { ai.test(ProviderSetup("ollama", "", null, "http://192.0.2.10:11434", 0)) }
        val noModel = runBlocking { ai.test(ProviderSetup("anthropic", "", "sk-ant-typed-on-the-phone", null, 0)) }

        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/api/ai/test", first.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"provider":"ollama","baseUrl":"http://mockai:11434","model":"mock-queue"}"""), sent(first))
        assertEquals(json.parseToJsonElement("""{"provider":"ollama","baseUrl":"http://192.0.2.10:11434"}"""), sent(server.takeRequest()))
        assertEquals(json.parseToJsonElement("""{"provider":"anthropic","apiKey":"sk-ant-typed-on-the-phone"}"""), sent(server.takeRequest()))
        assertEquals(
            ConnectionTest(true, listOf("mock-401", "mock-500", "mock-empty", "mock-garbage", "mock-html", "mock-huge", "mock-queue", "mock-slow"), null),
            connected,
        )
        assertEquals(
            ConnectionTest(false, emptyList(), "Ollama host \"192.0.2.10:11434\" is not allowed. It must match OLLAMA_BASE_URL or an entry in OLLAMA_ALLOWED_HOSTS."),
            notAllowed,
        )
        assertEquals(ConnectionTest(false, emptyList(), "Model id required to probe Anthropic."), noModel)
    }
}
