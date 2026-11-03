// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.domain.Meal

/**
 * The entries of a day's diary for meals the day has none of, by where they stand among its meals: under
 * the index of the first meal that comes after them in the day, or under -1 after the last one; each list
 * in the order of the day.
 */
internal fun entriesApart(meals: List<Meal>, written: List<DiaryEntry>): Map<Int, List<DiaryEntry>> =
    written.filter { entry -> meals.none { it.slot == entry.slot } }
        .sortedBy { MEAL_TYPES.indexOf(it.slot) }
        .groupBy { entry -> meals.indexOfFirst { MEAL_TYPES.indexOf(it.slot) > MEAL_TYPES.indexOf(entry.slot) } }
