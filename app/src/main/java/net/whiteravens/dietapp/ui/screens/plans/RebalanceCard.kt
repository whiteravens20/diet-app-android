// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatNumber
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the last change rebalanced: the day's kcal before and after and how many meals were scaled,
 * whether the day is still off its target and what the week could not take back; the way back, the
 * rest of the week, and away. It goes by itself after a while, later where the phone is set to give
 * more time to act.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RebalanceCard(
    rebalance: Rebalance?,
    notice: String?,
    busy: Boolean,
    onUndo: () -> Unit,
    onRest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val timeout = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(REBALANCE_SHOWN_MILLIS, containsIcons = false, containsText = true, containsControls = true)
        ?: REBALANCE_SHOWN_MILLIS
    LaunchedEffect(rebalance, notice) {
        delay(timeout)
        dismiss()
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Why the engine's plain replacement stands where a model was asked to reword the text.
            notice?.let { reason -> fallbackText(reason)?.let { Hint(it, MaterialTheme.colorScheme.tertiary) } }
            if (rebalance != null) RebalanceSaid(rebalance)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (rebalance != null) {
                    OutlinedButton(onClick = onUndo, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_undo)) }
                    if (rebalance.scope == "day") {
                        OutlinedButton(onClick = onRest, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_rest)) }
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.plans_rebalance_dismiss)) }
            }
        }
    }
}

/**
 * What a rebalance did: of a day, its kcal before and after, the meals scaled and whether it is still
 * off its target, with what spreading it over the rest of the plan would do; of the rest of the
 * plan, the meals scaled. Either way what it could not take back.
 */
@Composable
private fun RebalanceSaid(rebalance: Rebalance) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rebalance.scope == "day") {
            Text(stringResource(R.string.plans_rebalance_title), style = MaterialTheme.typography.titleSmall)
            Hint(
                stringResource(
                    R.string.plans_rebalance_summary,
                    formatNumber(rebalance.caloriesBefore.roundToInt()),
                    formatNumber(rebalance.caloriesAfter.roundToInt()),
                    formatNumber(rebalance.changes.size),
                ),
            )
            if (rebalance.bestEffort) Hint(stringResource(R.string.plans_rebalance_best_effort), MaterialTheme.colorScheme.tertiary)
            Hint(stringResource(R.string.plans_rebalance_rest_note))
        } else {
            Text(stringResource(R.string.plans_rebalance_title_plan), style = MaterialTheme.typography.titleSmall)
            Hint(stringResource(R.string.plans_rebalance_summary_plan, formatNumber(rebalance.changes.size)))
        }
        if (rebalance.unabsorbedKcal != 0.0) {
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

/** How long the card of a rebalance stays, where the phone asks for no longer. */
private const val REBALANCE_SHOWN_MILLIS = 10_000L

@Composable
internal fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
