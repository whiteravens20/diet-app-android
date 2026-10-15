// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A meal of one's own as its form is filled in, held to what the API takes, as the web's form holds it. */
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
    fun `every field is needed, and each is held to the API's bounds`() {
        val refused = CustomMealForm(name = "   ", calories = "5001", protein = "-1", fat = "x", carbs = "", servings = "0.2")

        assertEquals(CustomMealField.entries.toSet(), refused.problems())
        assertNull(refused.meal())
        assertEquals(setOf(CustomMealField.NAME), filled.copy(name = "x".repeat(121)).problems())
        assertEquals(setOf(CustomMealField.SERVINGS), filled.copy(servings = "20.5").problems())
        assertEquals(emptySet<CustomMealField>(), filled.copy(calories = "0", servings = "0.25").problems())
    }
}
