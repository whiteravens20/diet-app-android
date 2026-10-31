// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.domain.DiaryAmount
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryPlanMeal

/** Whether a change that waits to be sent is one a day of a diary shows: of its entries, or of the meals of its plan. */
internal fun QueuedChange.isOfADiary(): Boolean = when (this) {
    is QueuedChange.DiaryAdded, is QueuedChange.DiaryChanged, is QueuedChange.DiaryRemoved,
    is QueuedChange.Eaten, is QueuedChange.OwnMealAdded, is QueuedChange.MealRemoved,
    -> true
    else -> false
}

/**
 * The entries of the day of [date] of [profileId]'s diary, with its changes that wait to be sent over
 * those read, in the order they were made: one written after them, under its key, with what is known
 * of it there; one changed as the change says, its figures unknown where its amount is another until
 * the server has worked them out; one taken out left out.
 */
internal fun List<DiaryEntry>.entriesWithWaiting(profileId: String, date: String, waiting: List<QueuedChange>): List<DiaryEntry> = waiting.fold(this) { entries, change ->
    when (change) {
        is QueuedChange.DiaryAdded -> if (change.profileId == profileId && change.date == date) entries + change.entry() else entries
        is QueuedChange.DiaryChanged -> entries.map { if (it.id == change.id) it.changedBy(change) else it }
        is QueuedChange.DiaryRemoved -> entries.filterNot { it.id == change.id }
        else -> entries
    }
}

/**
 * The meals of the plan [planId] on the day of [date], with its changes that wait to be sent over them:
 * a meal marked eaten or not, a meal of one's own on the day under its key, beside the others or in the
 * place of the one it replaces, and a meal taken off the day left out.
 */
internal fun List<DiaryPlanMeal>.mealsWithWaiting(planId: String, date: String, waiting: List<QueuedChange>): List<DiaryPlanMeal> = waiting.fold(this) { meals, change ->
    when (change) {
        is QueuedChange.Eaten -> if (change.planId == planId) meals.map { if (it.id == change.mealId) it.copy(eaten = change.eaten) else it } else meals
        is QueuedChange.MealRemoved -> if (change.planId == planId) meals.filterNot { it.id == change.mealId } else meals
        is QueuedChange.OwnMealAdded -> if (change.planId == planId && change.date == date) {
            meals.filterNot { it.id == change.replaces } + DiaryPlanMeal(change.key, change.slot, change.name, null, false, change.calories * change.servings)
        } else {
            meals
        }
        else -> meals
    }
}

/**
 * How many of the changes that wait to be sent the sum of a day leaves out, as read from the server:
 * those of its entries, those read and those to come, and those of the meals of its plan.
 */
internal fun waitingOverDay(
    profileId: String,
    date: String,
    entries: List<DiaryEntry>,
    planId: String?,
    meals: List<DiaryPlanMeal>,
    waiting: List<QueuedChange>,
): Int = waiting.count { change ->
    when (change) {
        is QueuedChange.DiaryAdded -> change.profileId == profileId && change.date == date
        is QueuedChange.DiaryChanged -> entries.any { it.id == change.id }
        is QueuedChange.DiaryRemoved -> entries.any { it.id == change.id }
        is QueuedChange.Eaten -> change.planId == planId && meals.any { it.id == change.mealId }
        is QueuedChange.MealRemoved -> change.planId == planId && meals.any { it.id == change.mealId }
        is QueuedChange.OwnMealAdded -> change.planId == planId && change.date == date
        else -> false
    }
}

/** The entry written as it is shown until the server has it: what its name and its calories say. */
private fun QueuedChange.DiaryAdded.entry() = DiaryEntry(
    id = key,
    slot = slot,
    kind = when {
        recipeId != null -> "recipe"
        ingredientId != null -> "ingredient"
        else -> "meal"
    },
    name = name,
    servings = servings,
    amount = quantity?.let { DiaryAmount(it, unit.orEmpty(), it, if (unit == "piece") pieceName ?: "piece" else unit.orEmpty()) },
    calories = calories?.let { it * (servings ?: 1.0) },
    macros = null,
    estimated = false,
)

/** The entry as a change that waits to be sent makes it: another amount leaves its figures to the server. */
private fun DiaryEntry.changedBy(change: QueuedChange.DiaryChanged): DiaryEntry {
    val moved = copy(slot = change.slot ?: slot)
    if (change.servings == null && change.quantity == null) return moved
    return moved.copy(
        servings = change.servings ?: servings,
        amount = amount?.let { given -> change.quantity?.let { given.copy(quantity = it, shown = it) } ?: given },
        calories = null,
        macros = null,
    )
}
