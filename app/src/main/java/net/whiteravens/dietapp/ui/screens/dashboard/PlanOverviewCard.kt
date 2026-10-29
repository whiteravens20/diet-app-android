// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.OverviewDay
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Share
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.formatPercent
import net.whiteravens.dietapp.ui.components.AppCard
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What the plan a profile is living by comes to, as the web's card says it: what a day of it holds
 * against what it aims at, how much of what was due is eaten, how much of it fits the diet and is
 * made of favourites, how far the shopping is, and its days side by side. Read-only: every figure is
 * the API's. A profile with no plan is offered to make one ([onNewPlan]); a plan opens on [onOpen].
 */
@Composable
internal fun PlanOverviewCard(overview: PlanOverview?, failed: Throwable?, onOpen: (planId: String) -> Unit, onNewPlan: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                failed != null -> {
                    Text(stringResource(R.string.overview_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Text(errorMessage(failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                overview == null -> {
                    Text(stringResource(R.string.overview_none), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.overview_none_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onNewPlan) { Text(stringResource(R.string.overview_none_cta)) }
                }
                else -> Overview(overview, onOpen = { onOpen(overview.planId) })
            }
        }
    }
}

@Composable
private fun Overview(overview: PlanOverview, onOpen: () -> Unit) {
    val first = LocalDate.parse(overview.startDate)
    val last = first.plusDays(overview.durationDays - 1L)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(
                when (overview.status) {
                    "upcoming" -> R.string.overview_title_upcoming
                    "past" -> R.string.overview_title_past
                    else -> R.string.overview_title_active
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.overview_span, formatDates(first.toString(), last.toString()), dietTypeLabel(overview.dietType)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.overview_open)) }

    val diet = dietTypeLabel(overview.dietType)
    val shopping = overview.shoppingLists > 0
    Figures(
        listOf(
            Figure(
                stringResource(R.string.overview_energy),
                stringResource(R.string.overview_energy_value, formatNumber(overview.energy.roundToInt())),
                stringResource(R.string.overview_energy_hint, formatNumber(overview.energyTarget.roundToInt())),
            ),
            // Nothing was due before the first day of a plan: there is no share to name yet.
            if (overview.eaten.of == 0) Figure(stringResource(R.string.overview_eaten), "—", stringResource(R.string.overview_eaten_not_yet_hint))
            else Figure(stringResource(R.string.overview_eaten), formatPercent(overview.eaten.part), stringResource(R.string.overview_eaten_hint, formatNumber(overview.eaten.meals), meals(overview.eaten))),
            Figure(stringResource(R.string.overview_on_diet), formatPercent(overview.onDiet.part), stringResource(R.string.overview_on_diet_hint, formatNumber(overview.onDiet.meals), meals(overview.onDiet), diet)),
            Figure(stringResource(R.string.overview_favourites), formatPercent(overview.favourites.part), stringResource(R.string.overview_favourites_hint, formatNumber(overview.favourites.meals), meals(overview.favourites))),
            Figure(stringResource(R.string.overview_reuse), formatPercent(overview.ingredientReuse), stringResource(R.string.overview_reuse_hint)),
            if (!shopping) Figure(stringResource(R.string.overview_shopping), "—", stringResource(R.string.overview_shopping_none_hint))
            else Figure(
                stringResource(R.string.overview_shopping),
                formatPercent(if (overview.shoppingEntries == 0) 0.0 else overview.shoppingTicked.toDouble() / overview.shoppingEntries),
                stringResource(
                    R.string.overview_shopping_hint,
                    formatNumber(overview.shoppingTicked),
                    pluralStringResource(R.plurals.overview_entries, overview.shoppingEntries, overview.shoppingEntries),
                ),
            ),
        ),
    )

    Text(stringResource(R.string.overview_macros), style = MaterialTheme.typography.titleSmall)
    listOf(
        Triple(R.string.macro_protein, overview.macros.protein, overview.macrosTarget.protein),
        Triple(R.string.macro_fat, overview.macros.fat, overview.macrosTarget.fat),
        Triple(R.string.macro_carbs, overview.macros.carbs, overview.macrosTarget.carbs),
    ).forEach { (name, planned, target) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(stringResource(name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.overview_macro_value, formatNumber(planned.roundToInt()), formatNumber(target.roundToInt())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The bar is full at the target; what the plan holds beyond it does not make it longer.
            LinearProgressIndicator(
                progress = { if (target <= 0.0) 0f else (planned / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Text(stringResource(R.string.overview_days), style = MaterialTheme.typography.titleSmall)
    DaysChart(overview.days)
}

/** A figure of the card: what it is, what it comes to, and how to read it. */
private data class Figure(val label: String, val value: String, val hint: String)

/** The figures two to a row, each in a box of its own, the two boxes of a row as tall as each other. */
@Composable
private fun Figures(figures: List<Figure>) {
    figures.chunked(2).forEach { pair ->
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { figure ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(figure.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(figure.value, style = MaterialTheme.typography.titleLarge)
                        Text(figure.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** How many meals a share is of, counted in the language's own forms. */
@Composable
private fun meals(share: Share): String = pluralStringResource(R.plurals.overview_meals, share.of, share.of)

/**
 * The days of a plan side by side: what each holds as a bar, and what each aims at as a dashed line
 * across it; a skipped day is a gap. The same days are said in words to whoever does not see it.
 */
@Composable
private fun DaysChart(days: List<OverviewDay>) {
    if (days.isEmpty()) return
    val bar = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val base = MaterialTheme.colorScheme.outlineVariant
    val skipped = stringResource(R.string.plans_skipped)
    val said = days.map { day ->
        "${formatDayDate(day.date)}: " +
            if (day.skipped) skipped else stringResource(R.string.overview_day_of, formatNumber(day.calories.roundToInt()), formatNumber(day.target.roundToInt()))
    }.joinToString("; ")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = said },
    ) {
        val top = days.filterNot { it.skipped }.maxOfOrNull { maxOf(it.calories, it.target) }?.times(1.1) ?: return@Canvas
        if (top <= 0.0) return@Canvas
        val slot = size.width / days.size
        val width = (slot * 0.6f).coerceAtMost(36.dp.toPx())
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        days.forEachIndexed { index, day ->
            if (day.skipped) return@forEachIndexed
            val left = index * slot
            val height = (day.calories / top * size.height).toFloat()
            drawRoundRect(bar, topLeft = Offset(left + (slot - width) / 2, size.height - height), size = Size(width, height), cornerRadius = CornerRadius(3.dp.toPx()))
            val y = (size.height - day.target / top * size.height).toFloat()
            drawLine(line, Offset(left, y), Offset(left + slot, y), strokeWidth = 2.dp.toPx(), pathEffect = dash)
        }
    }
    Row {
        Text(formatDayDate(days.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (days.size > 1) Text(formatDayDate(days.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Key(bar, stringResource(R.string.overview_series_planned), dashed = false)
        Key(line, stringResource(R.string.overview_series_target), dashed = true)
    }
}

/** A key of the chart: what a colour, or a dashed line, stands for. */
@Composable
private fun Key(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 14.dp, height = 10.dp)) {
            Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
                if (dashed) drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())))
                else drawRect(color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
