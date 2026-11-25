// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.EffectList
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanEffects
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatDayMonth
import net.whiteravens.dietapp.ui.common.formatNumber
import kotlin.math.abs
import kotlin.math.roundToInt
import net.whiteravens.dietapp.ui.components.Text

/**
 * What the last change came to, on a card with the app's colour behind it and its edge: what the [day]
 * holds now, what was sized again and whether the day is still off its target, what the week could not
 * take back, and what the change did to the shopping lists and the freezer ([effects]), each a way to
 * the list or the day; the way back, a swap's whole ([swapRevision]) or the portions alone, the rest
 * of the week where the day is off its target, the day's history ([onHistory]), and away. A change
 * that says what it did in a sentence of its own ([said]) says that first. It goes by itself after a
 * while, later where the phone is set to give more time to act; a card that speaks of a list or the
 * freezer stays until it is put away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RebalanceCard(
    rebalance: Rebalance?,
    day: PlanDay?,
    notice: String?,
    busy: Boolean,
    onUndo: () -> Unit,
    onRest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    swapRevision: Int? = null,
    onUndoSwap: () -> Unit = {},
    reword: String? = null,
    onReword: (String) -> Unit = {},
    said: String? = null,
    effects: PlanEffects? = null,
    onOpenList: (EffectList) -> Unit = {},
    onShowDay: (String) -> Unit = {},
    onHistory: (() -> Unit)? = null,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val timeout = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(REBALANCE_SHOWN_MILLIS, containsIcons = true, containsText = true, containsControls = true)
        ?: REBALANCE_SHOWN_MILLIS
    val lasting = effects?.told == true
    LaunchedEffect(rebalance, swapRevision, notice, said, lasting) {
        if (lasting) return@LaunchedEffect
        delay(timeout)
        dismiss()
    }
    val swap = swapRevision != null
    val ofTheDay = rebalance == null || rebalance.scope == "day"
    val spreadable = ofTheDay && day?.offTarget != null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        // The edge in the app's colour, and its light shade behind the words.
        val edge = MaterialTheme.colorScheme.primary
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                .drawBehind { drawRect(edge, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            said?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            // Why the engine's plain replacement stands where a model was asked to reword the text.
            notice?.let { reason -> fallbackText(reason)?.let { Hint(it, MaterialTheme.colorScheme.tertiary) } }
            if (rebalance != null || swap) ChangeSaid(rebalance, swap, day, spreadable)
            effects?.let { Effects(it, onOpenList, onShowDay) }
            // A recipe a substitution made without a model holds the catalogue's name as it is.
            if (reword != null) Hint(stringResource(R.string.plans_reword_note))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (swap) {
                    OutlinedButton(onClick = onUndoSwap, enabled = !busy) { Text(stringResource(R.string.plans_undo_swap)) }
                } else if (rebalance != null) {
                    OutlinedButton(onClick = onUndo, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_undo)) }
                }
                if (reword != null) {
                    OutlinedButton(onClick = { onReword(reword) }, enabled = !busy) { Text(stringResource(R.string.plans_reword_button)) }
                }
                if (spreadable) {
                    OutlinedButton(onClick = onRest, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_rest)) }
                }
                onHistory?.let { TextButton(onClick = it) { Text(stringResource(R.string.plans_history_link)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.plans_rebalance_dismiss)) }
            }
        }
    }
}

/**
 * What a change did to the shopping lists and the freezer: each list that stopped matching its plan,
 * with the way to it, and the days whose notes of what to take out of the freezer or to freeze moved,
 * each a way to its day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Effects(effects: PlanEffects, onOpenList: (EffectList) -> Unit, onShowDay: (String) -> Unit) {
    effects.lists.forEach { list ->
        FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plans_effect_list, formatDates(list.fromDate, list.toDate)), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onOpenList(list) }) { Text(stringResource(R.string.plans_effect_open_list)) }
        }
    }
    listOf(R.string.plans_effect_thaw to effects.thaw, R.string.plans_effect_freeze to effects.freeze).forEach { (said, days) ->
        if (days.isEmpty()) return@forEach
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(said), style = MaterialTheme.typography.bodyMedium)
            days.forEach { day ->
                val named = stringResource(R.string.plans_effect_day, formatDayDate(day))
                TextButton(onClick = { onShowDay(day) }, modifier = Modifier.semantics { contentDescription = named }) {
                    Text(formatDayMonth(day))
                }
            }
        }
    }
}

/**
 * What a change did: of a day, what it holds now, what was sized again and whether it is still off
 * its target, with what spreading it over the rest of the plan would do; of the rest of the plan,
 * the meals scaled. Either way what it could not take back.
 */
@Composable
private fun ChangeSaid(rebalance: Rebalance?, swap: Boolean, day: PlanDay?, spreadable: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rebalance == null || rebalance.scope == "day") {
            Title(stringResource(if (swap && rebalance == null) R.string.plans_swap_done_title else R.string.plans_rebalance_title))
            day?.let {
                Hint(
                    stringResource(
                        R.string.plans_word_day,
                        formatNumber(it.nutrition.calories.roundToInt()),
                        formatNumber(it.calorieTarget.roundToInt()),
                        formatNumber(it.nutrition.protein.roundToInt()),
                        formatNumber(it.targetMacros.protein.roundToInt()),
                    ),
                )
            }
            if (rebalance != null) {
                Hint(
                    pluralStringResource(
                        R.plurals.plans_rebalance_summary,
                        rebalance.changes.size,
                        formatNumber(rebalance.caloriesBefore.roundToInt()),
                        formatNumber(rebalance.caloriesAfter.roundToInt()),
                        formatNumber(rebalance.changes.size),
                    ),
                )
                if (rebalance.bestEffort) Hint(stringResource(R.string.plans_rebalance_best_effort), MaterialTheme.colorScheme.tertiary)
            }
            if (spreadable) Hint(stringResource(R.string.plans_rebalance_rest_note))
        } else {
            Title(stringResource(R.string.plans_rebalance_title_plan))
            Hint(stringResource(R.string.plans_rebalance_summary_plan, formatNumber(rebalance.changes.size)))
        }
        if (rebalance != null && rebalance.unabsorbedKcal != 0.0) {
            Hint(
                stringResource(
                    if (rebalance.unabsorbedKcal > 0) R.string.plans_rebalance_left_over else R.string.plans_rebalance_left_under,
                    formatNumber(abs(rebalance.unabsorbedKcal).roundToInt()),
                ),
                MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** The card's title, larger than its lines, with the sign of portions sized again before it. */
@Composable
private fun Title(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** How long the card of a rebalance stays, where the phone asks for no longer. */
private const val REBALANCE_SHOWN_MILLIS = 15_000L

@Composable
internal fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
