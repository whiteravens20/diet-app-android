// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.ui.common.mealTypeLabel

/** The words a plan is written out with, in the language the app is shown in. */
@Composable
fun planWords(): PlanWords = PlanWords(
    head = listOf(
        stringResource(R.string.plans_export_date),
        stringResource(R.string.plans_export_meal),
        stringResource(R.string.plans_export_dish),
        stringResource(R.string.plans_export_servings),
        stringResource(R.string.plans_export_energy),
        stringResource(R.string.plans_export_protein),
        stringResource(R.string.plans_export_fat),
        stringResource(R.string.plans_export_carbs),
        stringResource(R.string.plans_export_eaten),
    ),
    meals = MealSlots.BY_COUNT.getValue(5).associateWith { mealTypeLabel(it) },
    ownMeal = stringResource(R.string.plans_custom_meal),
    yes = stringResource(R.string.common_yes),
    no = stringResource(R.string.common_no),
    skipped = stringResource(R.string.plans_skipped),
)
