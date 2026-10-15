// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.suspendCancellableCoroutine
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import java.text.FieldPosition
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Hands a plan to the platform's print service, which prints it or keeps it as a PDF, as the plan
 * is when this is shown. The sheet is laid out by a web view that runs no script and reaches
 * nothing outside the page it is given; [onDone] is told once the print service has the plan.
 */
@Composable
fun PlanPrinter(plan: MealPlan, words: PlanWords, onDone: () -> Unit) {
    val sheet = planSheet(plan, words)
    val name = exportName(plan)
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        context.activity()?.let { printPage(it, name, sheet.html(language)) }
        done()
    }
}

/** A plan as a sheet of paper, in the words and the formats of the app's language. */
@Composable
private fun planSheet(plan: MealPlan, words: PlanWords): PlanSheet {
    val average = plan.averageDailyNutrition
    return PlanSheet(
        title = stringResource(R.string.plans_export_sheet_title, formatDates(plan.startDate, lastDayOf(plan)), dietTypeLabel(plan.dietType)),
        average = stringResource(
            R.string.plans_export_sheet_average,
            formatNumber(average.calories.roundToInt()),
            formatNumber(average.protein.roundToInt()),
            formatNumber(average.fat.roundToInt()),
            formatNumber(average.carbs.roundToInt()),
        ),
        head = words.head.subList(1, 8),
        days = plan.days.map { day ->
            SheetDay(
                name = formatDayDate(day.date),
                note = if (day.skipped) words.skipped
                else stringResource(R.string.plans_export_sheet_day, formatNumber(day.nutrition.calories.roundToInt()), formatNumber(day.calorieTarget.roundToInt())),
                meals = if (day.skipped) emptyList() else day.meals.map { meal ->
                    SheetMeal(
                        meal = words.meal(meal.slot),
                        dish = dishOf(meal, words),
                        lines = if (meal.lines.isEmpty()) "" else formatList(meal.lines.map { line -> "${line.name} ${amountLabel(line.quantity, line.unit)}" }),
                        figures = listOf(
                            formatNumber(servingsOf(meal), fractionDigits = 2),
                            formatNumber(meal.nutrition.calories.roundToInt()),
                            formatNumber(meal.nutrition.protein.roundToInt()),
                            formatNumber(meal.nutrition.fat.roundToInt()),
                            formatNumber(meal.nutrition.carbs.roundToInt()),
                        ),
                    )
                },
            )
        },
    )
}

/** The days from [from] to [to] (yyyy-MM-dd) as one span, the way the language writes one: `15–21 Oct 2026`. */
@Composable
private fun formatDates(from: String, to: String): String {
    val format = DateIntervalFormat.getInstance(DateFormat.YEAR_ABBR_MONTH_DAY, LocalConfiguration.current.locales[0])
    format.timeZone = TimeZone.GMT_ZONE
    fun midnight(day: String) = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return format.format(DateInterval(midnight(from), midnight(to)), StringBuffer(), FieldPosition(0)).toString()
}

/** Lays [html] out as a page and gives it to the print service under [name], which the PDF it may keep is called by. */
private suspend fun printPage(activity: Activity, name: String, html: String) {
    val page = WebView(activity)
    page.settings.apply {
        javaScriptEnabled = false
        blockNetworkLoads = true
        allowFileAccess = false
        allowContentAccess = false
    }
    suspendCancellableCoroutine { loaded ->
        page.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (loaded.isActive) loaded.resume(Unit)
            }
        }
        page.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    // The print service lays the page out once the job is made: the page is held until then.
    activity.getSystemService(PrintManager::class.java).print(name, page.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
}

/** The activity a screen is shown in: only an activity can start a print job. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
