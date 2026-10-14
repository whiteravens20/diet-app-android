// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `a calorie target that cannot be had leaves its profile shown with the reason, and the others whole`() {
        val users = mockk<UserRepository>()
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val (held, whole) = listOf(Profile("a", "Ala", "balanced", 4), Profile("b", "Olek", "vegan", 3))
        coEvery { users.profiles() } returns listOf(held, whole)
        coEvery { users.calorieTarget(held.id) } throws refusal
        coEvery { users.calorieTarget(whole.id) } returns target

        val state = DashboardViewModel(users).state.value

        assertNull(state.error)
        assertEquals(listOf(held, whole), state.profiles.map { it.profile })
        assertNull(state.profiles[0].target)
        assertSame(refusal, state.profiles[0].targetError)
        assertEquals(target, state.profiles[1].target)
        assertNull(state.profiles[1].targetError)
    }
}
