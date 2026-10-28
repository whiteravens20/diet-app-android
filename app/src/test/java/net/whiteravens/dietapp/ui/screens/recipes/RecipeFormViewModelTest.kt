// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.RecipeDto
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.OwnRecipe
import net.whiteravens.dietapp.domain.OwnRecipeLine
import net.whiteravens.dietapp.domain.RecipePreview
import net.whiteravens.dietapp.domain.RecipeToEdit
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.refusal
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** A recipe written by hand, or edited, as its screen keeps it: ingredients found, a serving asked about, and the recipe saved or refused. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeFormViewModelTest {
    private val main = StandardTestDispatcher()
    private val json = AppModule.json()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val oats = LineIngredient(OATS, "Rolled oats", listOf("g"), "piece")
    private val milk = LineIngredient(MILK, "Whole milk", listOf("ml", "g"), "piece")
    private val serving = RecipePreview(Nutrition(325.0, 17.0, 13.0, 35.0), listOf("dairy", "gluten"), listOf("vegetarian"))
    private val created = json.decodeFromString<RecipeDto>(fixture("own-recipe-created")).toDomain()
    private val updated = json.decodeFromString<RecipeDto>(fixture("own-recipe-updated")).toDomain()

    /** Reads the details of a refusal as the app reads them. */
    private val reading = RecipeRepository(mockk(), mockk(), json)
    private val recipes = mockk<RecipeRepository>().also {
        every { it.problemsOf(any()) } answers { reading.problemsOf(firstArg()) }
        every { it.alreadyYours(any()) } answers { reading.alreadyYours(firstArg()) }
        coEvery { it.preview(any(), any()) } returns serving
    }

    private fun model(recipeId: String? = null) =
        RecipeFormViewModel(SavedStateHandle(if (recipeId == null) emptyMap() else mapOf("recipeId" to recipeId)), recipes)

    /** Fills the form in as a person would: a name, a description, a meal, and two lines with their amounts. */
    private fun RecipeFormViewModel.fill() {
        edit { it.copy(title = "Oat pancakes", description = "Soft pancakes of oats and milk.", mealTypes = listOf("breakfast")) }
        add(oats)
        add(milk)
        val (first, second) = state.value.form.lines
        edit { form -> form.copy(lines = listOf(first.copy(quantity = "80"), second.copy(quantity = "300", note = "warm"))) }
        edit { form -> form.copy(steps = listOf(form.steps.single().copy(text = "Fry small pancakes."))) }
    }

    @Test
    fun `a new recipe starts empty, and an ingredient found goes on a line of its own, the search emptied`() = runTest(main) {
        coEvery { recipes.ingredients("mil") } returns listOf(milk)
        val model = model()
        runCurrent()

        model.search("m")
        advanceTimeBy(1_000)
        coVerify(exactly = 0) { recipes.ingredients(any()) }
        model.search("mil")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        assertEquals(listOf(milk), model.state.value.found)
        assertTrue(model.state.value.searched)

        model.add(milk)

        assertNull(model.state.value.editing)
        assertEquals(listOf(FormLine(1, milk, "", "ml", "")), model.state.value.form.lines)
        assertEquals("", model.state.value.search)
        assertEquals(emptyList<LineIngredient>(), model.state.value.found)
    }

    @Test
    fun `a serving is asked about once typing pauses, and again only for a change that changes it`() = runTest(main) {
        val model = model()
        runCurrent()

        model.fill()
        advanceTimeBy(RecipeFormViewModel.PREVIEW_DELAY_MS / 2)
        coVerify(exactly = 0) { recipes.preview(any(), any()) }
        advanceTimeBy(RecipeFormViewModel.PREVIEW_DELAY_MS)
        runCurrent()

        coVerify(exactly = 1) { recipes.preview(1, listOf(OwnRecipeLine(OATS, 80.0, "g"), OwnRecipeLine(MILK, 300.0, "ml"))) }
        assertSame(serving, model.state.value.preview)

        // A note says nothing of a serving; the servings do.
        model.edit { form -> form.copy(lines = form.lines.map { it.copy(note = "cold") }) }
        advanceTimeBy(1_000)
        model.edit { it.copy(servings = "2") }
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 2) { recipes.preview(any(), any()) }
        coVerify(exactly = 1) { recipes.preview(2, any()) }
    }

    @Test
    fun `an amount that is no number holds the serving back without asking`() = runTest(main) {
        val model = model()
        runCurrent()
        model.fill()
        model.edit { form -> form.copy(lines = form.lines.map { it.copy(quantity = "a cup") }) }
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { recipes.preview(any(), any()) }
        assertNull(model.state.value.preview)
    }

    @Test
    fun `the fields a serving is refused for are marked, and a mark goes when its field changes`() = runTest(main) {
        coEvery { recipes.preview(any(), any()) } throws refusal("own-recipe-preview-refused")
        val model = model()
        runCurrent()
        model.fill()
        advanceTimeBy(1_000)
        runCurrent()

        val first = model.state.value.form.lines.first().key
        val marks = model.state.value.marks.refusals
        assertEquals(mapOf(RecipeField.SERVINGS to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(12.0, true))), marks.fields)
        assertEquals(mapOf(first to mapOf(LinePart.QUANTITY to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(18000.0, true)))), marks.lines)
        assertNull(model.state.value.preview)
        assertNull(model.state.value.previewFailed)

        model.edit { it.copy(servings = "3") }

        assertTrue(model.state.value.marks.refusals.fields.isEmpty())
        // The servings say with the amount how much a serving holds: the amount's mark goes as well.
        assertTrue(model.state.value.marks.refusals.lines.isEmpty())
    }

    @Test
    fun `a serving that cannot be asked about says why`() = runTest(main) {
        val offline = IOException("no answer")
        coEvery { recipes.preview(any(), any()) } throws offline
        val model = model()
        runCurrent()
        model.fill()
        advanceTimeBy(1_000)
        runCurrent()

        assertSame(offline, model.state.value.previewFailed)
        assertFalse(model.state.value.previewRefusals.marked)
    }

    @Test
    fun `saving with a field that holds no number marks it and sends nothing`() = runTest(main) {
        val model = model()
        runCurrent()
        model.fill()
        model.edit { form -> form.copy(cookMinutes = "", lines = form.lines.mapIndexed { index, line -> if (index == 1) line.copy(quantity = "") else line }) }
        // Before saving is asked for, nothing is marked for want of a number.
        assertTrue(model.state.value.marks.notWhole.isEmpty())

        model.save()
        runCurrent()

        coVerify(exactly = 0) { recipes.write(any()) }
        assertEquals(setOf(RecipeField.COOK), model.state.value.marks.notWhole)
        assertEquals(setOf(model.state.value.form.lines[1].key), model.state.value.marks.notNumbers)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun `a recipe saved is sent as it is typed, and opens as the API kept it`() = runTest(main) {
        coEvery { recipes.write(any()) } returns created
        val model = model()
        runCurrent()
        model.fill()

        model.save()
        runCurrent()

        coVerify {
            recipes.write(
                OwnRecipe(
                    title = "Oat pancakes",
                    description = "Soft pancakes of oats and milk.",
                    steps = listOf("Fry small pancakes."),
                    servings = 1,
                    mealTypes = listOf("breakfast"),
                    prepMinutes = 10,
                    cookMinutes = 0,
                    difficulty = "easy",
                    cuisine = null,
                    lines = listOf(OwnRecipeLine(OATS, 80.0, "g"), OwnRecipeLine(MILK, 300.0, "ml", "warm")),
                ),
            )
        }
        assertEquals(created.id, model.state.value.saved)
    }

    @Test
    fun `an edit starts on the recipe as it is kept, and opens the new version it saves`() = runTest(main) {
        val kept = OwnRecipe("Oat pancakes", "Soft.", listOf("Fry."), 2, listOf("breakfast"), 10, 15, "easy", null, listOf(OwnRecipeLine(OATS, 80.0, "g")))
        coEvery { recipes.toEdit(created.id) } returns RecipeToEdit(kept, listOf(oats))
        coEvery { recipes.edit(any(), any()) } returns updated
        val model = model(created.id)
        runCurrent()

        assertEquals(created.id, model.state.value.editing)
        assertEquals(listOf(FormLine(0, oats, "80", "g", "")), model.state.value.form.lines)
        model.edit { it.copy(servings = "3") }
        model.save()
        runCurrent()

        coVerify { recipes.edit(created.id, kept.copy(servings = 3)) }
        assertEquals(updated.id, model.state.value.saved)
    }

    @Test
    fun `an edit whose recipe cannot be read says why, and reads it again when asked`() = runTest(main) {
        val offline = IOException("no answer")
        coEvery { recipes.toEdit("gone") } throws offline
        val model = model("gone")
        runCurrent()

        assertSame(offline, model.state.value.loadError)
        model.save()
        runCurrent()
        coVerify(exactly = 0) { recipes.edit(any(), any()) }

        model.load()
        runCurrent()
        coVerify(exactly = 2) { recipes.toEdit("gone") }
    }

    @Test
    fun `the fields saving is refused for are marked, and nothing else is said of the refusal`() = runTest(main) {
        coEvery { recipes.write(any()) } throws refusal("own-recipe-refused")
        val model = model()
        runCurrent()
        model.fill()

        model.save()
        runCurrent()

        assertEquals(setOf(RecipeField.SERVINGS, RecipeField.STEPS, RecipeField.PREP), model.state.value.saveRefusals.fields.keys)
        assertNull(model.state.value.error)
        assertNull(model.state.value.saved)
    }

    @Test
    fun `a rule a recipe would break marks the field it is about`() = runTest(main) {
        coEvery { recipes.write(any()) } throws refusal("own-recipe-invalid-places")
        val model = model()
        runCurrent()
        model.fill()
        model.edit { it.copy(steps = listOf(FormStep(20, ""), FormStep(21, "Fry <b>small</b> pancakes."))) }

        model.save()
        runCurrent()

        val refused = model.state.value.saveRefusals
        val (first, second) = model.state.value.form.lines
        assertEquals(mapOf(RecipeField.TITLE to RecipeRefusal.AgainstRule("missing-locale")), refused.fields)
        assertEquals(mapOf(21 to RecipeRefusal.AgainstRule("markup")), refused.steps)
        assertEquals(
            mapOf(
                first.key to mapOf(LinePart.QUANTITY to RecipeRefusal.AgainstRule("quantity-too-large")),
                second.key to mapOf(LinePart.NOTE to RecipeRefusal.AgainstRule("markup")),
            ),
            refused.lines,
        )
        assertNull(model.state.value.error)
    }

    @Test
    fun `a recipe that would repeat one of one's own says so, and which it is`() = runTest(main) {
        val repeated = refusal("own-recipe-already-yours")
        coEvery { recipes.write(any()) } throws repeated
        val model = model()
        runCurrent()
        model.fill()

        model.save()
        runCurrent()

        assertSame(repeated, model.state.value.error)
        assertEquals("1a2ede14-e07f-41bb-8f0f-17d3f2f80b36", model.state.value.yours)
        assertFalse(model.state.value.saveRefusals.marked)

        // A change takes back what was said of the recipe as it was.
        model.edit { it.copy(title = "Oat pancakes with honey") }
        assertNull(model.state.value.error)
        assertNull(model.state.value.yours)
    }

    private companion object {
        const val OATS = "a0fb65dc-13be-471d-a952-7a43a14b19f1"
        const val MILK = "e37a806f-6e15-443b-982b-ee6957bc2729"
    }
}
