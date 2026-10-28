// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton

/**
 * A profile's form, for a new profile or one kept, in four parts: the body, the goal, the food and
 * how often its plans may repeat themselves, with what the figures come to beside them as they are
 * typed. Nothing is kept before it is saved; a field the API would refuse says why once saving was
 * asked.
 */
@Composable
fun ProfileFormScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: ProfileFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved != null) onSaved() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            title = state.editing?.let { stringResource(R.string.profiles_editing, it) } ?: stringResource(R.string.profiles_new),
            onBack = onBack,
        )
        when {
            state.loading -> {
                Skeleton(Modifier.fillMaxWidth().height(240.dp))
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
            }

            // A kept profile that could not be read has no form to show.
            state.error != null && state.editing == null && state.saved == null && !state.saving -> {
                ErrorBanner(errorMessage(state.error!!))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
            }

            else -> {
                val form = state.form
                val marks = state.marks
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormSection(stringResource(R.string.profile_section_body), first = true)
                        NameField(form, marks, viewModel::edit)
                        BodyFields(form, state.unit, marks, viewModel::edit)
                        FormSection(stringResource(R.string.profile_section_goal))
                        PaceChoice(form, state.unit, state.preview?.paces, viewModel::edit)
                        TargetWeightField(form, state.unit, marks, viewModel::edit)
                        OwnTargetField(form, marks, viewModel::edit)
                        RiskBox(
                            reasons = state.risks,
                            acknowledged = form.acknowledgeRisk,
                            onAcknowledge = { ticked -> viewModel.edit { it.copy(acknowledgeRisk = ticked) } },
                        )
                        ReminderField(form, viewModel::edit)
                        FormSection(stringResource(R.string.profile_section_food))
                        DietFields(form, marks, rules, viewModel::edit)
                        FormSection(stringResource(R.string.profile_section_variety))
                        LimitFields(form, marks, viewModel::edit)
                    }
                }
                CaloriePreviewCard(state.preview, state.behind, state.waiting, state.previewFailed, state.previewRefused)
                state.error?.let { ErrorBanner(errorMessage(it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = viewModel::save, enabled = !state.saving, shape = MaterialTheme.shapes.medium) {
                        Text(
                            stringResource(
                                when {
                                    state.saving -> R.string.profile_saving
                                    state.editing != null -> R.string.profile_save_changes
                                    else -> R.string.profile_create
                                },
                            ),
                        )
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

/** The heading of a part of the form, with a line above every part but the first. */
@Composable
private fun FormSection(title: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = if (first) 0.dp else 8.dp).semantics { heading() },
    )
}
