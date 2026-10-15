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
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.OverviewDay
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Share
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    /** A profile with no plan, unless a test says otherwise. */
    private val plans = mockk<MealPlanRepository>().also { coEvery { it.overview(any()) } returns null }

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

        val state = DashboardViewModel(users, plans).state.value

        assertNull(state.error)
        assertEquals(listOf(held, whole), state.profiles.map { it.profile })
        assertNull(state.profiles[0].target)
        assertSame(refusal, state.profiles[0].targetError)
        assertEquals(target, state.profiles[1].target)
        assertNull(state.profiles[1].targetError)
    }

    @Test
    fun `the dashboard read again on coming back shows a profile made in the meantime, and a failed reading leaves what was shown`() {
        val users = mockk<UserRepository>()
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val made = Profile("a", "Ala", "balanced", 4)
        coEvery { users.profiles() } returns emptyList()
        val model = DashboardViewModel(users, plans)
        assertEquals(emptyList<DashboardViewModel.ProfileStats>(), model.state.value.profiles)

        coEvery { users.profiles() } returns listOf(made)
        coEvery { users.calorieTarget(made.id) } returns target
        model.refresh()
        assertEquals(listOf(made), model.state.value.profiles.map { it.profile })

        coEvery { users.profiles() } throws IOException("unreachable")
        model.refresh()
        assertEquals(listOf(made), model.state.value.profiles.map { it.profile })
        assertNull(model.state.value.error)
    }

    @Test
    fun `the plan of the moment of each profile is read beside its target, and one that cannot be read is said on its own card`() {
        val users = mockk<UserRepository>()
        val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
        val (planned, unread) = listOf(Profile("a", "Ala", "balanced", 4), Profile("b", "Olek", "vegan", 3))
        val overview = PlanOverview(
            planId = "plan", startDate = "2026-10-13", durationDays = 7, dietType = "balanced", status = "active",
            energy = 1980.0, energyTarget = 2000.0, macros = Macros(100.0, 70.0, 220.0), macrosTarget = Macros(110.0, 65.0, 230.0),
            eaten = Share(4, 8), onDiet = Share(26, 28), favourites = Share(3, 28), ingredientReuse = 0.4,
            shoppingLists = 1, shoppingEntries = 40, shoppingTicked = 10,
            days = listOf(OverviewDay("2026-10-13", 1980.0, 2000.0, skipped = false)),
        )
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { users.profiles() } returns listOf(planned, unread)
        coEvery { users.calorieTarget(any()) } returns target
        coEvery { plans.overview(planned.id) } returns overview
        coEvery { plans.overview(unread.id) } throws refusal

        val state = DashboardViewModel(users, plans).state.value

        assertEquals(overview, state.profiles[0].overview)
        assertNull(state.profiles[0].overviewError)
        assertNull(state.profiles[1].overview)
        assertSame(refusal, state.profiles[1].overviewError)
        assertEquals(target, state.profiles[1].target)
    }
}
