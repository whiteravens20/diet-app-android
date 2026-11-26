// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
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
import net.whiteravens.dietapp.ui.components.KeptCorrection
import net.whiteravens.dietapp.ui.components.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

/**
 * A profile's form, for a new profile or one kept, in four parts: the body, the goal, the food and
 * how often its plans may repeat themselves, with what the figures come to beside them as they are
 * typed. Nothing is kept before it is saved; a field the API would refuse says why once saving was
 * asked.
 */
@Composable
fun ProfileFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /** The section a link came for, open beside the first: the water's note asks for the goal. */
    asked: ProfileSection? = null,
    viewModel: ProfileFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved != null) onSaved() }
    // A kept profile keeps each change as it is made: leaving keeps what the field being typed in holds, and asks
    // whether to leave behind a change that could not be kept.
    val focus = LocalFocusManager.current
    val keeping = state.editingId != null
    val leave = {
        focus.clearFocus()
        viewModel.leave()
    }
    BackHandler(enabled = keeping && !state.left) { leave() }
    // The sections open: a new profile's every one, a kept one's body alone, the rest folded to a line of what they hold.
    var opened by rememberSaveable(keeping) {
        mutableStateOf(if (keeping) setOfNotNull(ProfileSection.BODY, asked) else ProfileSection.entries.toSet())
    }
    LaunchedEffect(state.left) { if (state.left) onBack() }
    if (state.askLeaving) LeaveBehind(offline = state.autosave == Autosave.OFFLINE, onStay = viewModel::stay, onLeave = viewModel::leaveBehind)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            title = state.editing?.let { stringResource(R.string.profiles_editing, it) } ?: stringResource(R.string.profiles_new),
            onBack = if (keeping) leave else onBack,
        )
        if (keeping) {
            Text(
                stringResource(R.string.profile_autosave_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                // A section that needs an answer opens by itself: a field to fix.
                val needing = sectionsNeeding(marks)
                LaunchedEffect(needing) { if (!opened.containsAll(needing)) opened = opened + needing }
                // A section folded while one of its fields is typed in keeps what the field holds: the field is left as it goes.
                val section = @Composable { which: ProfileSection, first: Boolean ->
                    FormSection(which, sectionLine(which, form, state.unit), which in opened, first) { open ->
                        opened = if (open) opened + which else opened - which
                    }
                }
                // A field of a kept profile keeps what it holds once it is left.
                CompositionLocalProvider(LocalFieldLeft provides if (keeping) viewModel::keep else null) {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            section(ProfileSection.BODY, true)
                            if (ProfileSection.BODY in opened) {
                                NameField(form, marks, viewModel::edit)
                                BodyFields(form, state.unit, marks, viewModel::edit)
                            }
                            section(ProfileSection.GOAL, false)
                            if (ProfileSection.GOAL in opened) GoalFields(state, viewModel)
                            section(ProfileSection.FOOD, false)
                            if (ProfileSection.FOOD in opened) {
                                DietFields(form, marks, rules, viewModel::edit, target = state.ownTarget, weightKg = form.bodyWeightKg(state.unit))
                            }
                            section(ProfileSection.VARIETY, false)
                            if (ProfileSection.VARIETY in opened) LimitFields(form, marks, viewModel::edit)
                            section(ProfileSection.LABELS, false)
                            if (ProfileSection.LABELS in opened) LabelLimitFields(form, marks, viewModel::edit)
                        }
                    }
                }
                CaloriePreviewCard(state.preview, state.behind, state.waiting, state.previewFailed, state.previewRefused)
                state.error?.let { ErrorBanner(errorMessage(it)) }
                if (keeping) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = leave, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.profile_autosave_done)) }
                        AutosaveStatus(state.autosave, Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = viewModel::save, enabled = !state.saving, shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(if (state.saving) R.string.profile_saving else R.string.profile_create))
                        }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            }
        }
    }
}

/**
 * The heading of a part of the form, with a line above every part but the first: a press folds it to one [line] of
 * what it holds, and opens it again.
 */
@Composable
private fun FormSection(section: ProfileSection, line: String, open: Boolean, first: Boolean, onOpen: (Boolean) -> Unit) {
    if (!first) HorizontalDivider(Modifier.padding(top = 8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (first) 0.dp else 8.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onOpen(!open) }
            .semantics { if (open) collapse { onOpen(false); true } else expand { onOpen(true); true } },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (!open) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The goal: the pace, the weight to work towards, water, a target typed by hand, the correction the real expenditure
 * brought, what the goal means for a body planned no loss, and the reminders.
 */
@Composable
private fun GoalFields(state: ProfileFormViewModel.UiState, viewModel: ProfileFormViewModel) {
    val form = state.form
    val marks = state.marks
    PaceChoice(form, state.unit, state.preview?.paces, viewModel::edit)
    TargetWeightField(form, state.unit, marks, viewModel::edit)
    WaterTrackingChoice(form, viewModel::edit)
    if (form.waterTracking) WaterTargetField(form, marks, viewModel::edit)
    OwnTargetField(form, marks, viewModel::edit, least = state.preview?.calculation?.leastTypedTarget)
    // The correction the real expenditure brought, with the way to take it away; the profile is read again after.
    val correction = state.correction
    val editingId = state.editingId
    if (correction != null && editingId != null) {
        KeptCorrection(profileId = editingId, correction = correction, onDecided = viewModel::load)
    }
    RiskNote(reasons = state.risks)
    ReminderField(form, viewModel::edit)
    ReminderChoices(form, viewModel::edit)
}

/** What the form of a kept profile says of its changes, beside "Gotowe"; nothing before the first one. */
@Composable
private fun AutosaveStatus(status: Autosave, modifier: Modifier = Modifier) {
    val text = when (status) {
        Autosave.IDLE -> return
        Autosave.SAVING -> R.string.profile_autosave_saving
        Autosave.SAVED -> R.string.profile_autosave_saved
        Autosave.NOT_SAVED -> R.string.profile_autosave_not_saved
        Autosave.OFFLINE -> R.string.profile_autosave_offline
    }
    val wrong = status == Autosave.NOT_SAVED || status == Autosave.OFFLINE
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Leaving the form of a kept profile while a change is not kept: stay, or leave it behind. */
@Composable
private fun LeaveBehind(offline: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.profile_leave_title)) },
        text = { Text(stringResource(if (offline) R.string.profile_leave_offline else R.string.profile_leave_not_saved)) },
        confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.profile_leave_stay)) } },
        dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.profile_leave_anyway)) } },
    )
}
