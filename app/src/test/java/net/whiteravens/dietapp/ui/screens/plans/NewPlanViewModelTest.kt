// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.remote.PublicConfigDto
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.domain.RelaxedMeal
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class NewPlanViewModelTest {
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private val profile = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
    private val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
    private val made = json.decodeFromString<MealPlanDto>(fixture("meal-plan")).toDomain()
    private val asked = mutableListOf<PlanAsk>()
    private val plans = mockk<MealPlanRepository>()
    private val profiles = mockk<ProfileRepository>().also { coEvery { it.profile(profile.id) } returns profile }
    private val users = mockk<UserRepository>().also { coEvery { it.calorieTarget(profile.id) } returns target }
    private val favorites = mockk<FavoriteRepository>().also {
        coEvery { it.marked(profile.id) } returns listOf(
            MarkedRecipe("oats", "Oats", listOf("breakfast"), "favorite", emptyList()),
            MarkedRecipe("liver", "Liver", listOf("lunch"), "avoid", emptyList()),
        )
        coEvery { it.sets(profile.id) } returns listOf(FavoriteSet("s", "Weekday", mapOf("breakfast" to "oats")))
    }
    /** The instance as a running backend describes it (`config.json`): the meals of a day and the order limits give way in. */
    private val server = mockk<ServerRepository>().also {
        val plans = AppModule.json().decodeFromString<PublicConfigDto>(fixture("config")).plans
        val rules = InstanceRules(
            maxProfiles = 2,
            catalogueSupply = emptyMap(),
            mealSlots = plans.mealSlots.mapKeys { (count, _) -> count.toInt() },
            relaxations = plans.relaxations,
        )
        every { it.rules } returns MutableStateFlow<InstanceRules?>(rules)
        coEvery { it.refresh() } just runs
    }

    private fun model() = NewPlanViewModel(SavedStateHandle(mapOf(NewPlanViewModel.PROFILE_ID to profile.id)), profiles, users, favorites, plans, server)

    @Test
    fun `the form begins with the profile, its target and the favourites it can lock, those to avoid left out`() {
        val model = model()

        val state = model.state.value
        assertEquals(profile, state.profile)
        assertEquals(target.dailyTarget.roundToInt(), state.profileTarget)
        assertEquals(listOf("oats"), state.liked.map { it.recipeId })
        assertEquals(profile.figures.mealCount, state.planMealCount)
        assertEquals(profile.figures.dietType, state.planDiet)
    }

    @Test
    fun `a plan made opens, made as the form asked with the profile's meals for its locks`() {
        coEvery { plans.generate(capture(asked)) } returns made
        val model = model()

        model.editDay(model.state.value.form.startDate) { it.copy(setId = "s") }
        model.generate()

        assertEquals(made.id, model.state.value.made)
        assertEquals(mapOf("breakfast" to "oats"), asked.single().days.single().locks)
        assertEquals(profile.id, asked.single().profileId)
    }

    @Test
    fun `a form with a refused field asks for nothing and says what is wrong`() {
        val model = model()

        model.edit { it.copy(durationDays = "40") }
        model.generate()

        assertEquals(true, model.state.value.showProblems)
        coVerify(exactly = 0) { plans.generate(any()) }
    }

    @Test
    fun `a plan that cannot be filled inside its limits says what stands in the way, and is asked for again with them let give way`() {
        val day = LocalDate.of(2026, 10, 12)
        val needed = RelaxationNeeded(listOf("weekly_cap", "cook_time"), listOf(RelaxedMeal(day, "lunch", listOf("weekly_cap"))))
        val refusal = ApiException(422, "PLAN_NEEDS_RELAXATION", "The plan cannot be filled inside its limits.")
        coEvery { plans.generate(capture(asked)) } throws refusal andThen made
        every { plans.relaxationNeeded(refusal) } returns needed
        val model = model()

        model.edit { it.copy(mayGiveWay = listOf("ingredient_cap")) }
        model.generate()

        val inTheWay = model.state.value.inTheWay!!
        assertSame(needed, inTheWay.needed)
        assertEquals(listOf("ingredient_cap"), inTheWay.allowed)
        assertNull(model.state.value.error)

        model.letGiveWay()

        // In the order limits give way, whatever order they were named in.
        val allowed = listOf("ingredient_cap", "cook_time", "weekly_cap")
        assertEquals(allowed, asked.last().allowRelaxations)
        assertEquals(allowed, model.state.value.form.mayGiveWay)
        assertEquals(made.id, model.state.value.made)
        assertNull(model.state.value.inTheWay)
    }

    @Test
    fun `any other refusal is said as it is`() {
        val refusal = ApiException(400, "NO_ELIGIBLE_RECIPE", "No recipe is left.")
        coEvery { plans.generate(any()) } throws refusal
        every { plans.relaxationNeeded(refusal) } returns null
        val model = model()

        model.generate()

        assertSame(refusal, model.state.value.error)
        assertNull(model.state.value.inTheWay)
    }

    @Test
    fun `a plan begun from a day of the calendar starts on that day`() {
        val day = LocalDate.of(2026, 11, 2)
        val model = NewPlanViewModel(
            SavedStateHandle(mapOf(NewPlanViewModel.PROFILE_ID to profile.id, NewPlanViewModel.START_DATE to day.toString())),
            profiles,
            users,
            favorites,
            plans,
            server,
        )

        assertEquals(day, model.state.value.form.startDate)
        assertEquals(LocalDate.now(), model().state.value.form.startDate)
    }
}

