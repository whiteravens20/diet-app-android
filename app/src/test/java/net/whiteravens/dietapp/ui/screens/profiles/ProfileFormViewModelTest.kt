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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.remote.CaloriePreviewDto
import net.whiteravens.dietapp.data.remote.ApiErrorIssueDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
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
import net.whiteravens.dietapp.domain.LabelLimits
import net.whiteravens.dietapp.domain.LabelLimit
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

        coVerify(exactly = 1) { profiles.preview(match { it.age == 30 && it.weightKg == 64.5 }) }
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
    fun `a new profile is sent with the limits from labels turned on, and not while one turned on holds no number`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns preview("profile-calories-preview-minor-keeping")
        coEvery { profiles.create(any(), any(), any()) } returns Profile("p", "Ala", "balanced", 3, 70.0, null)
        val model = model()
        runCurrent()

        model.type()
        model.edit { it.copy(labels = it.labels + (LabelLimit.SUGARS_AT_MOST to LabelLimitTyped(on = true, grams = ""))) }
        model.save()
        runCurrent()
        coVerify(exactly = 0) { profiles.create(any(), any(), any()) }
        assertTrue(ProfileField.LABEL_SUGARS in model.state.value.marks)

        model.edit { it.copy(labels = it.labels + (LabelLimit.SUGARS_AT_MOST to LabelLimitTyped(on = true, grams = "45,5"))) }
        model.save()
        runCurrent()
        coVerify { profiles.create(any(), any(), LabelLimits(sugarsAtMost = 45.5)) }
    }

    @Test
    fun `the risk of a loss is the API's to say, and a new profile is sent with no word about it`() = runTest(main) {
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

        coVerify { profiles.create(any(), ProfileChoices.FIRST_LIMITS, LabelLimits()) }
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
        coVerify { profiles.create(match { it.weeklyLossTarget == null }, any(), LabelLimits()) }
        assertNull(keep.state.value.error)
    }

    @Test
    fun `a body planned no loss is told so by the API, and the form asks nothing of it`() = runTest(main) {
        coEvery { profiles.preview(any()) } returns preview("profile-calories-preview-minor-losing")
        val model = model()
        runCurrent()
        model.type()
        model.edit { it.copy(age = "16") }
        advanceTimeBy(ProfileFormViewModel.PREVIEW_DELAY_MS * 2)
        runCurrent()

        assertTrue(model.state.value.preview!!.calculation.goalHeld)
        assertEquals(listOf("minor"), model.state.value.risks)
        coVerify { profiles.preview(match { it.age == 16 }) }
    }

    /** The form of the kept profile of the fixture, open, on an API that keeps each change it is sent, the answers given in [kept]. */
    private fun TestScope.editing(kept: MutableList<ProfileDetails> = mutableListOf()): Pair<ProfileFormViewModel, ProfileDetails> {
        val first = json.decodeFromString<ProfileDto>(fixture("profile")).toDetails()
        kept += first
        coEvery { profiles.profile(first.id) } returns first
        coEvery { profiles.preview(any()) } returns answer
        // As the API does, the parts named and no other.
        coEvery { profiles.change(first.id, any(), any()) } answers {
            kept.last().let { it.copy(figures = it.figures.withParts(secondArg(), thirdArg())) }.also { kept += it }
        }
        coEvery { profiles.changeLimits(first.id, any(), any(), any()) } answers {
            kept.last().let { before ->
                val parts = arg<Set<String>>(3)
                before.copy(
                    preferences = before.preferences
                        .with(before.preferences.limits.withParts(secondArg(), parts))
                        .copy(labelLimits = if ("labelLimits" in parts) thirdArg() else before.preferences.labelLimits),
                )
            }.also { kept += it }
        }
        val model = model(first.id)
        runCurrent()
        return model to first
    }

    @Test
    fun `a field of a kept profile is kept once it is left, alone, and nothing while it is typed`() = runTest(main) {
        val (model, first) = editing()

        model.edit { it.copy(weight = "70") }
        runCurrent()
        coVerify(exactly = 0) { profiles.change(any(), any(), any()) }
        model.keep()
        runCurrent()

        coVerify(exactly = 1) { profiles.change(first.id, match { it.weightKg == 70.0 && it.name == first.figures.name }, setOf("weightKg")) }
        assertEquals(Autosave.SAVED, model.state.value.autosave)
        // Left again with nothing new in it, the field sends nothing.
        model.keep()
        runCurrent()
        coVerify(exactly = 1) { profiles.change(any(), any(), any()) }
    }

    @Test
    fun `a choice of a kept profile is kept the moment it is made, the custom diet with its split`() = runTest(main) {
        val (model, first) = editing()

        model.edit { it.copy(activityLevel = "active") }
        runCurrent()
        model.edit { it.copy(dietType = "custom") }
        runCurrent()

        coVerify { profiles.change(first.id, match { it.activityLevel == "active" }, setOf("activityLevel")) }
        coVerify {
            profiles.change(first.id, match { it.dietType == "custom" && it.customMacroSplit == ProfileChoices.FIRST_SPLIT }, setOf("dietType", "customMacroSplit"))
        }
    }

    @Test
    fun `a part the API refuses is marked at its field, and the other parts are kept`() = runTest(main) {
        val (model, first) = editing()
        coEvery { profiles.change(first.id, any(), setOf("age")) } throws
            ApiException(400, "VALIDATION_FAILED", "Request payload failed validation.", listOf(ApiErrorIssueDto("age", "Too big", "too_big", maximum = 120.0, inclusive = true)))

        model.edit { it.copy(age = "200", height = "175") }
        model.keep()
        runCurrent()

        coVerify { profiles.change(first.id, match { it.heightCm == 175.0 }, setOf("heightCm")) }
        assertEquals(FieldRefusal.TooBig(120.0, inclusive = true), model.state.value.marks.refusals.fields[ProfileField.AGE])
        assertEquals(Autosave.NOT_SAVED, model.state.value.autosave)
        assertNull(model.state.value.error)
        // The refused age is not sent again until it changes.
        model.keep()
        runCurrent()
        coVerify(exactly = 1) { profiles.change(first.id, any(), setOf("age")) }
    }

    @Test
    fun `a field that holds no number is not sent and says so, and the others are kept`() = runTest(main) {
        val (model, first) = editing()

        model.edit { it.copy(age = "thirty", height = "175") }
        model.keep()
        runCurrent()

        coVerify { profiles.change(first.id, any(), setOf("heightCm")) }
        coVerify(exactly = 0) { profiles.change(first.id, any(), setOf("age")) }
        assertEquals(setOf(ProfileField.AGE), model.state.value.marks.notNumbers)
        assertEquals(Autosave.NOT_SAVED, model.state.value.autosave)
    }

    @Test
    fun `a limit of a kept profile's plans is kept through its preferences, a limit on a kind taken away as none`() = runTest(main) {
        val kept = mutableListOf<ProfileDetails>()
        val (model, first) = editing(kept)

        model.edit { it.copy(limits = it.limits.copy(kindPerDay = "2")) }
        model.keep()
        runCurrent()
        model.edit { it.copy(limits = it.limits.copy(kindPerDay = "")) }
        model.keep()
        runCurrent()

        coVerify { profiles.changeLimits(first.id, match { it.maxMealsPerDaySameKind == 2 }, any(), setOf("maxMealsPerDaySameKind")) }
        coVerify { profiles.changeLimits(first.id, match { it.maxMealsPerDaySameKind == null }, any(), setOf("maxMealsPerDaySameKind")) }
        assertNull(kept.last().preferences.limits.maxMealsPerDaySameKind)
    }

    @Test
    fun `a change that met no network is said, sent again a while later, and kept once the network is back`() = runTest(main) {
        val (model, first) = editing()
        var tries = 0
        coEvery { profiles.change(first.id, any(), setOf("weightKg")) } answers {
            tries += 1
            if (tries == 1) throw IOException("no network") else first.copy(figures = secondArg())
        }

        model.edit { it.copy(weight = "70") }
        model.keep()
        runCurrent()
        assertEquals(Autosave.OFFLINE, model.state.value.autosave)

        advanceTimeBy(ProfileFormViewModel.SEND_AGAIN_MS + 1)
        runCurrent()

        assertEquals(2, tries)
        assertEquals(Autosave.SAVED, model.state.value.autosave)
    }

    @Test
    fun `a value put back as the profile has it is kept, whether it met no network or was refused, and leaving asks nothing`() = runTest(main) {
        val (model, first) = editing()
        var tries = 0
        coEvery { profiles.change(first.id, any(), setOf("weightKg")) } answers {
            tries += 1
            throw IOException("no network")
        }
        coEvery { profiles.change(first.id, any(), setOf("age")) } throws
            ApiException(400, "VALIDATION_FAILED", "Request payload failed validation.", listOf(ApiErrorIssueDto("age", "Too big", "too_big", maximum = 120.0, inclusive = true)))

        model.edit { it.copy(weight = "70", age = "200") }
        model.keep()
        runCurrent()
        assertEquals(Autosave.OFFLINE, model.state.value.autosave)

        // Both put back as the profile has them: nothing is left to send, and nothing is sent again.
        model.edit { it.copy(weight = "66", age = "34") }
        model.keep()
        runCurrent()
        assertEquals(Autosave.SAVED, model.state.value.autosave)
        val sent = tries
        advanceTimeBy(ProfileFormViewModel.SEND_AGAIN_MS * 3)
        runCurrent()
        assertEquals(sent, tries)

        model.leave()
        runCurrent()
        assertFalse(model.state.value.askLeaving)
        assertTrue(model.state.value.left)
    }

    @Test
    fun `leaving waits for every change on its way, and asks first where one is not kept`() = runTest(main) {
        val (model, first) = editing()
        model.edit { it.copy(weight = "70") }
        model.leave()
        runCurrent()
        assertTrue(model.state.value.left)
        coVerify { profiles.change(first.id, any(), setOf("weightKg")) }

        val (offline, kept) = editing()
        coEvery { profiles.change(kept.id, any(), any()) } throws IOException("no network")
        offline.edit { it.copy(weight = "71") }
        offline.leave()
        runCurrent()
        assertTrue(offline.state.value.askLeaving)
        assertFalse(offline.state.value.left)
        offline.stay()
        assertFalse(offline.state.value.askLeaving)
        offline.leave()
        runCurrent()
        offline.leaveBehind()
        assertTrue(offline.state.value.left)
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

/** These figures with the parts [parts] names, by their names in the API, as [sent] has them: what the API keeps of a change. */
private fun ProfileFigures.withParts(sent: ProfileFigures, parts: Set<String>): ProfileFigures {
    fun <T> part(name: String, kept: T, changed: T): T = if (name in parts) changed else kept
    return copy(
        name = part("name", name, sent.name),
        age = part("age", age, sent.age),
        sex = part("sex", sex, sent.sex),
        heightCm = part("heightCm", heightCm, sent.heightCm),
        weightKg = part("weightKg", weightKg, sent.weightKg),
        targetWeightKg = part("targetWeightKg", targetWeightKg, sent.targetWeightKg),
        activityLevel = part("activityLevel", activityLevel, sent.activityLevel),
        dietType = part("dietType", dietType, sent.dietType),
        weeklyLossTarget = part("weeklyLossTarget", weeklyLossTarget, sent.weeklyLossTarget),
        manualCalorieTarget = part("manualCalorieTarget", manualCalorieTarget, sent.manualCalorieTarget),
        mealCount = part("mealCount", mealCount, sent.mealCount),
        weightReminderCadence = part("weightReminderCadence", weightReminderCadence, sent.weightReminderCadence),
        waterTargetMl = part("waterTargetMl", waterTargetMl, sent.waterTargetMl),
        waterTracking = part("waterTracking", waterTracking, sent.waterTracking),
        cookingReminders = part("cookingReminders", cookingReminders, sent.cookingReminders),
        waterReminders = part("waterReminders", waterReminders, sent.waterReminders),
        customMacroSplit = part("customMacroSplit", customMacroSplit, sent.customMacroSplit),
    )
}

/** These limits with the ones [parts] names as [sent] has them. */
private fun PlanLimits.withParts(sent: PlanLimits, parts: Set<String>): PlanLimits {
    fun <T> part(name: String, kept: T, changed: T): T = if (name in parts) changed else kept
    return copy(
        maxConsecutiveDaysSameMeal = part("maxConsecutiveDaysSameMeal", maxConsecutiveDaysSameMeal, sent.maxConsecutiveDaysSameMeal),
        maxTimesPerWeekSameMeal = part("maxTimesPerWeekSameMeal", maxTimesPerWeekSameMeal, sent.maxTimesPerWeekSameMeal),
        maxMealsPerDaySameKind = part("maxMealsPerDaySameKind", maxMealsPerDaySameKind, sent.maxMealsPerDaySameKind),
        maxMealsPerWeekSameKind = part("maxMealsPerWeekSameKind", maxMealsPerWeekSameKind, sent.maxMealsPerWeekSameKind),
        kindLimits = part("kindLimits", kindLimits, sent.kindLimits),
        inventoryBiasResetEvery = part("inventoryBiasResetEvery", inventoryBiasResetEvery, sent.inventoryBiasResetEvery),
    )
}
