// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.Substitution
import net.whiteravens.dietapp.domain.SubstitutionPreview
import net.whiteravens.dietapp.domain.SuggestedSubstitute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One ingredient of a meal put in the place of another, as the web's window walks through it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubstituteViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun mainThread() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun reset() = Dispatchers.resetMain()

    private val butter = IngredientChoice("butter", "Butter", "dairy")
    private val oil = IngredientChoice("oil", "Olive oil", "oils")
    private val ghee = IngredientChoice("ghee", "Ghee", "dairy")
    private val margarine = IngredientChoice("margarine", "Margarine", "fats")
    private val asked = mutableListOf<Substitution>()

    private fun preview(valid: Boolean = true, moved: List<String> = emptyList()) = SubstitutionPreview(
        before = Nutrition(500.0, 20.0, 25.0, 40.0),
        after = Nutrition(480.0, 20.0, 23.0, 40.0),
        from = MealLine("butter", "Butter", 20.0, "g"),
        to = MealLine("oil", "Olive oil", 18.0, "g"),
        sizing = "energy",
        merged = false,
        violations = if (valid) emptyList() else listOf("allergen"),
        moved = moved,
        valid = valid,
    )

    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.substitutes("plan", "meal") } returns mapOf("butter" to oil)
        coEvery { it.previewSubstitution(capture(asked)) } returns preview()
        coEvery { it.suggestSubstitute("plan", "meal", "butter") } returns SuggestedSubstitute(ghee, pick = "note", reason = "Keeps the taste.", fallbackReason = null)
        coEvery { it.substitute(capture(asked)) } returns mockk()
    }
    private val catalogue = mockk<ProfileRepository>().also { coEvery { it.ingredients("mar") } returns listOf(butter, margarine, oil) }
    private val users = mockk<UserRepository>().also { coEvery { it.me() } returns account("admin") }

    private fun account(aiMode: String) = SessionUser("u", "ala@example.test", "Ala", true, "en", aiMode = aiMode)

    private fun opened(): SubstituteViewModel = SubstituteViewModel(plans, catalogue, users).also {
        it.open("plan", "meal")
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `what is offered for a line is the catalogue's suggestion first, then what the search found, never the line itself`() = runTest(scheduler) {
        val model = opened()

        model.pickLine("butter")
        model.search("m")
        advanceUntilIdle()
        assertEquals(listOf(oil), model.state.value.candidates)

        model.search("mar")
        advanceTimeBy(100)
        // Asked once typing pauses.
        coVerify(exactly = 0) { catalogue.ingredients(any()) }
        advanceUntilIdle()

        assertEquals(listOf(oil, margarine), model.state.value.candidates)
    }

    @Test
    fun `a substitute is previewed at once, and made only where nothing stands in its way and a moved serving is taken`() = runTest(scheduler) {
        coEvery { plans.previewSubstitution(any()) } returns preview(moved = listOf("fat")) andThen preview(valid = false)
        val model = opened()
        model.pickLine("butter")

        model.pickSubstitute(oil)
        advanceUntilIdle()

        assertFalse(model.state.value.canApply)
        model.setAcceptChange(true)
        assertTrue(model.state.value.canApply)

        model.pickSubstitute(margarine)
        advanceUntilIdle()

        // Another substitute takes back what was taken for the first.
        assertFalse(model.state.value.acceptChange)
        assertFalse(model.state.value.canApply)
    }

    @Test
    fun `a model's pick is chosen at once, and its note goes with that substitution and with no other`() = runTest(scheduler) {
        val model = opened()
        model.pickLine("butter")

        model.suggest()
        advanceUntilIdle()
        assertEquals(ghee, model.state.value.to)
        assertEquals(ghee, model.state.value.candidates[1])
        model.setRewriteWithAi(true)
        model.apply()
        advanceUntilIdle()

        val made = asked.last()
        assertEquals(Substitution("plan", "meal", "butter", "ghee", rewriteWithAi = true, acceptChange = false, pick = "note"), made)

        model.pickSubstitute(oil)
        advanceUntilIdle()
        model.apply()
        advanceUntilIdle()

        assertNull(asked.last().pick)
    }

    @Test
    fun `an account without a model asks none, and the text is not reworded by one`() = runTest(scheduler) {
        coEvery { users.me() } returns account("none")
        val model = opened()
        model.pickLine("butter")

        model.suggest()
        model.pickSubstitute(oil)
        advanceUntilIdle()
        model.setRewriteWithAi(true)
        model.apply()
        advanceUntilIdle()

        assertEquals(false, model.state.value.aiOn)
        coVerify(exactly = 0) { plans.suggestSubstitute(any(), any(), any()) }
        assertFalse(asked.last().rewriteWithAi)
    }
}
