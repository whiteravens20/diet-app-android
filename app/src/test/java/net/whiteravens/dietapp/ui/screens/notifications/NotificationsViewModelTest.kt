// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.notifications

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.NotificationDto
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val json = AppModule.json()

    /** As the API answered them: Ala's review of preferences and her reminder, and Ola's reminder. */
    private val unread = json.decodeFromString<List<NotificationDto>>(fixture("notifications-unread")).map { it.toDomain() }
    private val review = unread[0]
    private val alasReminder = unread[1]
    private val olasReminder = unread[2]

    private val notifications = mockk<NotificationRepository>().also { coEvery { it.unread() } returns unread }
    private val users = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(
            Profile(review.profileId, "Ala", "balanced", 4, 64.0, null),
            Profile(olasReminder.profileId, "Ola", "balanced", 4, 64.0, null),
        )
    }

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the notifications are read with the names of their profiles, and names that cannot be read leave them without one`() {
        val state = NotificationsViewModel(notifications, users).state.value

        assertFalse(state.loading)
        assertEquals(unread, state.items)
        assertEquals(mapOf(review.profileId to "Ala", olasReminder.profileId to "Ola"), state.names)

        coEvery { users.profiles() } throws IOException("unreachable")
        val nameless = NotificationsViewModel(notifications, users).state.value

        assertEquals(unread, nameless.items)
        assertEquals(emptyMap<String, String>(), nameless.names)
        assertNull(nameless.error)
    }

    @Test
    fun `a list that cannot be read says why, and is asked for again`() {
        val unreachable = IOException("unreachable")
        coEvery { notifications.unread() } throws unreachable
        val model = NotificationsViewModel(notifications, users)

        assertSame(unreachable, model.state.value.error)
        assertEquals(emptyList<AccountNotification>(), model.state.value.items)

        coEvery { notifications.unread() } returns unread
        model.load()

        assertNull(model.state.value.error)
        assertEquals(unread, model.state.value.items)
    }

    @Test
    fun `one opened is marked read before the reader is taken where it points, and leaves the list`() {
        var went = false
        coEvery { notifications.read(olasReminder.id) } answers { assertFalse("taken there before it was read", went) }
        val model = NotificationsViewModel(notifications, users)

        model.open(olasReminder) { went = true }

        coVerify(exactly = 1) { notifications.read(olasReminder.id) }
        assertTrue(went)
        assertEquals(listOf(review, alasReminder), model.state.value.items)
        assertNull(model.state.value.marking)
    }

    @Test
    fun `one that cannot be marked read stays, says why and takes the reader nowhere`() {
        val unreachable = IOException("unreachable")
        coEvery { notifications.read(review.id) } throws unreachable
        var went = false
        val model = NotificationsViewModel(notifications, users)

        model.open(review) { went = true }

        assertFalse(went)
        assertSame(unreachable, model.state.value.error)
        assertEquals(unread, model.state.value.items)
        assertNull(model.state.value.marking)
    }

    @Test
    fun `one the API no longer has for the account is gone from the list, and still leads where it pointed`() {
        coEvery { notifications.read(review.id) } throws ApiException(404, "NOTIFICATION_NOT_FOUND", "Notification not found.")
        var went = false
        val model = NotificationsViewModel(notifications, users)

        model.open(review) { went = true }

        assertTrue(went)
        assertNull(model.state.value.error)
        assertEquals(listOf(alasReminder, olasReminder), model.state.value.items)
    }

    @Test
    fun `one put away is marked read where it stands, and all are at once`() {
        coEvery { notifications.read(alasReminder.id) } just runs
        coEvery { notifications.readAll() } returns 2
        val model = NotificationsViewModel(notifications, users)

        model.dismiss(alasReminder)

        coVerify(exactly = 1) { notifications.read(alasReminder.id) }
        assertEquals(listOf(review, olasReminder), model.state.value.items)

        model.readAll()

        coVerify(exactly = 1) { notifications.readAll() }
        assertEquals(emptyList<AccountNotification>(), model.state.value.items)
    }

    @Test
    fun `nothing else is marked while one is on its way`() {
        val answered = CompletableDeferred<Unit>()
        coEvery { notifications.read(review.id) } coAnswers { answered.await() }
        val model = NotificationsViewModel(notifications, users)

        model.dismiss(review)
        assertEquals(review.id, model.state.value.marking)
        model.dismiss(olasReminder)
        model.readAll()
        answered.complete(Unit)

        coVerify(exactly = 0) { notifications.read(olasReminder.id) }
        coVerify(exactly = 0) { notifications.readAll() }
        assertEquals(listOf(alasReminder, olasReminder), model.state.value.items)
        assertNull(model.state.value.marking)
    }

    @Test
    fun `pulled down, the list is read again, and one put away meanwhile stays away from the older answer`() {
        val model = NotificationsViewModel(notifications, users)
        val answer = CompletableDeferred<Unit>()
        coEvery { notifications.unread() } coAnswers {
            answer.await()
            unread
        }
        coEvery { notifications.read(review.id) } just runs

        model.pull()
        assertTrue(model.state.value.pulled)
        model.dismiss(review)
        answer.complete(Unit)

        assertEquals(listOf(alasReminder, olasReminder), model.state.value.items)
        assertFalse(model.state.value.pulled)
    }

    @Test
    fun `shown again after another screen the list is read again, and a list that cannot be read keeps what was shown`() {
        val model = NotificationsViewModel(notifications, users)
        coEvery { notifications.unread() } returns listOf(olasReminder)

        model.shownAgain(cameToTheFront = false)
        assertEquals(listOf(olasReminder), model.state.value.items)

        val readAt = model.state.value.readAt
        assertNotNull(readAt)
        coEvery { notifications.unread() } throws IOException("unreachable")
        model.shownAgain(cameToTheFront = false)
        assertEquals(listOf(olasReminder), model.state.value.items)
        assertNull(model.state.value.error)
        assertTrue(model.state.value.offline)
        assertEquals(readAt, model.state.value.readAt)
    }
}
