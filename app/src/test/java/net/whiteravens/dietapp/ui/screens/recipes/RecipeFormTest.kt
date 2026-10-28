// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import io.mockk.mockk
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.OwnRecipe
import net.whiteravens.dietapp.domain.OwnRecipeLine
import net.whiteravens.dietapp.domain.RecipeToEdit
import net.whiteravens.dietapp.testing.refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recipe's form: what it sends, what it asks a serving about, and where the API's refusals land on it. */
class RecipeFormTest {
    private val oats = LineIngredient(OATS, "Rolled oats", listOf("g"), "piece")
    private val milk = LineIngredient(MILK, "Whole milk", listOf("ml", "g"), "piece")
    private val egg = LineIngredient(EGG, "Large egg", listOf("g", "piece"), "piece")

    /** Reads the details of a refusal as the app reads them. */
    private val recipes = RecipeRepository(mockk(), mockk(), AppModule.json())

    /** A form filled in, its lines and steps under keys that are not their places. */
    private val filled = RecipeForm(
        title = "Oat pancakes",
        description = "Soft pancakes of oats and milk.",
        mealTypes = listOf("breakfast"),
        servings = "2",
        lines = listOf(FormLine(3, oats, "80"), FormLine(4, milk, "300,5", note = "warm"), FormLine(6, egg, "2", "piece", note = " ")),
        steps = listOf(FormStep(10, ""), FormStep(11, "Fry small pancakes on a dry pan.")),
        nextKey = 12,
    )

    @Test
    fun `the recipe sent holds every step as it is typed, the amounts with a comma or a point, and a note left empty as none`() {
        val sent = filled.recipe()!!

        assertEquals(listOf("", "Fry small pancakes on a dry pan."), sent.steps)
        assertEquals(
            listOf(OwnRecipeLine(OATS, 80.0, "g"), OwnRecipeLine(MILK, 300.5, "ml", "warm"), OwnRecipeLine(EGG, 2.0, "piece")),
            sent.lines,
        )
        assertEquals(listOf(2, 10, 0), listOf(sent.servings, sent.prepMinutes, sent.cookMinutes))
    }

    @Test
    fun `a field that holds no number where one is needed sends nothing, and says which it is`() {
        val typed = filled.copy(servings = "2.5", cookMinutes = "", lines = filled.lines.map { if (it.key == 4) it.copy(quantity = "a cup") else it })

        assertNull(typed.recipe())
        assertEquals(setOf(RecipeField.SERVINGS, RecipeField.COOK), typed.notWhole())
        assertEquals(setOf(4), typed.notNumbers())
    }

    @Test
    fun `a serving is asked about once there is a line, the servings are whole and every amount is a number, the notes left out`() {
        assertNull(RecipeForm().previewAsk())
        assertNull(filled.copy(servings = "two").previewAsk())
        assertNull(filled.withLine(oats).previewAsk())

        val ask = filled.previewAsk()!!

        assertEquals(2, ask.servings)
        assertEquals(listOf(null, null, null), ask.lines.map { it.note })
        // A note says nothing of a serving: changing it asks nothing new.
        assertEquals(ask, filled.copy(lines = filled.lines.map { it.copy(note = "cold") }).previewAsk())
        // What the API is to say, the form does not: a serving of nothing is asked about.
        assertEquals(0, filled.copy(servings = "0").previewAsk()!!.servings)
    }

    @Test
    fun `a line is added in the unit its ingredient is offered in first, and a step is moved within the steps only`() {
        val added = filled.withLine(egg).withStep()

        assertEquals(FormLine(12, egg, "", "g", ""), added.lines.last())
        assertEquals(FormStep(13), added.steps.last())
        assertEquals(listOf(11, 10, 13), added.withStepMoved(11, -1).steps.map { it.key })
        assertEquals(added, added.withStepMoved(10, -1))
        assertEquals(added, added.withStepMoved(13, 1))
    }

    @Test
    fun `the bounds the API names mark the form's fields, and a line's amount on the line it was sent as`() {
        val preview = RecipeRefusals.ofBounds(refusal("own-recipe-preview-refused").fieldRefusals, filled)
        val saved = RecipeRefusals.ofBounds(refusal("own-recipe-refused").fieldRefusals, filled)

        assertEquals(mapOf(RecipeField.SERVINGS to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(12.0, true))), preview.fields)
        assertEquals(mapOf(3 to mapOf(LinePart.QUANTITY to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(18000.0, true)))), preview.lines)
        assertEquals(
            mapOf(
                RecipeField.SERVINGS to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(12.0, true)),
                RecipeField.STEPS to RecipeRefusal.OutOfBounds(FieldRefusal.TooSmall(1.0, true)),
                RecipeField.PREP to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(480.0, true)),
            ),
            saved.fields,
        )
        assertTrue(preview.elsewhere.isEmpty() && saved.elsewhere.isEmpty())
    }

    @Test
    fun `the rules a recipe would break mark the field of their place, a step counted among all those sent`() {
        val form = filled.copy(title = " ", steps = listOf(FormStep(10, ""), FormStep(11, "Fry <b>small</b> pancakes on a dry pan.")))

        val broken = RecipeRefusals.ofRules(recipes.problemsOf(refusal("own-recipe-invalid-places"))!!, form)
        val noSteps = RecipeRefusals.ofRules(recipes.problemsOf(refusal("own-recipe-no-steps"))!!, form)
        val markup = RecipeRefusals.ofRules(recipes.problemsOf(refusal("own-recipe-invalid"))!!, form)

        assertEquals(mapOf(RecipeField.TITLE to RecipeRefusal.AgainstRule("missing-locale")), broken.fields)
        assertEquals(mapOf(11 to RecipeRefusal.AgainstRule("markup")), broken.steps)
        assertEquals(
            mapOf(
                3 to mapOf(LinePart.QUANTITY to RecipeRefusal.AgainstRule("quantity-too-large")),
                4 to mapOf(LinePart.NOTE to RecipeRefusal.AgainstRule("markup")),
            ),
            broken.lines,
        )
        assertTrue(broken.elsewhere.isEmpty())
        assertEquals(mapOf(RecipeField.STEPS to RecipeRefusal.AgainstRule("missing-locale")), noSteps.fields)
        assertEquals(mapOf(RecipeField.TITLE to RecipeRefusal.AgainstRule("markup")), markup.fields)
    }

    @Test
    fun `a field changed since it was refused is no longer refused, an amount also with its unit or the servings, and a step moved keeps it`() {
        val refused = RecipeRefusals(
            fields = mapOf(RecipeField.TITLE to RecipeRefusal.AgainstRule("markup"), RecipeField.SERVINGS to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(12.0, true))),
            lines = mapOf(
                3 to mapOf(LinePart.QUANTITY to RecipeRefusal.AgainstRule("quantity-too-large")),
                4 to mapOf(LinePart.NOTE to RecipeRefusal.AgainstRule("markup"), LinePart.QUANTITY to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(18000.0, true))),
            ),
            steps = mapOf(11 to RecipeRefusal.AgainstRule("markup")),
        )

        val moved = refused.after(filled, filled.withStepMoved(11, -1))
        val retitled = refused.after(filled, filled.copy(title = "Pancakes"))
        val unit = refused.after(filled, filled.copy(lines = filled.lines.map { if (it.key == 4) it.copy(unit = "g") else it }))
        val servings = refused.after(filled, filled.copy(servings = "3"))
        val removed = refused.after(filled, filled.copy(lines = filled.lines.filterNot { it.key == 3 }))

        assertEquals(refused, moved)
        assertEquals(setOf(RecipeField.SERVINGS), retitled.fields.keys)
        assertEquals(mapOf(LinePart.NOTE to RecipeRefusal.AgainstRule("markup")), unit.lines[4])
        assertEquals(setOf(RecipeField.TITLE), servings.fields.keys)
        assertEquals(mapOf(4 to mapOf(LinePart.NOTE to RecipeRefusal.AgainstRule("markup"))), servings.lines)
        assertEquals(setOf(4), removed.lines.keys)
    }

    @Test
    fun `what a serving was last asked about is said over what saving was, field by field`() {
        val saved = RecipeRefusals(
            fields = mapOf(RecipeField.TITLE to RecipeRefusal.AgainstRule("markup")),
            lines = mapOf(3 to mapOf(LinePart.QUANTITY to RecipeRefusal.AgainstRule("quantity-too-large"), LinePart.NOTE to RecipeRefusal.AgainstRule("markup"))),
        )
        val asked = RecipeRefusals(lines = mapOf(3 to mapOf(LinePart.QUANTITY to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(18000.0, true)))))

        val shown = asked.over(saved)

        assertEquals(saved.fields, shown.fields)
        assertEquals(
            mapOf(3 to mapOf(LinePart.QUANTITY to RecipeRefusal.OutOfBounds(FieldRefusal.TooBig(18000.0, true)), LinePart.NOTE to RecipeRefusal.AgainstRule("markup"))),
            shown.lines,
        )
    }

    @Test
    fun `a recipe of one's own starts its form as it is kept, each line with its ingredient`() {
        val kept = OwnRecipe(
            title = "Oat pancakes",
            description = "Soft pancakes of oats, milk and eggs.",
            steps = listOf("Blend.", "Fry."),
            servings = 2,
            mealTypes = listOf("breakfast"),
            prepMinutes = 10,
            cookMinutes = 15,
            difficulty = "easy",
            cuisine = "polish",
            lines = listOf(OwnRecipeLine(OATS, 80.0, "g"), OwnRecipeLine(MILK, 212.4, "ml", "warm")),
        )

        val form = RecipeForm.of(RecipeToEdit(kept, listOf(oats, milk)))

        assertEquals(listOf(FormLine(0, oats, "80", "g", ""), FormLine(1, milk, "212.4", "ml", "warm")), form.lines)
        assertEquals(listOf(FormStep(2, "Blend."), FormStep(3, "Fry.")), form.steps)
        assertEquals(4, form.nextKey)
        assertEquals(kept, form.recipe())
    }

    private companion object {
        const val OATS = "a0fb65dc-13be-471d-a952-7a43a14b19f1"
        const val MILK = "e37a806f-6e15-443b-982b-ee6957bc2729"
        const val EGG = "e8ca52e7-16a4-41fa-b861-966769b3944e"
    }
}
