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
import net.whiteravens.dietapp.data.remote.CaloriePreviewDto
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDetails
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.refusal
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
class ProfileFormViewModelTest {
    private val main = StandardTestDispatcher()
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val target = json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain()
    private val answer = CaloriePreview(target, List(5) { target })

    /** What a running instance answered for the figures of a girl of 16. */
    private fun preview(name: String) =
        json.decodeFromString<CaloriePreviewDto>(fixture(name)).let { CaloriePreview(it.calculation.toDomain(), it.paces.map { pace -> pace.toDomain() }) }
    private val profiles = mockk<ProfileRepository>()
    private val users = mockk<UserRepository>().also {
        coEvery { it.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", weightUnit = "kg", aiMode = "none")
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
    fun `a form with a field that holds no number is not sent, and its fields say what is wrong`() = runTest(main) {
        val model = model()
        runCurrent()

        model.edit { it.copy(name = "Ala", age = "two hundred", height = "168", weight = "66") }
        model.save()
        runCurrent()

        assertTrue(model.state.value.showProblems)
        assertEquals(setOf(ProfileField.AGE), model.state.value.marks.notNumbers)
        coVerify(exactly = 0) { profiles.create(any(), any(), any()) }
    }

    @Test
    fun `a form the API refuses is marked field by field, kinds of protein by their row, and a mark goes once its field changes`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        coEvery { profiles.create(any(), any(), any()) } throws refusal("profile-refused")
        val model = model()
        runCurrent()

        // As the API's answer had it: no name, 200 years, a weight of 500 kg to reach, and every limit past its bounds.
        model.edit {
            it.copy(
                name = "", age = "200", height = "168", weight = "66", targetWeight = "500",
                limits = LimitsTyped("9", "0", "", "", listOf(KindLimitTyped("eggs", "9", "50")), "25"),
            )
        }
        model.save()
        runCurrent()

        val marks = model.state.value.marks
        assertEquals(FieldRefusal.TooSmall(1.0, inclusive = true), marks.refusals.fields[ProfileField.NAME])
        assertEquals(FieldRefusal.TooBig(120.0, inclusive = true), marks.refusals.fields[ProfileField.AGE])
        assertEquals(FieldRefusal.TooBig(35.0, inclusive = true), marks.refusals.kinds["eggs"]?.get(KindPart.WEEK))
        // Every field the API named is on the form, so the form says it alone.
        assertNull(model.state.value.error)

        model.edit { it.copy(age = "30") }

        assertFalse(ProfileField.AGE in model.state.value.marks)
        assertTrue(ProfileField.NAME in model.state.value.marks)
    }

    @Test
    fun `figures the API refuses are marked as they are typed, the card waits for them to be corrected, and a corrected field loses its mark`() = runTest(main) {
        coEvery { profiles.preview(match { it.age == 5 }) } throws refusal("calorie-preview-refused")
        coEvery { profiles.preview(match { it.age != 5 }) } returns answer
        val model = model()
        runCurrent()

        // As the API's answer had it: five years, 300 cm, 10 kg and a target of 100 kcal.
        model.edit { it.copy(name = "Ala", age = "5", height = "300", weight = "10", ownTarget = "100") }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()

        val marks = model.state.value.marks
        assertEquals(setOf(ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT, ProfileField.OWN_TARGET), marks.refusals.fields.keys)
        assertTrue(model.state.value.previewRefused)
        assertFalse(model.state.value.waiting)
        assertFalse(model.state.value.previewFailed)

        model.edit { it.copy(age = "30") }
        assertFalse(ProfileField.AGE in model.state.value.marks)
        assertTrue(ProfileField.HEIGHT in model.state.value.marks)
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()

        // The figures as they are now came to an answer: nothing of them is refused any longer.
        assertEquals(emptyMap<ProfileField, FieldRefusal>(), model.state.value.refusals.fields)
        assertNotNull(model.state.value.preview)
    }

    @Test
    fun `the risk of a loss is the API's to say, and a new profile says it acknowledged only when the form showed it`() = runTest(main) {
        coEvery { profiles.preview(match { it.weeklyLossTarget != null }) } returns preview("profile-calories-preview-minor-losing")
        coEvery { profiles.preview(match { it.weeklyLossTarget == null }) } returns preview("profile-calories-preview-minor-keeping")
        coEvery { profiles.create(any(), any(), any()) } returns Profile("p", "Ala", "balanced", 3, 70.0, null)
        val model = model()
        runCurrent()

        model.type()
        model.edit { it.copy(age = "16") }
        // Nothing is said of a risk before the API has answered for the figures.
        assertEquals(emptyList<String>(), model.state.value.risks)
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()
        assertEquals(listOf("minor"), model.state.value.risks)
        model.save()
        runCurrent()

        coVerify { profiles.create(any(), ProfileChoices.FIRST_LIMITS, false) }
        assertEquals("p", model.state.value.saved?.id)

        // With no loss asked for the API sees no risk in it, although it warns of the age, and nothing is said of it.
        val keep = model()
        runCurrent()
        keep.type()
        keep.edit { it.copy(age = "16", weeklyLossTarget = null) }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()
        assertEquals(emptyList<String>(), keep.state.value.risks)
        keep.save()
        runCurrent()
        coVerify { profiles.create(match { it.weeklyLossTarget == null }, any(), null) }
        assertNull(keep.state.value.error)
    }

    @Test
    fun `the box ticked is said to the API with the figures, and stays while the API still sees the risk`() = runTest(main) {
        coEvery { profiles.preview(match { !it.acknowledgeRisk }) } returns preview("profile-calories-preview-minor-losing")
        coEvery { profiles.preview(match { it.acknowledgeRisk }) } returns preview("profile-calories-preview-minor-losing-acknowledged")
        val model = model()
        runCurrent()
        model.type()
        model.edit { it.copy(age = "16") }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()
        assertTrue(model.state.value.preview!!.calculation.goalHeld)

        model.edit { it.copy(acknowledgeRisk = true) }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()

        coVerify { profiles.preview(match { it.acknowledgeRisk && it.age == 16 }) }
        assertFalse(model.state.value.preview!!.calculation.goalHeld)
        assertEquals(listOf("minor"), model.state.value.risks)
    }

    @Test
    fun `a profile edited is kept whole, its preferences as they are and its limits as the form has them`() = runTest(main) {
        val kept = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
        coEvery { profiles.profile(kept.id) } returns kept
        coEvery { profiles.preview(any()) } returns answer
        coEvery { profiles.update(any(), any(), any(), any()) } returns Profile(kept.id, kept.figures.name, "balanced", 5, kept.figures.weightKg, kept.figures.targetWeightKg)
        val model = model(kept.id)
        runCurrent()

        assertEquals(kept.figures.name, model.state.value.editing)
        model.edit { it.copy(limits = it.limits.copy(kindPerDay = "2", kindLimits = listOf(KindLimitTyped("fish", perWeek = "3")))) }
        model.save()
        runCurrent()

        coVerify {
            profiles.update(
                kept.id,
                match { it.weightKg == kept.figures.weightKg && it.name == kept.figures.name },
                kept.preferences.with(PlanLimits(2, 3, 2, null, listOf(KindLimit("fish", null, 3)), 5)),
                null,
            )
        }
    }

    @Test
    fun `the first profile goes on a step only when the step's fields hold numbers the API did not refuse`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns answer
        val made = Profile("p", "Ala", "balanced", 3, 70.0, null)
        // As the API's answer had it: a name of 81 letters.
        coEvery { profiles.create(match { it.name.length > 80 }, any(), any()) } throws refusal("profile-name-too-long")
        coEvery { profiles.create(match { it.name == "Ala" }, any(), any()) } returns made
        val model = model()
        runCurrent()

        model.next()
        assertEquals(0, model.state.value.step)
        assertTrue(model.state.value.showProblems)

        model.type()
        model.next()
        assertEquals(1, model.state.value.step)
        assertFalse(model.state.value.showProblems)
        model.edit { it.copy(ownTarget = "five hundred") }
        model.next()
        assertEquals(1, model.state.value.step)
        model.edit { it.copy(ownTarget = "") }
        model.next()
        assertEquals(2, model.state.value.step)

        // A field of an earlier step the API refuses takes the steps back to it, with its reason.
        model.edit { it.copy(name = "A".repeat(81), allergens = setOf("peanuts")) }
        model.make()
        runCurrent()
        assertEquals(0, model.state.value.step)
        assertEquals(FieldRefusal.TooBig(80.0, inclusive = true), model.state.value.refusals.fields[ProfileField.NAME])
        assertNull(model.state.value.saved)

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
}
