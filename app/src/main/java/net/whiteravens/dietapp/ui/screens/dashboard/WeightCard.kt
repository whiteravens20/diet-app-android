// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.WeightPoint
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.common.weightLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.screens.dashboard.DashboardViewModel.WeightProblem
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The weights a profile logged, as the web's card keeps them: today's weight typed in the account's
 * unit and logged, the last one taken back once it is confirmed, and ninety days of them as a chart
 * with the mean of each seven days and a line at the weight the profile works towards, or at the one
 * it registered with while it names none. The API keeps kilograms, and says the days and their means.
 * The last weight [waiting] to be sent says so; the chart has it once it is sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeightCard(
    stats: DashboardViewModel.ProfileStats,
    waiting: Boolean,
    unit: WeightUnit,
    log: DashboardViewModel.WeightLog,
    onType: (String) -> Unit,
    onLog: () -> Unit,
    onRemoveLatest: () -> Unit,
) {
    val profile = stats.profile
    val zone = ZoneId.systemDefault()
    // The API answers the newest first, so the first is the one taken back.
    val latest = stats.weights?.firstOrNull()
    val latestWeight = latest?.let { weightLabel(it.kg, unit.code) }.orEmpty()
    val latestDate = latest?.let { formatDate(it.recordedAt.atZone(zone).toLocalDate().toString()) }.orEmpty()
    var confirming by rememberSaveable { mutableStateOf(false) }

    if (confirming && latest != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(stringResource(R.string.weight_remove_latest_confirm, latestWeight, latestDate)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemoveLatest()
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.titleMedium)
            val registered = weightLabel(profile.weightKg, unit.code)
            FormField(
                value = log.typed,
                onValueChange = onType,
                label = stringResource(R.string.weight_today, unit.code),
                placeholder = formatNumber(unit.shownToOne(profile.weightKg), 1),
                hint = profile.targetWeightKg?.let { stringResource(R.string.weight_target_hint, registered, weightLabel(it, unit.code)) }
                    ?: stringResource(R.string.weight_reference_hint, registered),
                problem = when (val problem = log.problem) {
                    WeightProblem.NotANumber -> stringResource(R.string.refusal_not_a_number)
                    is WeightProblem.Refused -> refusalText(problem.refusal, unit.refusalBound)
                    else -> null
                },
                keyboardType = KeyboardType.Decimal,
            )
            when (val problem = log.problem) {
                is WeightProblem.LogFailed -> Failure("${stringResource(R.string.weight_log_failed)} ${errorMessage(problem.error)}")
                is WeightProblem.RemoveFailed -> Failure("${stringResource(R.string.weight_remove_failed)} ${errorMessage(problem.error)}")
                else -> Unit
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onLog, enabled = !log.logging && log.typed.isNotBlank()) {
                    Text(stringResource(if (log.logging) R.string.weight_logging else R.string.weight_log))
                }
                if (latest != null) {
                    TextButton(onClick = { confirming = true }, enabled = !log.removing) {
                        Icon(AppIcons.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.weight_remove_latest), modifier = Modifier.padding(start = 4.dp))
                        Text(
                            "(${stringResource(R.string.weight_remove_latest_label, latestWeight, latestDate)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (waiting) WaitingMark()
                }
            }

            val days = stats.days
            when {
                stats.weightsError != null -> {
                    Text(stringResource(R.string.weight_read_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Text(errorMessage(stats.weightsError), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                days.isNullOrEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.weight_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val target = profile.targetWeightKg
                    WeightChart(
                        points = days,
                        lineKg = target ?: profile.weightKg,
                        lineLabel = stringResource(if (target != null) R.string.weight_target_label else R.string.weight_reference_label),
                        unit = unit,
                    )
                }
            }
        }
    }
}

@Composable
private fun Failure(said: String) = Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

/**
 * The weights in the account's unit, a day to a step: what was logged as a thin line with dots, the
 * mean of the seven days that end with each day as a thicker one, and a dashed line across at
 * [lineKg]. The same days are said in words to whoever does not see the chart.
 */
@Composable
private fun WeightChart(points: List<WeightPoint>, lineKg: Double, lineLabel: String, unit: WeightUnit) {
    val logged = MaterialTheme.colorScheme.onSurfaceVariant
    val mean = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.tertiary
    val edge = MaterialTheme.colorScheme.outlineVariant
    val values = points.flatMap { listOf(unit.shown(it.kg), unit.shown(it.mean)) } + unit.shown(lineKg)
    // Room above and below the weights, whole units at both ends, so that a steady weight is not drawn along an edge.
    val room = maxOf((values.max() - values.min()) * 0.1, 0.5)
    val bottom = floor(values.min() - room)
    val top = ceil(values.max() + room)
    val said = points.map { point ->
        stringResource(R.string.weight_chart_point, formatDate(point.date.toString()), weightLabel(point.kg, unit.code), weightLabel(point.mean, unit.code))
    }.joinToString("; ")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(CHART_HEIGHT), verticalArrangement = Arrangement.SpaceBetween) {
            Text(formatNumber(top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatNumber(bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(CHART_HEIGHT)
                .semantics { contentDescription = said },
        ) {
            val inset = 4.dp.toPx()
            val width = size.width - 2 * inset
            fun x(index: Int) = inset + if (points.size == 1) width / 2 else width * index / (points.size - 1)
            val span = top - bottom
            fun y(kg: Double): Float {
                val fromTop = top - unit.shown(kg)
                return (fromTop / span * size.height).toFloat()
            }
            drawLine(edge, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            drawLine(edge, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(line, Offset(0f, y(lineKg)), Offset(size.width, y(lineKg)), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            fun through(of: (WeightPoint) -> Double) = Path().apply {
                points.forEachIndexed { index, point -> if (index == 0) moveTo(x(index), y(of(point))) else lineTo(x(index), y(of(point))) }
            }
            drawPath(through { it.kg }, logged, style = Stroke(width = 1.dp.toPx()))
            points.forEachIndexed { index, point -> drawCircle(logged, radius = 2.dp.toPx(), center = Offset(x(index), y(point.kg))) }
            drawPath(through { it.mean }, mean, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
    Row {
        Text(formatDate(points.first().date.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (points.size > 1) Text(formatDate(points.last().date.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Key(logged, stringResource(R.string.weight_series_entry), thickness = 1, dashed = false)
        Key(mean, stringResource(R.string.weight_series_mean), thickness = 3, dashed = false)
        Key(line, lineLabel, thickness = 2, dashed = true)
    }
}

/** A key of the chart: the line a colour, a thickness or a dash stands for. */
@Composable
private fun Key(color: Color, label: String, thickness: Int, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = thickness.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CHART_HEIGHT = 180.dp
