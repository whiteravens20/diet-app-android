// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

        assertEquals(unread, fresh.read())
        assertEquals(emptyList<Any>(), fresh.read())
    }

    @Test
    fun `a notification made after the last read is the only one the next read gives`() = runBlocking {
        coEvery { notifications.unread() } returns unread.drop(1)
        val fresh = NewNotifications(notifications, store)
        fresh.read()

        coEvery { notifications.unread() } returns unread

        assertEquals(listOf(unread.first()), fresh.read())
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
        assertEquals(emptyList<Any>(), fresh.read())
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
