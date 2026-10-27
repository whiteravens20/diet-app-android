// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.remote.NotificationDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.NotificationAbout
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The account's notifications read, one marked read and all at once, against a local server answering as the backend did. */
class NotificationRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val ala = "84b48a01-708a-4c00-993a-c95cfc9cfd3c"
    private val ola = "98d9b694-a3f5-4da8-9bce-95be25cb1b32"

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): NotificationRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return NotificationRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `the notifications not read yet are read the newest first, each about what its type says, a reminder with no weight logged counting no days`() {
        server.enqueue(answer(200, "notifications-unread"))

        val read = runBlocking { repository().unread() }

        val asked = server.takeRequest()
        assertEquals("GET", asked.method)
        assertEquals("/api/notifications/unread", asked.requestUrl!!.encodedPath)
        assertEquals(
            listOf(
                AccountNotification("e287e6ab-8278-4d07-8894-9770a91fe2fe", ala, NotificationAbout.PreferencesReview, Instant.parse("2026-10-27T12:16:25.940Z")),
                AccountNotification("ab638a74-941c-4356-bb45-1f3912abec62", ala, NotificationAbout.WeightReminder(null), Instant.parse("2026-10-27T12:16:22.981Z")),
                AccountNotification("0a5d6aa6-4b07-4bf7-b7c3-1e63a43ea781", ola, NotificationAbout.WeightReminder(3), Instant.parse("2026-10-27T12:16:22.981Z")),
            ),
            read,
        )
    }

    @Test
    fun `none not read yet is an empty list`() {
        server.enqueue(answer(200, "notifications-none"))

        assertEquals(emptyList<AccountNotification>(), runBlocking { repository().unread() })
    }

    @Test
    fun `a notification is marked read by its id, and one the account does not have is refused with its code`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "notification-unknown"))
        val notifications = repository()

        runBlocking { notifications.read("e287e6ab-8278-4d07-8894-9770a91fe2fe") }
        val refused = runCatching { runBlocking { notifications.read("1b7b7bb0-3c41-4e0b-9bd7-0e1f3c1b4e55") } }.exceptionOrNull() as ApiException

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/notifications/e287e6ab-8278-4d07-8894-9770a91fe2fe/read", sent.requestUrl!!.encodedPath)
        assertEquals(404, refused.statusCode)
        assertEquals("NOTIFICATION_NOT_FOUND", refused.code)
    }

    @Test
    fun `all are marked read at once, and the API says how many there were`() {
        server.enqueue(answer(200, "notifications-read-all"))

        val marked = runBlocking { repository().readAll() }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/notifications/read-all", sent.requestUrl!!.encodedPath)
        assertEquals(2, marked)
    }

    @Test
    fun `a kind this version does not know is kept under the name the API gives it`() {
        val newer = NotificationDto("n", ala, type = "plan_ready", createdAt = "2026-10-27T12:16:25.940Z")

        assertEquals(NotificationAbout.Other("plan_ready"), newer.toDomain().about)
    }
}
