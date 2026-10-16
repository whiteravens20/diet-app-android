// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField

/** The meals a set can name, in the order of a day, as the web's form lists them. */
private val SET_SLOTS = MealSlots.BY_COUNT.getValue(5)

/** The longest a set's name may be: the API's own bound. */
private const val LABEL_LENGTH = 80

/**
 * A profile's favourite sets, as the web's card has them: each a name with the recipe it has for
 * each meal it names, to change or delete; a new one made from the profile's favourites. What a set
 * needs first, and where there is none yet, the way to the first one.
 */
@Composable
internal fun FavoriteSetsCard(
    stats: DashboardViewModel.ProfileStats,
    deleting: String?,
    deleteFailed: Throwable?,
    onNew: () -> Unit,
    onEdit: (FavoriteSet) -> Unit,
    onDelete: (FavoriteSet) -> Unit,
    onRecipes: () -> Unit,
) {
    val sets = stats.sets
    val marks = stats.marks
    // A set is made of what the profile likes: a recipe it marked to avoid is none of that.
    val hasFavorites = marks.orEmpty().any { it.sentiment != AVOID }
    AppCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.favorite_sets_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.favorite_sets_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (hasFavorites && !sets.isNullOrEmpty()) OutlinedButton(onClick = onNew) { Text(stringResource(R.string.favorite_sets_new)) }
        }
        when {
            stats.setsError != null -> ErrorBanner(errorMessage(stats.setsError), Modifier.padding(top = 12.dp))
            // Until both answers are in, nothing is said to be missing.
            sets == null || marks == null -> Unit
            !hasFavorites -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted(stringResource(R.string.favorite_sets_no_favorites))
                OutlinedButton(onClick = onRecipes) { Text(stringResource(R.string.favorite_sets_no_favorites_cta)) }
            }
            sets.isEmpty() -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted(stringResource(R.string.favorite_sets_empty))
                Button(onClick = onNew) { Text(stringResource(R.string.favorite_sets_new)) }
            }
            else -> Column(Modifier.padding(top = 8.dp)) {
                sets.forEachIndexed { index, set ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SetRow(set, marks, busy = deleting != null, onEdit = { onEdit(set) }, onDelete = { onDelete(set) })
                }
            }
        }
        deleteFailed?.let { ErrorBanner("${stringResource(R.string.favorite_sets_err_delete)} ${errorMessage(it)}", Modifier.padding(top = 8.dp)) }
    }
}

/** A set: its name, the recipe it has for each meal it names, and what can be done with it. */
@Composable
private fun SetRow(set: FavoriteSet, marks: List<MarkedRecipe>, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(set.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Muted(
            SET_SLOTS.filter { it in set.slots }.map { slot -> "${mealTypeLabel(slot)}: ${titleOf(set.slots.getValue(slot), marks)}" }.joinToString(" · "),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.common_edit)) }
            TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * The window a set is made or changed in, as the web's form is: its name, and for each meal one of
 * the profile's favourites that are recipes for it, or none, which leaves a day's meal of that kind
 * as it is where the set is put. A set names at least one meal.
 */
@Composable
internal fun FavoriteSetDialog(edit: DashboardViewModel.SetEdit, marks: List<MarkedRecipe>, onSave: (label: String, slots: Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val kept = edit.set
    var label by rememberSaveable(edit.profileId, kept?.id) { mutableStateOf(kept?.label.orEmpty()) }
    var slots by rememberSaveable(edit.profileId, kept?.id) { mutableStateOf(kept?.slots.orEmpty()) }
    var noMeal by rememberSaveable(edit.profileId, kept?.id) { mutableStateOf(false) }
    val liked = marks.filter { it.sentiment != AVOID }
    val pick = "— ${stringResource(R.string.favorite_sets_pick_recipe)} —"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kept?.label ?: stringResource(R.string.favorite_sets_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(LABEL_LENGTH) },
                    label = { Text(stringResource(R.string.favorite_sets_label)) },
                    placeholder = { Text(stringResource(R.string.favorite_sets_label_placeholder)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.favorite_sets_slots_heading), style = MaterialTheme.typography.titleSmall)
                Muted(stringResource(R.string.favorite_sets_slots_hint))
                SET_SLOTS.forEach { slot ->
                    // The favourites that are recipes for the meal, and the set's own recipe where it is no longer among them.
                    val options = liked.filter { slot in it.mealTypes }.map { it.recipeId }
                    val chosen = slots[slot]
                    ChoiceField(
                        label = mealTypeLabel(slot),
                        options = listOf(null) + options + listOfNotNull(chosen?.takeUnless { it in options }),
                        selected = chosen,
                        optionLabel = { id -> id?.let { titleOf(it, marks) } ?: pick },
                        onSelect = { id ->
                            noMeal = false
                            slots = if (id == null) slots - slot else slots + (slot to id)
                        },
                    )
                }
                if (noMeal) Text(stringResource(R.string.favorite_sets_at_least_one), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                edit.error?.let { ErrorBanner("${stringResource(R.string.favorite_sets_err_save)} ${errorMessage(it)}") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (slots.isEmpty()) noMeal = true else onSave(label.trim(), SET_SLOTS.filter { it in slots }.associateWith { slots.getValue(it) })
                },
                enabled = label.isNotBlank() && !edit.saving,
            ) { Text(stringResource(R.string.favorite_sets_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !edit.saving) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A recipe of a set by its title among the profile's marks; one no longer marked by its id, as the web's card says it. */
private fun titleOf(recipeId: String, marks: List<MarkedRecipe>): String = marks.firstOrNull { it.recipeId == recipeId }?.title ?: recipeId

/** How a profile marks a recipe it would rather not have planned. */
private const val AVOID = "avoid"
