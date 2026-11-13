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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.time.LocalDate
import javax.inject.Inject

/** A profile's choice on its target weight reached: to keep the weight from now on, or to be asked again in two weeks. */
@HiltViewModel
class KeepWeightViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {

    data class UiState(
        val deciding: Boolean = false,
        val failed: Throwable? = null,
        /** How many choices were saved: a change of it is the profile's to be read again. */
        val decided: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun decide(profileId: String, keep: Boolean) {
        if (_state.value.deciding) return
        _state.update { it.copy(deciding = true, failed = null) }
        viewModelScope.launch {
            attempt { if (keep) profiles.keepWeight(profileId) else profiles.putOffKeepingWeight(profileId) }
                .onSuccess { _state.update { it.copy(deciding = false, decided = it.decided + 1) } }
                .onFailure { error -> _state.update { it.copy(deciding = false, failed = error) } }
        }
    }
}

/**
 * The weight worked towards is reached, on the dashboard, as the web's card offers it: to keep it
 * from now on ("Przejdź"), or to be asked again in two weeks ("Nie teraz"). Nothing until the newest
 * weight comes down to the target weight while the pace still loses, nor while the offer is put
 * off. [onDecided] is told once a choice is saved, so that the profile and its target are read again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeepWeightCard(
    profile: Profile,
    unit: WeightUnit,
    onDecided: () -> Unit,
    viewModel: KeepWeightViewModel = hiltViewModel(key = "keep-weight-${profile.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.decided) { if (state.decided > 0) onDecided() }
    val target = profile.targetWeightKg
    if (!profile.offersToKeep(LocalDate.now()) || target == null) return
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.keep_weight_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keep_weight_reached, held("${formatNumber(unit.shown(target), 1)}\u00A0${unit.code}")), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.keep_weight_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.decide(profile.id, keep = true) }, enabled = !state.deciding) { Text(stringResource(R.string.keep_weight_keep)) }
                OutlinedButton(onClick = { viewModel.decide(profile.id, keep = false) }, enabled = !state.deciding) { Text(stringResource(R.string.keep_weight_later)) }
            }
            state.failed?.let {
                Text("${stringResource(R.string.keep_weight_act_failed)} ${errorMessage(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
