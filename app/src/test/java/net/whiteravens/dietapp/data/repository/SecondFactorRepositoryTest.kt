// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.SecondFactorSetup
import net.whiteravens.dietapp.domain.SecondFactorState
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Two-step sign-in set up, switched on, given new recovery codes and switched off, against a local server answering as the backend did. */
class SecondFactorRepositoryTest {
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

    private fun repository(): SecondFactorRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return SecondFactorRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    private fun body(sent: okhttp3.mockwebserver.RecordedRequest) = json.parseToJsonElement(sent.body.readUtf8())

    @Test
    fun `whether it is on is read, off and then on since a moment with its recovery codes counted`() {
        server.enqueue(answer(200, "second-factor-off"))
        server.enqueue(answer(200, "second-factor-state-on"))
        val secondFactor = repository()

        assertEquals(SecondFactorState(enabled = false, since = null, recoveryCodesLeft = 0), runBlocking { secondFactor.state() })
        assertEquals(
            SecondFactorState(enabled = true, since = Instant.parse("2026-10-17T08:42:56.963Z"), recoveryCodesLeft = 10),
            runBlocking { secondFactor.state() },
        )
        assertEquals("/api/users/me/second-factor", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `a secret is handed out for the password, and its first code, typed with spaces, switches it on and answers the recovery codes`() {
        server.enqueue(answer(200, "second-factor-setup"))
        server.enqueue(answer(401, "second-factor-code-wrong"))
        server.enqueue(answer(201, "second-factor-on"))
        val secondFactor = repository()

        val setup = runBlocking { secondFactor.propose("Str0ng-Passw0rd-1!") }
        val wrong = runCatching { runBlocking { secondFactor.begin(setup.enrolment, "123 456") } }.exceptionOrNull() as ApiException
        val codes = runBlocking { secondFactor.begin(setup.enrolment, " 654 321 ") }

        assertEquals(
            SecondFactorSetup(
                secret = "LBBALDN6MTVWWNMCVDDHRHV7UMMLPLR5",
                uri = "otpauth://totp/Diet%20App%20(192.168.2.129):android-account-a721061a%40live.test?secret=LBBALDN6MTVWWNMCVDDHRHV7UMMLPLR5" +
                    "&issuer=Diet%20App%20%28192.168.2.129%29&algorithm=SHA1&digits=6&period=30",
                enrolment = "<removed>",
            ),
            setup,
        )
        val proposed = server.takeRequest()
        assertEquals("/api/users/me/second-factor/setup", proposed.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"currentPassword":"Str0ng-Passw0rd-1!"}"""), body(proposed))
        assertEquals(json.parseToJsonElement("""{"enrolment":"<removed>","code":"123456"}"""), body(server.takeRequest()))
        val begun = server.takeRequest()
        assertEquals("POST", begun.method)
        assertEquals("/api/users/me/second-factor", begun.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"enrolment":"<removed>","code":"654321"}"""), body(begun))
        assertEquals("INVALID_SECOND_FACTOR_CODE", wrong.code)
        assertEquals(401, wrong.statusCode)
        assertEquals(
            listOf("PZ2KE-VY1ZF", "DKKDP-6ZEC4", "HJ1DA-2VK96", "FZ3A0-123AZ", "7BPCQ-ZQQDA", "79QV8-DCRMM", "Z94Y8-8S32Y", "HNNXE-2T05M", "SKPDT-EXYSQ", "4H4VZ-3ZMA6"),
            codes,
        )
    }

    @Test
    fun `new recovery codes and switching it off each take the password and a code`() {
        server.enqueue(answer(200, "second-factor-new-codes"))
        server.enqueue(MockResponse().setResponseCode(204))
        val secondFactor = repository()

        val fresh = runBlocking { secondFactor.newRecoveryCodes("Str0ng-Passw0rd-1!", "PZ2KE-VY1ZF") }
        runBlocking { secondFactor.end("Str0ng-Passw0rd-1!", "HVF7N-PXWYT") }

        val renewed = server.takeRequest()
        assertEquals("/api/users/me/second-factor/recovery-codes", renewed.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"currentPassword":"Str0ng-Passw0rd-1!","code":"PZ2KE-VY1ZF"}"""), body(renewed))
        val ended = server.takeRequest()
        assertEquals("DELETE", ended.method)
        assertEquals("/api/users/me/second-factor", ended.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"currentPassword":"Str0ng-Passw0rd-1!","code":"HVF7N-PXWYT"}"""), body(ended))
        assertEquals("HVF7N-PXWYT", fresh.first())
        assertEquals(10, fresh.size)
    }
}
