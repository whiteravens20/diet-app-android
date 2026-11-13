// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import kotlin.math.abs

/**
 * A corrected target the diary and the weights propose, on the dashboard, as the web's card says it:
 * what the body seems to use against what the formula says, and the target that would come of it,
 * to accept or put off. Nothing where nothing is proposed. [onDecided] is told once a decision is
 * saved, so that the profile's target is read again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenditureCard(
    profileId: String,
    onDecided: () -> Unit,
    viewModel: ExpenditureViewModel = hiltViewModel(key = "expenditure-$profileId"),
) {
    LaunchedEffect(profileId) { viewModel.open(profileId) }
    LifecycleResumeEffect(viewModel) {
        viewModel.read()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.decided) { if (state.decided > 0) onDecided() }
    val shown = state.expenditure?.takeIf { it.proposed } ?: return
    val correction = shown.correction ?: return
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.expenditure_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (correction > 0) R.string.expenditure_proposed_more else R.string.expenditure_proposed_less,
                    shown.estimate ?: shown.maintenance,
                    abs(correction),
                    shown.targetCorrected ?: shown.target,
                    shown.target,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.expenditure_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.decide(ExpenditureViewModel.Decision.ACCEPT) }, enabled = !state.deciding) { Text(stringResource(R.string.expenditure_accept)) }
                OutlinedButton(onClick = { viewModel.decide(ExpenditureViewModel.Decision.LATER) }, enabled = !state.deciding) { Text(stringResource(R.string.expenditure_later)) }
            }
            state.failed?.let {
                Text("${stringResource(R.string.expenditure_act_failed)} ${errorMessage(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The correction [profileId] keeps, [correction] kcal a day, on its profile, with the way to take it away. */
@Composable
fun KeptCorrection(
    profileId: String,
    correction: Int,
    onDecided: () -> Unit,
    viewModel: ExpenditureViewModel = hiltViewModel(key = "expenditure-kept-$profileId"),
) {
    LaunchedEffect(profileId) { viewModel.open(profileId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.decided) { if (state.decided > 0) onDecided() }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.expenditure_kept, "${if (correction > 0) "+" else "\u2212"}${abs(correction)}"),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { viewModel.decide(ExpenditureViewModel.Decision.DROP) }, enabled = !state.deciding) {
                Text(stringResource(R.string.expenditure_drop))
            }
            state.failed?.let {
                Text("${stringResource(R.string.expenditure_act_failed)} ${errorMessage(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
