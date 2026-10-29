// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.di.AppModule
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Asking a channel for the newest build, against a local server that answers as a channel does. */
class UpdatesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = AppModule.json()
    private lateinit var server: MockWebServer
    private var now = Instant.parse("2026-10-30T10:00:00Z")

    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private val store by lazy {
        UpdateStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "updates.preferences_pb") }), json)
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
        scope.cancel()
    }

    private fun updates(versionCode: Int = 448): Updates {
        val channel = UpdateChannel(server.url("/diet-app-android/version.json").toString(), server.url("/diet-app-android/").toString())
        return Updates({ channel }, store, json, OkHttpClient(), clock, versionCode)
    }

    /** A version file as the edge page script writes one. */
    private fun versionFile(code: Int) = MockResponse().setBody(
        """{"versionCode":$code,"versionName":"edge-1a2b3c$code","apiVersion":"0.139.0","apk":"diet-app-edge.apk","sha256":"${"ab".repeat(32)}","size":12345}""",
    )

    @Test
    fun `a newer build the channel names is offered, and the build that runs is not`() = runBlocking {
        server.enqueue(versionFile(449))
        val newer = updates()
        assertTrue(newer.ask())
        assertEquals(449, newer.available.first()?.versionCode)
        assertEquals("/diet-app-android/version.json", server.takeRequest().path)

        server.enqueue(versionFile(448))
        newer.ask()
        assertNull(newer.available.first())
        assertEquals(448, newer.latest.first()?.versionCode)
    }

    @Test
    fun `an answer that is no version file, or no answer, offers nothing and is kept as unanswered`() = runBlocking {
        val updates = updates()
        server.enqueue(MockResponse().setBody("<html>Not here</html>"))
        assertFalse(updates.ask())
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(updates.ask())

        assertNull(updates.available.first())
        assertEquals(Asked(now, answered = false), updates.asked.first())
    }

    @Test
    fun `the channel is asked at most every six hours, unless asked now`() = runBlocking {
        val updates = updates()
        server.enqueue(versionFile(449))
        updates.askIfDue()
        now = now.plus(Duration.ofHours(5))
        updates.askIfDue()
        assertEquals(1, server.requestCount)

        server.enqueue(versionFile(450))
        updates.ask()
        assertEquals(2, server.requestCount)

        now = now.plus(Duration.ofHours(6))
        server.enqueue(versionFile(450))
        updates.askIfDue()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `turned off, nothing is asked by itself and nothing offered`() = runBlocking {
        val updates = updates()
        server.enqueue(versionFile(449))
        updates.ask()
        updates.turn(false)

        updates.askIfDue()
        assertEquals(1, server.requestCount)
        assertNull(updates.available.first())
    }

    @Test
    fun `a build put off is not offered, and a newer one is`() = runBlocking {
        val updates = updates()
        server.enqueue(versionFile(449))
        updates.ask()
        updates.putOff(449)
        assertNull(updates.available.first())

        server.enqueue(versionFile(450))
        updates.ask()
        assertEquals(450, updates.available.first()?.versionCode)
    }
}
