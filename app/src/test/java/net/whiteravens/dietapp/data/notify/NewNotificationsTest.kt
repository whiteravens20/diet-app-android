// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.remote.NotificationDto
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone shows each of the account's unread notifications once, known by its id and the time it
 * was made, whatever runs of the background work read it. The answers are what a running backend
 * answered.
 */
class NewNotificationsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun stop() = scope.cancel()

    private val json = AppModule.json()

    /** Ala's review of preferences and her reminder, and Ola's reminder. */
    private val unread = json.decodeFromString<List<NotificationDto>>(fixture("notifications-unread")).map { it.toDomain() }

    private val data by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "phone_notifications.preferences_pb") })
    }
    private val store by lazy { PhoneNotificationsStore(data) }
    private val notifications = mockk<NotificationRepository>()

    @Test
    fun `the first read gives every unread notification, and the next one none of them again`() = runBlocking {
        coEvery { notifications.unread() } returns unread
        val fresh = NewNotifications(notifications, store)

        assertEquals(unread, fresh.read().fresh)
        assertEquals(emptyList<Any>(), fresh.read().fresh)
    }

    @Test
    fun `a notification made after the last read is the only one the next read gives`() = runBlocking {
        coEvery { notifications.unread() } returns unread.drop(1)
        val fresh = NewNotifications(notifications, store)
        fresh.read()

        coEvery { notifications.unread() } returns unread

        assertEquals(listOf(unread.first()), fresh.read().fresh)
    }

    @Test
    fun `what the phone keeps is the unread notifications alone, so that one read elsewhere is forgotten`() = runBlocking {
        coEvery { notifications.unread() } returns unread
        val fresh = NewNotifications(notifications, store)
        fresh.read()

        coEvery { notifications.unread() } returns unread.drop(1)
        fresh.read()

        assertEquals(unread.drop(1).map(NewNotifications::keyOf).toSet(), store.shown())
    }

    @Test
    fun `a read that fails keeps what was shown, so that nothing is shown twice when the network is back`() = runBlocking {
        coEvery { notifications.unread() } returns unread
        val fresh = NewNotifications(notifications, store)
        fresh.read()

        coEvery { notifications.unread() } throws IOException("no network")
        assertTrue(runCatching { fresh.read() }.isFailure)

        coEvery { notifications.unread() } returns unread
        assertEquals(emptyList<Any>(), fresh.read().fresh)
    }

    @Test
    fun `two runs of the work at the same moment give each notification once between them`() = runBlocking {
        coEvery { notifications.unread() } coAnswers {
            delay(200)
            unread
        }
        val fresh = NewNotifications(notifications, store)

        val given = listOf(async(Dispatchers.IO) { fresh.read() }, async(Dispatchers.IO) { fresh.read() }).awaitAll().flatMap { it.fresh }

        assertEquals(unread.sortedBy { it.id }, given.sortedBy { it.id })
    }

    @Test
    fun `an older answer that comes last does not take back what a newer one kept`() = runBlocking {
        val answered = AtomicInteger()
        coEvery { notifications.unread() } coAnswers {
            // The first run's answer, from before the newest notification was made, comes after the second run's.
            if (answered.getAndIncrement() == 0) {
                delay(300)
                unread.drop(1)
            } else {
                unread
            }
        }
        val fresh = NewNotifications(notifications, store)
        listOf(
            async(Dispatchers.IO) { fresh.read() },
            async(Dispatchers.IO) {
                delay(50)
                fresh.read()
            },
        ).awaitAll()

        coEvery { notifications.unread() } returns unread
        assertEquals(emptyList<Any>(), fresh.read().fresh)
    }

    @Test
    fun `what the phone showed and is read since, anywhere, is said to be taken down, once`() = runBlocking {
        coEvery { notifications.unread() } returns unread
        val fresh = NewNotifications(notifications, store)
        assertEquals(emptySet<String>(), fresh.read().gone)

        // The first read elsewhere: it goes, the others stay; read again, nothing more goes.
        coEvery { notifications.unread() } returns unread.drop(1)
        assertEquals(setOf(NewNotifications.keyOf(unread.first())), fresh.read().gone)
        assertEquals(emptySet<String>(), fresh.read().gone)
    }

    @Test
    fun `of a list as long as the API gives, one shown that is older than its last may be unread still, and stays`() = runBlocking {
        val base = unread.first()
        fun made(at: Int) = base.copy(id = "n-$at", createdAt = base.createdAt.plusSeconds(at.toLong()))
        // The newest fifty, of fifty-one unread: the oldest was shown before newer ones came.
        val oldest = made(0)
        coEvery { notifications.unread() } returns listOf(oldest)
        val fresh = NewNotifications(notifications, store)
        fresh.read()
        val newest = (1..NewNotifications.MOST_UNREAD_LISTED).map(::made).reversed()
        coEvery { notifications.unread() } returns newest

        assertEquals(emptySet<String>(), fresh.read().gone)
        // Kept as shown: once it is among the listed again, it is not shown twice.
        coEvery { notifications.unread() } returns newest.drop(1) + oldest
        assertEquals(emptyList<Any>(), fresh.read().fresh.filter { it.id == oldest.id })
    }

    @Test
    fun `the notifications are off until they are turned on, and forgotten whole at signing out`() = runBlocking {
        assertFalse(store.isOn())

        store.turn(true)
        store.keepShown(setOf("a@1"))
        assertTrue(store.isOn())

        store.clear()
        assertFalse(store.isOn())
        assertEquals(emptySet<String>(), store.shown())
    }
}
