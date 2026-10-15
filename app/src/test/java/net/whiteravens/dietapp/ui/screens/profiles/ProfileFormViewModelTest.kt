// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.testing.fixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFormViewModelTest {
    private val main = StandardTestDispatcher()
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
    private val answer = CaloriePreview(target, List(5) { target })
    private val profiles = mockk<ProfileRepository>()
    private val users = mockk<UserRepository>().also {
        coEvery { it.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", "system", weightUnit = "kg", aiMode = "none")
    }
    private val server = mockk<ServerRepository>().also {
        every { it.rules } returns MutableStateFlow<InstanceRules?>(null)
        coEvery { it.refresh() } just runs
    }

    private fun model(profileId: String? = null) =
        ProfileFormViewModel(SavedStateHandle(if (profileId == null) emptyMap() else mapOf(ProfileFormViewModel.PROFILE_ID to profileId)), profiles, users, server)

    private fun ProfileFormViewModel.type() = edit { it.copy(name = "Ala", age = "30", height = "168", weight = "64.5") }

    @Test
    fun `the figures are asked about once typing pauses, and only once the API would take them`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        val model = model()
        runCurrent()

        model.edit { it.copy(age = "30") }
        advanceTimeBy(1_000)
        coVerify(exactly = 0) { profiles.preview(any()) }

        model.type()
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS / 2)
        coVerify(exactly = 0) { profiles.preview(any()) }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS)
        runCurrent()

        coVerify(exactly = 1) { profiles.preview(match { !it.acknowledgeRisk && it.age == 30 && it.weightKg == 64.5 }) }
        assertEquals(5, model.state.value.preview?.paces?.size)
        assertFalse(model.state.value.behind)
    }

    @Test
    fun `figures asked about before are not asked again, and a field that does not change them asks nothing`() = runTest(main) {
        val asked = mutableListOf<CalorieQuestion>()
        coEvery { profiles.preview(capture(asked)) } returns answer
        val model = model()
        runCurrent()

        model.type()
        advanceTimeBy(1_000)
        model.edit { it.copy(weight = "70") }
        advanceTimeBy(1_000)
        model.edit { it.copy(weight = "64.5", name = "Ola", targetWeight = "60", mealCount = 5) }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(64.5, 70.0), asked.map { it.weightKg })
        assertSame(answer, model.state.value.preview)
        assertFalse(model.state.value.behind)
    }

    @Test
    fun `figures that can no longer be worked out show nothing at once, not what earlier ones came to`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        val model = model()
        runCurrent()
        model.type()
        advanceTimeBy(1_000)
        runCurrent()

        model.edit { it.copy(height = "") }

        assertNull(model.state.value.preview)
        assertFalse(model.state.value.waiting)
    }

    @Test
    fun `a failed answer is said for the figures it was asked for alone`() = runTest(main) {
        coEvery { profiles.preview(any()) } throws IOException("unreachable")
        val model = model()
        runCurrent()
        model.type()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(model.state.value.previewFailed)
        assertFalse(model.state.value.waiting)

        model.edit { it.copy(weight = "70") }
        assertFalse(model.state.value.previewFailed)
        assertTrue(model.state.value.waiting)
    }

    @Test
    fun `a form with a refused field is not sent, and its fields say what is wrong`() = runTest(main) {
        val model = model()
        runCurrent()

        model.edit { it.copy(name = "Ala", age = "200") }
        model.save()
        runCurrent()

        assertTrue(model.state.value.showProblems)
        coVerify(exactly = 0) { profiles.create(any(), any(), any()) }
    }

    @Test
    fun `a new profile says whether the risk is acknowledged only when the form showed the risk`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        coEvery { profiles.create(any(), any(), any()) } returns Profile("p", "Ala", "balanced", 3)
        val model = model()
        runCurrent()

        model.type()
        model.edit { it.copy(age = "16") }
        assertEquals(listOf("minor"), model.state.value.risks)
        model.save()
        runCurrent()

        coVerify { profiles.create(any(), ProfileBounds.FIRST_LIMITS, false) }
        assertEquals("p", model.state.value.saved?.id)

        // With no loss asked for there is no risk, and nothing is said of it.
        val keep = model()
        runCurrent()
        keep.type()
        keep.edit { it.copy(age = "16", weeklyLossTarget = null) }
        assertEquals(emptyList<String>(), keep.state.value.risks)
        keep.save()
        runCurrent()
        coVerify { profiles.create(match { it.weeklyLossTarget == null }, any(), null) }
        assertNull(keep.state.value.error)
    }

    @Test
    fun `a profile edited is kept whole, its preferences as they are and its limits as the form has them`() = runTest(main) {
        val kept = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
        coEvery { profiles.profile(kept.id) } returns kept
        coEvery { profiles.preview(any()) } returns answer
        coEvery { profiles.update(any(), any(), any(), any()) } returns Profile(kept.id, kept.figures.name, "balanced", 5)
        val model = model(kept.id)
        runCurrent()

        assertEquals(kept.figures.name, model.state.value.editing)
        model.edit { it.copy(limits = it.limits.copy(weeklyAmount = "2500")) }
        model.save()
        runCurrent()

        coVerify {
            profiles.update(
                kept.id,
                match { it.weightKg == kept.figures.weightKg && it.name == kept.figures.name },
                kept.preferences.with(PlanLimits(2, 3, 2500, 5)),
                null,
            )
        }
    }

    @Test
    fun `the first profile goes on a step only when the step's fields hold what the API would take`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        coEvery { profiles.create(any(), any(), any()) } returns Profile("p", "Ala", "balanced", 3)
        val model = model()
        runCurrent()

        model.next()
        assertEquals(0, model.state.value.step)
        assertTrue(model.state.value.showProblems)

        model.type()
        model.next()
        assertEquals(1, model.state.value.step)
        assertFalse(model.state.value.showProblems)
        model.edit { it.copy(ownTarget = "500") }
        model.next()
        assertEquals(1, model.state.value.step)
        model.edit { it.copy(ownTarget = "") }
        model.next()
        assertEquals(2, model.state.value.step)

        // A field of an earlier step that went wrong takes the steps back to it.
        model.edit { it.copy(name = "", allergens = setOf("peanuts")) }
        model.make()
        assertEquals(0, model.state.value.step)
        coVerify(exactly = 0) { profiles.create(any(), any(), any()) }

        model.edit { it.copy(name = "Ala") }
        model.make()
        runCurrent()

        // The allergens chosen, and every other preference the API's own.
        coVerify { profiles.create(match { it.allergens == listOf("peanuts") && it.weightReminderCadence == "weekly" }, null, null) }
        assertEquals("p", model.state.value.saved?.id)

        model.startOver()
        assertNull(model.state.value.saved)
        assertEquals(0, model.state.value.step)
        assertEquals(ProfileForm(), model.state.value.form)
    }

    @Test
    fun `the calculator begins with a profile's figures once the unit to show them in is known`() = runTest(main) {
        val kept = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
        coEvery { users.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", "system", weightUnit = "lb", aiMode = "none")
        coEvery { profiles.preview(any()) } returns answer
        val model = model()

        model.startFrom(kept)
        runCurrent()

        assertEquals(WeightUnit.LB, model.state.value.unit)
        assertEquals(WeightUnit.LB.shownToOne(kept.figures.weightKg).toString(), model.state.value.form.weight)

        model.startFrom(null)
        assertEquals(ProfileForm(), model.state.value.form)
    }
}
