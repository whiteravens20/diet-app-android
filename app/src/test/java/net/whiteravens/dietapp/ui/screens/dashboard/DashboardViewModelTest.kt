// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.MarkedRecipe
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

    /** A profile with no favourite set and nothing marked, unless a test says otherwise. */
    private val favorites = mockk<FavoriteRepository>().also {
        coEvery { it.sets(any()) } returns emptyList()
        coEvery { it.marked(any(), any()) } returns emptyList()
    }

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

        val state = DashboardViewModel(users, plans, favorites).state.value

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
        val model = DashboardViewModel(users, plans, favorites)
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

        val state = DashboardViewModel(users, plans, favorites).state.value

        assertEquals(overview, state.profiles[0].overview)
        assertNull(state.profiles[0].overviewError)
        assertNull(state.profiles[1].overview)
        assertSame(refusal, state.profiles[1].overviewError)
        assertEquals(target, state.profiles[1].target)
    }

    private val ala = Profile("a", "Ala", "balanced", 4)
    private val oats = MarkedRecipe("oats", "Oats", listOf("breakfast"), "favorite", emptyList())
    private val weekday = FavoriteSet("weekday", "Weekday", mapOf("breakfast" to "oats"))

    private fun users() = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(ala)
        coEvery { it.calorieTarget(any()) } returns json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
    }

    @Test
    fun `a profile's favourite sets and marks are read with it, and ones that cannot be read are said on its card`() {
        coEvery { favorites.sets(ala.id) } returns listOf(weekday)
        coEvery { favorites.marked(ala.id, any()) } returns listOf(oats)

        val read = DashboardViewModel(users(), plans, favorites).state.value.profiles.single()

        assertEquals(listOf(weekday), read.sets)
        assertEquals(listOf(oats), read.marks)
        assertNull(read.setsError)

        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { favorites.marked(ala.id, any()) } throws refusal
        val unread = DashboardViewModel(users(), plans, favorites).state.value.profiles.single()

        assertEquals(listOf(weekday), unread.sets)
        assertNull(unread.marks)
        assertSame(refusal, unread.setsError)
    }

    @Test
    fun `a set made is first among its profile's, a set changed stands where it was, and one refused keeps its window open with the reason`() {
        val older = FavoriteSet("older", "Older", mapOf("dinner" to "soup"))
        coEvery { favorites.sets(ala.id) } returns listOf(weekday, older)
        val made = FavoriteSet("new", "Training day", mapOf("breakfast" to "oats"))
        coEvery { favorites.createSet(ala.id, "Training day", mapOf("breakfast" to "oats")) } returns made
        val changed = weekday.copy(label = "Workday")
        coEvery { favorites.updateSet("weekday", "Workday", weekday.slots) } returns changed
        val model = DashboardViewModel(users(), plans, favorites)

        model.newSet(ala.id)
        model.saveSet("Training day", mapOf("breakfast" to "oats"))
        model.editSet(ala.id, weekday)
        model.saveSet("Workday", weekday.slots)

        assertNull(model.state.value.editing)
        assertEquals(listOf(made, changed, older), model.state.value.profiles.single().sets)

        val refusal = ApiException(400, "FAVORITE_SET_NOT_ELIGIBLE", "Recipe oats is not a recipe for dinner.")
        coEvery { favorites.updateSet("older", any(), any()) } throws refusal
        model.editSet(ala.id, older)
        model.saveSet("Older", mapOf("dinner" to "oats"))

        assertEquals(DashboardViewModel.SetEdit(ala.id, older, saving = false, error = refusal), model.state.value.editing)
        model.closeSet()
        assertNull(model.state.value.editing)
    }

    @Test
    fun `a set deleted leaves its card, and one refused is said on its profile's card`() {
        coEvery { favorites.sets(ala.id) } returns listOf(weekday)
        coEvery { favorites.deleteSet("weekday") } just runs
        val model = DashboardViewModel(users(), plans, favorites)

        model.deleteSet(ala.id, "weekday")

        coVerify { favorites.deleteSet("weekday") }
        assertEquals(emptyList<FavoriteSet>(), model.state.value.profiles.single().sets)

        val gone = ApiException(404, "SET_NOT_FOUND", "Favorite set not found.")
        coEvery { favorites.deleteSet("weekday") } throws gone
        model.deleteSet(ala.id, "weekday")

        assertEquals(DashboardViewModel.SetDeleteFailed(ala.id, gone), model.state.value.setDeleteFailed)
        assertNull(model.state.value.deletingSet)
    }
}
