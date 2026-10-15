// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.Nutrition
import kotlin.math.roundToInt

/** The bounds the API holds a meal of one's own to: what one serving holds, and how many servings it is. */
object CustomMealBounds {
    const val NAME_LENGTH = 120
    val CALORIES = 0.0..5000.0
    val PROTEIN = 0.0..500.0
    val FAT = 0.0..500.0
    val CARBS = 0.0..1000.0
    val SERVINGS = 0.25..20.0
}

/** A field of a meal of one's own that holds what the API would refuse. */
enum class CustomMealField { NAME, CALORIES, PROTEIN, FAT, CARBS, SERVINGS }

/**
 * A meal of the user's own as its form is filled in. Such a meal has no ingredients, so what it
 * holds is what the user says: the numbers of one serving, and how many servings the meal is.
 */
data class CustomMealForm(
    val name: String = "",
    val slot: String = "lunch",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val servings: String = "1",
) {
    /** The fields that hold what the API would refuse; every field is needed. */
    fun problems(): Set<CustomMealField> = buildSet {
        if (name.isBlank() || name.trim().length > CustomMealBounds.NAME_LENGTH) add(CustomMealField.NAME)
        if (!calories.within(CustomMealBounds.CALORIES)) add(CustomMealField.CALORIES)
        if (!protein.within(CustomMealBounds.PROTEIN)) add(CustomMealField.PROTEIN)
        if (!fat.within(CustomMealBounds.FAT)) add(CustomMealField.FAT)
        if (!carbs.within(CustomMealBounds.CARBS)) add(CustomMealField.CARBS)
        if (!servings.within(CustomMealBounds.SERVINGS)) add(CustomMealField.SERVINGS)
    }

    /**
     * What the meal adds to its day, one serving times the servings rounded as the API rounds it,
     * once both are filled in; it does not wait for a name.
     */
    fun adds(): Nutrition? {
        if (problems().any { it != CustomMealField.NAME }) return null
        val times = servings.number()!!
        fun of(field: String) = (field.number()!! * times).roundToInt().toDouble()
        return Nutrition(calories = of(calories), protein = of(protein), fat = of(fat), carbs = of(carbs))
    }

    /** The meal to add, or null while a field holds what the API would refuse. */
    fun meal(): CustomMeal? = if (problems().isNotEmpty()) {
        null
    } else {
        CustomMeal(
            name = name.trim(),
            slot = slot,
            perServing = Nutrition(calories.number()!!, protein.number()!!, fat.number()!!, carbs.number()!!),
            servings = servings.number()!!,
        )
    }
}

/** A number as a person types it, with a point or a comma. */
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun String.within(range: ClosedFloatingPointRange<Double>): Boolean = number()?.let { it in range } == true
