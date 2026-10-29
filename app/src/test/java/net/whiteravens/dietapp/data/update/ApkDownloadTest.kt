// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** A build downloaded and held to its version file: a file that is not the one described never stays. */
class ApkDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val bytes = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val into by lazy { File(folder.root, "update.apk") }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun fetch(size: Long = bytes.size.toLong(), sha: String = sha256): UpdateFailure? = runCatching {
        ApkDownload(OkHttpClient()).fetch(server.url("/diet-app-edge.apk").toString(), size, sha, into) {}
    }.exceptionOrNull()?.let { (it as UpdateException).why }

    @Test
    fun `the file the version file describes is kept whole`() {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val progress = mutableListOf<Long>()

        ApkDownload(OkHttpClient()).fetch(server.url("/diet-app-edge.apk").toString(), bytes.size.toLong(), sha256, into) { progress += it }

        assertArrayEquals(bytes, into.readBytes())
        assertEquals(bytes.size.toLong(), progress.last())
    }

    @Test
    fun `another file of the same size is deleted`() {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        assertEquals(UpdateFailure.CHECKSUM, fetch(sha = "00".repeat(32)))
        assertFalse(into.exists())
    }

    @Test
    fun `a file longer than described is deleted before it is all read`() {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        assertEquals(UpdateFailure.CHECKSUM, fetch(size = 1_000))
        assertFalse(into.exists())
    }

    @Test
    fun `a download cut short, or refused, leaves no file`() {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertEquals(UpdateFailure.DOWNLOAD, fetch())
        assertFalse(into.exists())

        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(UpdateFailure.DOWNLOAD, fetch())
        assertFalse(into.exists())
    }
}
