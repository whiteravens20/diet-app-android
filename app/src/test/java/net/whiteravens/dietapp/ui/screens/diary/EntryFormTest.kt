// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import io.mockk.every
import io.mockk.mockk
import net.whiteravens.dietapp.domain.DiaryAmount
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.NewDiaryEntry
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An entry as its windows are filled in: what is held to what the API takes, and what is written. */
class EntryFormTest {
    private val recipe = mockk<Recipe>().also {
        every { it.id } returns "r"
        every { it.title } returns "Oatmeal"
        every { it.nutritionPerServing } returns Nutrition(226.0, 10.0, 5.0, 30.0)
    }
    private val egg = DiaryIngredient("i", "Egg", listOf("g", "piece"), "piece", "g", 143.0)

    private fun meal(name: String = "Soup", calories: String = "300", servings: String = "1", protein: String = "", fat: String = "", carbs: String = "") =
        EntryForm(kind = EntryKind.MEAL, slot = "dinner", name = name, calories = calories, servings = servings, protein = protein, fat = fat, carbs = carbs)

    private fun outOfBounds(refusal: FieldRefusal) = EntryProblem.OutOfBounds(refusal)

    @Test
    fun `a meal of one's own needs a name and its calories alone`() {
        assertEquals(emptyMap<EntryField, EntryProblem>(), meal().problems())
        assertEquals(NewDiaryEntry.OwnMeal("dinner", "Soup", 1.0, 300.0, null, null, null), meal().entry())
    }

    @Test
    fun `a name of nothing but spaces is no name, and a name too long is said to be`() {
        assertEquals(outOfBounds(FieldRefusal.TooSmall(1.0, inclusive = true)), meal(name = "   ").problems()[EntryField.NAME])
        assertEquals(outOfBounds(FieldRefusal.TooBig(120.0, inclusive = true)), meal(name = "x".repeat(121)).problems()[EntryField.NAME])
        assertNull(meal(name = "x".repeat(120)).problems()[EntryField.NAME])
        assertNull(meal(name = " ").entry())
    }

    @Test
    fun `calories are asked for, and those typed are held to what the API takes`() {
        assertEquals(EntryProblem.NotANumber, meal(calories = "").problems()[EntryField.CALORIES])
        assertEquals(EntryProblem.NotANumber, meal(calories = "much").problems()[EntryField.CALORIES])
        assertEquals(outOfBounds(FieldRefusal.TooSmall(0.0, inclusive = true)), meal(calories = "-1").problems()[EntryField.CALORIES])
        assertEquals(outOfBounds(FieldRefusal.TooBig(5000.0, inclusive = true)), meal(calories = "5000.5").problems()[EntryField.CALORIES])
        assertNull(meal(calories = "0").problems()[EntryField.CALORIES])
        assertNull(meal(calories = "5000").problems()[EntryField.CALORIES])
    }

    @Test
    fun `a macronutrient left empty is one the API estimates, and one typed is held as the calories are`() {
        assertEquals(EntryProblem.NotANumber, meal(protein = "a lot").problems()[EntryField.PROTEIN])
        assertEquals(outOfBounds(FieldRefusal.TooBig(500.0, inclusive = true)), meal(fat = "501").problems()[EntryField.FAT])
        assertEquals(outOfBounds(FieldRefusal.TooBig(1000.0, inclusive = true)), meal(carbs = "1001").problems()[EntryField.CARBS])
        assertEquals(outOfBounds(FieldRefusal.TooSmall(0.0, inclusive = true)), meal(protein = "-5").problems()[EntryField.PROTEIN])
        assertEquals(NewDiaryEntry.OwnMeal("dinner", "Soup", 2.0, 300.0, 12.5, null, 40.0), meal(protein = "12,5", carbs = "40", servings = "2").entry())
    }

    @Test
    fun `servings are held to what the API takes, a decimal comma as well as a point`() {
        assertEquals(EntryProblem.NotANumber, meal(servings = "").problems()[EntryField.SERVINGS])
        assertEquals(outOfBounds(FieldRefusal.TooSmall(0.25, inclusive = true)), meal(servings = "0,1").problems()[EntryField.SERVINGS])
        assertEquals(outOfBounds(FieldRefusal.TooBig(20.0, inclusive = true)), meal(servings = "21").problems()[EntryField.SERVINGS])
        assertNull(meal(servings = "0,25").problems()[EntryField.SERVINGS])
        assertNull(meal(servings = "20").problems()[EntryField.SERVINGS])
    }

    @Test
    fun `what a meal or a recipe adds to the day is said, rounded as the API rounds it, once what is typed can be written`() {
        assertEquals(750, meal(calories = "300", servings = "2,5").adds())
        assertNull(meal(calories = "300", servings = "21").adds())
        assertNull(meal(calories = "much").adds())
        assertNull(meal(calories = "5001").adds())
        val form = EntryForm(slot = "lunch", recipe = recipe, servings = "1,5")
        assertEquals(339, form.adds())
        // An ingredient's figures go through the API's units.
        assertNull(EntryForm(kind = EntryKind.INGREDIENT, slot = "lunch", ingredient = egg, quantity = "2", unit = "piece").adds())
        // Nothing is picked.
        assertNull(EntryForm(slot = "lunch").adds())
    }

    @Test
    fun `a recipe is written once it is picked, in the servings typed`() {
        assertTrue(EntryForm(slot = "lunch").nothingPicked)
        assertNull(EntryForm(slot = "lunch").entry())
        assertEquals(NewDiaryEntry.OfRecipe("lunch", "r", "Oatmeal", 226.0, 1.5), EntryForm(slot = "lunch", recipe = recipe, servings = "1.5").entry())
    }

    @Test
    fun `an ingredient is written once it is picked, in an amount held to its unit`() {
        val form = EntryForm(kind = EntryKind.INGREDIENT, slot = "lunch", ingredient = egg, unit = "piece")
        assertEquals(EntryProblem.NotANumber, form.problems()[EntryField.QUANTITY])
        assertEquals(outOfBounds(FieldRefusal.TooSmall(0.0, inclusive = false)), form.copy(quantity = "0").problems()[EntryField.QUANTITY])
        assertEquals(outOfBounds(FieldRefusal.TooBig(50.0, inclusive = true)), form.copy(quantity = "51").problems()[EntryField.QUANTITY])
        assertEquals(outOfBounds(FieldRefusal.TooBig(5000.0, inclusive = true)), form.copy(unit = "g", quantity = "5001").problems()[EntryField.QUANTITY])
        assertNull(form.copy(unit = "g", quantity = "5000").problems()[EntryField.QUANTITY])
        assertEquals(NewDiaryEntry.OfIngredient("lunch", "i", "Egg", "piece", 2.5, "piece"), form.copy(quantity = "2,5").entry())
    }

    @Test
    fun `a window nothing was done in may still open on another kind, one something was done in stays`() {
        assertTrue(EntryForm(slot = "lunch").untouched)
        assertFalse(EntryForm(slot = "lunch", search = "oat").untouched)
        assertFalse(EntryForm(slot = "lunch", recipe = recipe).untouched)
        assertFalse(EntryForm(kind = EntryKind.MEAL, slot = "lunch", calories = "1").untouched)
    }

    private val written = DiaryEntry("e", "snack", "ingredient", "Milk", null, DiaryAmount(250.0, "g", 250.0, "g"), 48.0, null, false)
    private val serving = DiaryEntry("s", "lunch", "recipe", "Stew", 1.5, null, 339.0, null, false)

    @Test
    fun `an entry changed sends what changes, an amount left alone stays exactly what it was`() {
        val edit = EntryEdit(written)
        assertFalse(edit.changed)
        assertNull(edit.newQuantity)
        assertNull(edit.newServings)
        assertEquals("dinner", edit.copy(slot = "dinner").newSlot)
        assertNull(edit.copy(slot = "snack").newSlot)
        assertEquals(300.0, edit.copy(quantity = "300").newQuantity)
        assertTrue(edit.copy(quantity = "300").changed)
        assertEquals(2.0, EntryEdit(serving).copy(servings = "2").newServings)
    }

    @Test
    fun `what an entry's window holds is held to what the API takes, but for what is left alone`() {
        assertEquals(emptyMap<EntryField, EntryProblem>(), EntryEdit(written).problems())
        assertEquals(EntryProblem.NotANumber, EntryEdit(written).copy(quantity = "").problems()[EntryField.QUANTITY])
        assertEquals(outOfBounds(FieldRefusal.TooBig(5000.0, inclusive = true)), EntryEdit(written).copy(quantity = "9000").problems()[EntryField.QUANTITY])
        assertEquals(outOfBounds(FieldRefusal.TooBig(20.0, inclusive = true)), EntryEdit(serving).copy(servings = "30").problems()[EntryField.SERVINGS])
        // A meal of a plan copied here may hold less than an entry can be written with: left alone, it is not asked about.
        val small = serving.copy(servings = 0.13)
        assertEquals(emptyMap<EntryField, EntryProblem>(), EntryEdit(small).problems())
    }
}
