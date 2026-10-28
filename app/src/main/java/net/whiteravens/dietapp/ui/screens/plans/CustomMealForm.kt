// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.Nutrition
import kotlin.math.roundToInt

/** A field of a meal of one's own, by the path the API names it by when it refuses what it holds. */
enum class CustomMealField(val path: String) {
    NAME("name"),
    CALORIES("nutrition.calories"),
    PROTEIN("nutrition.protein"),
    FAT("nutrition.fat"),
    CARBS("nutrition.carbs"),
    SERVINGS("servings"),
}

/**
 * A meal of the user's own as its form is filled in. Such a meal has no ingredients, so what it
 * holds is what the user says: the numbers of one serving, and how many servings the meal is. What
 * the API takes of them is the API's to say.
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
    /** The fields that hold no number where one is asked for; each number is needed. */
    fun problems(): Set<CustomMealField> = buildSet {
        if (calories.number() == null) add(CustomMealField.CALORIES)
        if (protein.number() == null) add(CustomMealField.PROTEIN)
        if (fat.number() == null) add(CustomMealField.FAT)
        if (carbs.number() == null) add(CustomMealField.CARBS)
        if (servings.number() == null) add(CustomMealField.SERVINGS)
    }

    /**
     * What the meal adds to its day, one serving times the servings rounded as the API rounds it,
     * once both are filled in; it does not wait for a name.
     */
    fun adds(): Nutrition? {
        if (problems().isNotEmpty()) return null
        val times = servings.number()!!
        fun of(field: String) = (field.number()!! * times).roundToInt().toDouble()
        return Nutrition(calories = of(calories), protein = of(protein), fat = of(fat), carbs = of(carbs))
    }

    /** The meal to add, or null while a field holds no number where one is asked for. */
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
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
