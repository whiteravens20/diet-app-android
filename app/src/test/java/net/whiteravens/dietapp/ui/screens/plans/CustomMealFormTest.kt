// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.testing.refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A meal of one's own as its form is filled in: numbers where numbers are asked for, and what the API takes of them is the API's to say. */
class CustomMealFormTest {
    private val filled = CustomMealForm(name = " Mum's lasagne ", slot = "dinner", calories = "450", protein = "22,5", fat = "18", carbs = "48", servings = "1.5")

    @Test
    fun `a filled form is the meal of one serving and its servings, named without the spaces around it`() {
        assertEquals(emptySet<CustomMealField>(), filled.problems())
        assertEquals(CustomMeal("Mum's lasagne", "dinner", Nutrition(450.0, 22.5, 18.0, 48.0), 1.5), filled.meal())
    }

    @Test
    fun `what the meal adds to the day is one serving times the servings, rounded, and it does not wait for a name`() {
        assertEquals(Nutrition(675.0, 34.0, 27.0, 72.0), filled.copy(name = "").adds())
        assertNull(filled.copy(servings = "").adds())
        assertNull(filled.copy(fat = "").adds())
    }

    @Test
    fun `each number is needed and has to be one, and what the API takes of them or of the name is left to the API`() {
        val unread = CustomMealForm(name = "Soup", calories = "x", protein = "", fat = "NaN", carbs = "40", servings = "two")

        assertEquals(setOf(CustomMealField.CALORIES, CustomMealField.PROTEIN, CustomMealField.FAT, CustomMealField.SERVINGS), unread.problems())
        assertNull(unread.meal())
        // Sent as typed, for the API to refuse with its reasons: a name of spaces, numbers past its bounds.
        val outOfBounds = CustomMealForm(name = "   ", calories = "6000", protein = "600", fat = "600", carbs = "1200", servings = "30")
        assertEquals(emptySet<CustomMealField>(), outOfBounds.problems())
        assertEquals(CustomMeal("", "lunch", Nutrition(6000.0, 600.0, 600.0, 1200.0), 30.0), outOfBounds.meal())
    }

    @Test
    fun `each field is the one the API names when it refuses what it holds`() {
        // The API's answer to a name too long and every number past its bounds.
        val refused = refusal("custom-meal-refused").fieldRefusals.keys

        assertEquals(CustomMealField.entries.map { it.path }, refused.toList())
    }
}
