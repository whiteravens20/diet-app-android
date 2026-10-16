// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.PreferencesChange
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel.IngredientList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val main = StandardTestDispatcher()
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val kept: ProfileDetails = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails().let {
        it.copy(preferences = it.preferences.copy(favoriteIngredientIds = listOf("apple")))
    }
    private val profiles = mockk<ProfileRepository>().also {
        coEvery { it.profiles() } returns listOf(kept)
        coEvery { it.ingredientsOf(listOf("apple")) } returns listOf(IngredientChoice("apple", "Apple", "fruits"))
    }
    private val users = mockk<UserRepository>().also {
        coEvery { it.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", "system", weightUnit = "lb", aiMode = "none")
    }
    private val server = mockk<ServerRepository>().also {
        every { it.rules } returns MutableStateFlow<InstanceRules?>(InstanceRules(2, emptyMap()))
        coEvery { it.refresh() } just runs
    }

    private fun loaded(): ProfilesViewModel = ProfilesViewModel(profiles, users, server).also { it.load() }

    @Test
    fun `the profiles are read with the names of the ingredients they keep, in the account's unit`() = runTest(main) {
        val model = loaded()
        runCurrent()

        assertEquals(listOf(kept), model.state.value.list)
        assertEquals("Apple", model.state.value.names["apple"])
        assertEquals(WeightUnit.LB, model.state.value.unit)
    }

    @Test
    fun `a change of preferences is shown at once, sends the part that changed alone and keeps the profile as the API answered`() = runTest(main) {
        val answered = CompletableDeferred<ProfileDetails>()
        val saved = kept.copy(figures = kept.figures.copy(allergens = listOf("dairy", "eggs")))
        coEvery { profiles.updatePreferences(kept.id, any()) } coAnswers { answered.await() }
        val model = loaded()
        runCurrent()

        model.changeAllergens(kept, listOf("eggs", "dairy"))
        runCurrent()
        // In the order the application lists them, whatever order they were chosen in.
        assertEquals(listOf("dairy", "eggs"), model.state.value.preferencesOf(kept).allergens)
        coVerify { profiles.updatePreferences(kept.id, PreferencesChange(allergens = listOf("dairy", "eggs"))) }

        // While one change is on its way, another is not sent.
        model.changeCuisines(kept, listOf("polish"))
        coVerify(exactly = 0) { profiles.updatePreferences(kept.id, PreferencesChange(preferredCuisines = listOf("polish"))) }

        answered.complete(saved)
        runCurrent()
        assertNull(model.state.value.sending)
        assertSame(saved, model.state.value.list.single())
    }

    @Test
    fun `a change the API refuses is taken back and said`() = runTest(main) {
        val refusal = ApiException(400, "VALIDATION_FAILED", "Request payload failed validation.")
        coEvery { profiles.updatePreferences(kept.id, any()) } throws refusal
        val model = loaded()
        runCurrent()

        model.changeCuisines(kept, listOf("italian"))
        runCurrent()

        assertEquals(emptyList<String>(), model.state.value.preferencesOf(kept).cuisines)
        assertSame(refusal, model.state.value.error)
    }

    @Test
    fun `the catalogue is searched once two letters are typed and typing pauses, and an ingredient found is added by name`() = runTest(main) {
        val bean = IngredientChoice("bean", "Bean", "legumes")
        coEvery { profiles.ingredients("be") } returns listOf(bean)
        coEvery { profiles.updatePreferences(kept.id, any()) } returns kept
        val model = loaded()
        runCurrent()

        model.search(kept.id, IngredientList.AVOIDED, "b")
        advanceTimeBy(1_000)
        coVerify(exactly = 0) { profiles.ingredients(any()) }

        model.search(kept.id, IngredientList.AVOIDED, "be")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()
        assertEquals(listOf(bean), model.state.value.searches[kept.id to IngredientList.AVOIDED]?.found)

        model.add(kept, IngredientList.AVOIDED, bean)
        runCurrent()

        coVerify { profiles.updatePreferences(kept.id, PreferencesChange(excludedIngredientIds = listOf("bean"))) }
        assertEquals("Bean", model.state.value.names["bean"])
        assertEquals("", model.state.value.searches[kept.id to IngredientList.AVOIDED]?.text)
    }

    @Test
    fun `an ingredient is removed from its list alone`() = runTest(main) {
        coEvery { profiles.updatePreferences(kept.id, any()) } returns kept
        val model = loaded()
        runCurrent()

        model.remove(kept, IngredientList.FAVOURITE, "apple")
        runCurrent()

        coVerify { profiles.updatePreferences(kept.id, PreferencesChange(favoriteIngredientIds = emptyList())) }
    }

    @Test
    fun `a profile deleted leaves the list, and the first profile's steps say what comes next`() = runTest(main) {
        coEvery { profiles.delete(kept.id) } just runs
        val model = loaded()
        runCurrent()

        coEvery { profiles.profiles() } returns emptyList()
        model.delete(kept)
        runCurrent()
        assertEquals(emptyList<ProfileDetails>(), model.state.value.list)

        coEvery { profiles.profiles() } returns listOf(kept)
        val made = Profile(kept.id, kept.figures.name, "balanced", 5, kept.figures.weightKg, kept.figures.targetWeightKg)
        model.made(made)
        runCurrent()
        assertEquals(made, model.state.value.made)
        assertEquals(listOf(kept), model.state.value.list)
    }
}
