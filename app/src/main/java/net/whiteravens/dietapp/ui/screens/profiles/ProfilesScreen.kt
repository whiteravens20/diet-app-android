// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.weightLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton

/**
 * The account's profiles: each with what it is, to edit or delete; a new one up to what the
 * instance allows; and the first one set up in three steps when there is none.
 */
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onFirstPlan: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    // Read anew on opening and on coming back, from a form that saved a profile among others.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val maxProfiles = rules?.maxProfiles ?: InstanceRules.DEFAULT_MAX_PROFILES
    val list = state.list

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.profiles_title),
            subtitle = pluralStringResource(R.plurals.profiles_subhead, maxProfiles, maxProfiles),
            onBack = onBack,
        )
        // A change refused outside a form (a profile that could not be deleted) is said here.
        state.error?.let { ErrorBanner(errorMessage(it)) }

        when {
            state.loading && list.isEmpty() -> {
                Skeleton(Modifier.fillMaxWidth().height(96.dp))
                Skeleton(Modifier.fillMaxWidth().height(96.dp))
            }

            state.loadError != null && list.isEmpty() -> {
                ErrorBanner(errorMessage(state.loadError!!))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
            }

            else -> {
                list.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        unit = state.unit,
                        deleting = state.deleting,
                        onEdit = { onEdit(profile.id) },
                        onDelete = { deleting = profile.id },
                    )
                }
                if (list.isEmpty()) FirstProfile(rules = rules, onMade = viewModel::made)

                state.made?.takeIf { made -> list.any { it.id == made.id } }?.let { made -> MadeCard(made, onFirstPlan) }

                if (list.isNotEmpty() && list.size < maxProfiles) {
                    Button(onClick = onAdd, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.profiles_add)) }
                }
                if (list.size >= maxProfiles) {
                    Text(
                        pluralStringResource(R.plurals.profiles_at_limit, maxProfiles, maxProfiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    deleting?.let { id ->
        val profile = list.find { it.id == id }
        if (profile == null) {
            deleting = null
        } else {
            AlertDialog(
                onDismissRequest = { deleting = null },
                text = { Text(stringResource(R.string.profiles_delete_confirm, profile.figures.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleting = null
                            viewModel.delete(profile)
                        },
                    ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
    }
}

/** A profile in the list: its name and what it is, to edit or delete. */
@Composable
private fun ProfileCard(profile: ProfileDetails, unit: WeightUnit, deleting: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val figures = profile.figures
    AppCard {
        Text(figures.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                R.string.profiles_summary,
                pluralStringResource(R.plurals.profiles_age, figures.age, figures.age),
                formatNumber(figures.heightCm, 3),
                weightLabel(figures.weightKg, unit.code),
                dietTypeLabel(figures.dietType),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.common_edit)) }
            TextButton(onClick = onDelete, enabled = !deleting) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * The first profile of an account, set up in three steps: the body, the goal, and how its owner
 * eats. One form holds all three, a step at a time, so that going back finds everything as it was
 * left; beside it stand the figures that what is typed comes to. Nothing is kept before the last.
 */
@Composable
private fun FirstProfile(rules: InstanceRules?, onMade: (Profile) -> Unit) {
    val viewModel: ProfileFormViewModel = hiltViewModel(key = "first-profile")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        state.saved?.let { made ->
            onMade(made)
            viewModel.startOver()
        }
    }
    val form = state.form
    val problems = if (state.showProblems) state.problems else emptySet()
    val steps = listOf(R.string.profile_wizard_step_body, R.string.profile_wizard_step_goal, R.string.profile_wizard_step_food)
    val last = steps.lastIndex

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_wizard_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.profile_wizard_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.profile_wizard_progress, state.step + 1, steps.size, stringResource(steps[state.step])),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            when (state.step) {
                0 -> {
                    Lead(R.string.profile_wizard_body_lead)
                    NameField(form, problems, viewModel::edit)
                    BodyFields(form, state.unit, problems, viewModel::edit)
                }

                1 -> {
                    Lead(R.string.profile_wizard_goal_lead)
                    PaceChoice(form, state.unit, state.preview?.paces, viewModel::edit)
                    RiskBox(
                        reasons = state.risks,
                        acknowledged = form.acknowledgeRisk,
                        onAcknowledge = { ticked -> viewModel.edit { it.copy(acknowledgeRisk = ticked) } },
                    )
                    TargetWeightField(form, state.unit, problems, viewModel::edit)
                    OwnTargetField(form, problems, viewModel::edit)
                }

                else -> {
                    Lead(R.string.profile_wizard_food_lead)
                    DietFields(form, problems, rules, viewModel::edit)
                    AllergenChoice(
                        selected = form.allergens,
                        onChange = { chosen -> viewModel.edit { it.copy(allergens = chosen.toSet()) } },
                        enabled = !state.saving,
                    )
                }
            }
            state.error?.let { ErrorBanner(errorMessage(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.step > 0) {
                    OutlinedButton(onClick = viewModel::back, enabled = !state.saving, shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.common_back))
                    }
                }
                Button(
                    onClick = { if (state.step < last) viewModel.next() else viewModel.make() },
                    enabled = !state.saving,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        stringResource(
                            when {
                                state.step < last -> R.string.common_next
                                state.saving -> R.string.profile_saving
                                else -> R.string.profile_create
                            },
                        ),
                    )
                }
            }
        }
    }
    CaloriePreviewCard(state.preview, state.behind, state.waiting, state.previewFailed)
}

@Composable
private fun Lead(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** What comes after the first profile: its first plan. */
@Composable
private fun MadeCard(made: Profile, onFirstPlan: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_wizard_made, made.name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.profile_wizard_made_next),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onFirstPlan, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.profile_wizard_made_cta)) }
        }
    }
}
