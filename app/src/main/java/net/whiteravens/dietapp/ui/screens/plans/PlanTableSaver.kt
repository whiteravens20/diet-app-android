// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.ui.common.CsvDialect
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.common.csvOf
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import java.io.IOException

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

/**
 * Saving a plan as a table for a spreadsheet: the function given back asks where, in the platform's
 * own window, and the table of [plan] as it is by then is written there in the dialect of the app's
 * language. A file that cannot be written is said in a short note.
 */
@Composable
fun rememberPlanTableSaver(plan: MealPlan?, words: PlanWords): (MealPlan) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialect = CsvDialect.of(LocalConfiguration.current.locales[0].language)
    val failed = stringResource(R.string.plans_export_failed)
    val current by rememberUpdatedState(plan)
    val table by rememberUpdatedState(words)
    val where = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val shown = current
        if (uri == null || shown == null) return@rememberLauncherForActivityResult
        val text = csvOf(planTable(shown, table), dialect)
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                attempt { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("No stream for $uri") }
            }
            if (written.isFailure) Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
        }
    }
    return { shown -> where.launch("${exportName(shown)}.csv") }
}
