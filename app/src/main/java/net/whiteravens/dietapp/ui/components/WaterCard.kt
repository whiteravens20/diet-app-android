// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.WATER_AMOUNTS_ML
import net.whiteravens.dietapp.domain.WaterDay
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The water of [profileId]'s [day], as the web's card keeps it: what was drunk against the profile's
 * target, an amount added at a press or typed, the last entry removed, and the seven days that end
 * with the day as bars, a dashed line at the target and a tick over each day that reached it. On the
 * dashboard it is today's; in the diary it is the day the diary shows.
 */
@Composable
fun WaterCard(
    profileId: String,
    day: LocalDate,
    onOpenProfile: (() -> Unit)? = null,
    viewModel: WaterViewModel = hiltViewModel(key = "water-$profileId-$day"),
) {
    LaunchedEffect(profileId, day) { viewModel.open(profileId, day) }
    // Shown again, from another screen or coming back to the front: the day as it now is.
    LifecycleResumeEffect(viewModel) {
        viewModel.read()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noteClosed by viewModel.noteClosed.collectAsStateWithLifecycle()
    WaterCardContent(
        state,
        onAdd = { viewModel.add(it) },
        onType = viewModel::type,
        onAddTyped = viewModel::addTyped,
        onRemoveLast = viewModel::removeLast,
        noteShown = !noteClosed,
        onCloseNote = viewModel::closeNote,
        onOpenProfile = onOpenProfile,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaterCardContent(
    state: WaterViewModel.UiState,
    onAdd: (Int) -> Unit,
    onType: (String) -> Unit,
    onAddTyped: () -> Unit,
    onRemoveLast: () -> Unit,
    noteShown: Boolean = false,
    onCloseNote: () -> Unit = {},
    onOpenProfile: (() -> Unit)? = null,
) {
    val shown = state.shown
    val ready = state.ready
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.water_title), style = MaterialTheme.typography.titleMedium)
            when {
                shown == null && state.error != null -> {
                    Text(stringResource(R.string.water_read_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Text(errorMessage(state.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Added without a network all the same: what waits is said, the rest of the day comes with the answer.
                    state.waitingOnly?.let { Text(stringResource(R.string.water_waiting_only, liters(it.ml)), style = MaterialTheme.typography.bodyMedium) }
                }
                shown == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    val drunk = liters(shown.ml)
                    Text(
                        shown.target?.let { stringResource(R.string.water_drunk, drunk, liters(it)) } ?: stringResource(R.string.water_drunk_no_target, drunk),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    shown.target?.let { target ->
                        LinearProgressIndicator(progress = { (shown.ml.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WATER_AMOUNTS_ML.forEach { ml ->
                    Button(onClick = { onAdd(ml) }, enabled = ready) { Text(stringResource(R.string.water_add_ml, ml)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = state.typed,
                    onValueChange = onType,
                    label = stringResource(R.string.water_own),
                    problem = stringResource(R.string.water_not_an_amount).takeIf { state.problem == WaterViewModel.Problem.NotAnAmount },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onAddTyped, enabled = ready && state.typed.isNotEmpty()) { Text(stringResource(R.string.water_add)) }
            }
            when (val problem = state.problem) {
                is WaterViewModel.Problem.AddFailed -> Failure("${stringResource(R.string.water_add_failed)} ${errorMessage(problem.error)}")
                is WaterViewModel.Problem.RemoveFailed -> Failure("${stringResource(R.string.water_remove_failed)} ${errorMessage(problem.error)}")
                else -> Unit
            }
            state.last?.let { last ->
                val at = last.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRemoveLast, enabled = !state.busy) {
                        Icon(AppIcons.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.water_remove_last), modifier = Modifier.padding(start = 4.dp))
                        Text(
                            "(${stringResource(R.string.water_remove_last_label, millilitres(last.ml), at)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (state.lastWaits) WaitingMark()
                }
            }
            shown?.let { WaterChart(it) }
            if (noteShown) TurnOffNote(onClose = onCloseNote, onOpenProfile = onOpenProfile)
        }
    }
}

/** That the card can be turned off in the profile, said once, as the web says it, until it is closed. */
@Composable
private fun TurnOffNote(onClose: () -> Unit, onOpenProfile: (() -> Unit)?) {
    val link = stringResource(R.string.water_note_link)
    val whole = stringResource(R.string.water_note, link)
    val linked = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(whole)
        val start = whole.indexOf(link)
        if (onOpenProfile != null && start >= 0) {
            addLink(
                LinkAnnotation.Clickable(
                    tag = "profile",
                    styles = TextLinkStyles(SpanStyle(color = linked, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
                ) { onOpenProfile() },
                start,
                start + link.length,
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) }
    }
}

@Composable
private fun Failure(said: String) = Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

/** Millilitres as the language writes a volume in litres, kept with its unit: `0,6 l`. */
@Composable
private fun liters(ml: Int): String = held("${formatNumber(ml / 1000.0, 2)} ${stringResource(R.string.unit_liter)}")

/** Millilitres kept with their unit: `330 ml`. */
@Composable
private fun millilitres(ml: Int): String = held("${formatNumber(ml)} ${stringResource(R.string.unit_ml)}")

/**
 * The seven days as bars on a line, the [day]'s own darker, a dashed line across at the target where
 * there is one, a tick over each day that reached it; the same said in words to whoever does not see
 * the bars, and how many of the days were on target.
 */
@Composable
private fun WaterChart(day: WaterDay) {
    val locale = LocalConfiguration.current.locales[0]
    val dayOf = DateTimeFormatter.ofPattern("d MMM", locale)
    val target = day.target
    val reached = day.reached.map { it.day }.toSet()
    val lines = day.days.map { point ->
        val name = point.day.format(dayOf)
        if (point.day in reached) stringResource(R.string.water_day_reached, name, liters(point.ml))
        else stringResource(R.string.water_day_line, name, liters(point.ml))
    }
    val said = "${stringResource(R.string.water_days_label, day.day.format(dayOf))}: ${lines.joinToString("; ")}"
    val bar = MaterialTheme.colorScheme.primary
    val otherBar = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val tick = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics { contentDescription = said },
        ) {
            val tickRoom = 14.dp.toPx()
            val room = size.height - tickRoom
            val most = maxOf(day.days.maxOfOrNull { it.ml } ?: 0, target ?: 0, 1)
            val slot = size.width / day.days.size
            val width = slot * 0.6f
            fun y(ml: Int) = tickRoom + room * (1 - ml.toFloat() / most)
            drawLine(edge, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            day.days.forEachIndexed { index, point ->
                val left = slot * index + (slot - width) / 2
                if (point.ml > 0) {
                    drawRect(
                        if (point.day == day.day) bar else otherBar,
                        topLeft = Offset(left, y(point.ml)),
                        size = Size(width, size.height - y(point.ml)),
                    )
                }
                if (point.day in reached) {
                    val middle = left + width / 2
                    val top = y(point.ml) - 4.dp.toPx()
                    val mark = Path().apply {
                        moveTo(middle - 4.dp.toPx(), top - 4.dp.toPx())
                        lineTo(middle - 1.dp.toPx(), top - 1.dp.toPx())
                        lineTo(middle + 5.dp.toPx(), top - 8.dp.toPx())
                    }
                    drawPath(mark, tick, style = Stroke(width = 2.dp.toPx()))
                }
            }
            if (target != null) {
                drawLine(line, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            day.days.forEach { point ->
                Text(
                    point.day.format(dayOf),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (target != null) {
            Text(
                pluralStringResource(R.plurals.water_in_target, day.days.size, reached.size, day.days.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CHART_HEIGHT = 120.dp
