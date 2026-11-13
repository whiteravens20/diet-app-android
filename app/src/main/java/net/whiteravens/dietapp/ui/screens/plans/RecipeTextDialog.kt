// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.Text

/**
 * The window the words of [recipeId], a recipe a substitution made, are corrected in: its title,
 * description and steps, in the reader's language. [onDone] once they are written.
 */
@Composable
internal fun RecipeTextDialog(
    recipeId: String,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: RecipeTextViewModel = hiltViewModel(key = "reword-$recipeId"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(recipeId) { viewModel.open(recipeId) }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plans_reword_title), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val failed = state.loadError
                when {
                    state.loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    failed != null -> ErrorBanner(errorMessage(failed))
                    else -> Words(state, viewModel)
                }
                state.refused?.let { ErrorBanner(errorMessage(it)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = viewModel::save, enabled = !state.saving && !state.loading && state.loadError == null) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}

/** The title, the description and the steps as they are typed, each step moved, taken off or added. */
@Composable
private fun Words(state: RecipeTextViewModel.UiState, viewModel: RecipeTextViewModel) {
    Text(stringResource(R.string.plans_reword_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::typeTitle,
        label = { Text(stringResource(R.string.own_recipe_field_title)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::typeDescription,
        label = { Text(stringResource(R.string.own_recipe_field_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.recipes_steps), style = MaterialTheme.typography.titleSmall)
    state.steps.forEachIndexed { index, step ->
        val number = index + 1
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = step,
                onValueChange = { typed -> viewModel.typeStep(index, typed) },
                label = { Text(stringResource(R.string.own_recipe_step_number, number)) },
                minLines = 2,
                modifier = Modifier.weight(1f),
            )
            Column {
                IconButton(onClick = { viewModel.moveStep(index, -1) }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.own_recipe_step_up, number))
                }
                IconButton(onClick = { viewModel.moveStep(index, 1) }, enabled = index < state.steps.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.own_recipe_step_down, number))
                }
                IconButton(onClick = { viewModel.removeStep(index) }, enabled = state.steps.size > 1) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.own_recipe_remove_step, number))
                }
            }
        }
    }
    OutlinedButton(onClick = viewModel::addStep) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.own_recipe_add_step), modifier = Modifier.padding(start = 8.dp))
    }
}
