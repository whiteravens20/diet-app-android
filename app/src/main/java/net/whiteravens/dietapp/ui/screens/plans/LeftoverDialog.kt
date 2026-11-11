// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.Portion
import net.whiteravens.dietapp.ui.components.DOT
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.time.LocalDate
import kotlin.math.roundToInt

/** The parts of a portion that may be left, as the window offers them, and their names. */
private val PARTS = listOf(
    0.75 to R.string.plans_leftover_three_quarters,
    0.5 to R.string.plans_leftover_half,
    0.25 to R.string.plans_leftover_quarter,
)

/** A number above nothing typed in a field, a comma taken for a point; null for anything else. */
private fun typed(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

/**
 * What one person says is left of their portion: nothing of it [eaten] and all of it left, or a part,
 * chosen ([part]) or weighed ([byWeight], the [whole] portion and what is [left] of it).
 */
internal data class Left(
    val eaten: Boolean,
    val part: Double? = null,
    val byWeight: Boolean = false,
    val whole: String = "",
    val left: String = "",
) {
    /** The part of the portion left; null while that says none yet, or two weights say none that can be. */
    val share: Double?
        get() = when {
            !eaten -> 1.0
            byWeight -> {
                val whole = typed(whole)
                val left = typed(left)
                if (whole != null && left != null && left < whole) left / whole else null
            }
            else -> part
        }

    /** Two weights that cannot be: what is left weighs no less than the whole. */
    val wrongWeights: Boolean
        get() {
            val whole = typed(whole)
            val left = typed(left)
            return eaten && byWeight && whole != null && left != null && left >= whole
        }
}

private val LeftSaver = listSaver<Left, Any?>(
    save = { listOf(it.eaten, it.part, it.byWeight, it.whole, it.left) },
    restore = { Left(it[0] as Boolean, it[1] as Double?, it[2] as Boolean, it[3] as String, it[4] as String) },
)

/** Why the next day cannot take what is left of a meal, as its change says it under its name. */
@StringRes
internal fun leftoverOff(offer: String): Int = when (offer) {
    "no_day" -> R.string.plans_leftover_off_no_day
    "skipped" -> R.string.plans_leftover_off_skipped
    else -> R.string.plans_leftover_off_no_meal
}

/** A tenth of a serving, as a portion is said. */
private fun shown(servings: Double): Double = (servings * 10).roundToInt() / 10.0

/**
 * The window that puts what is left of a meal in as a meal of the next day, in the place of the dish at
 * a time of day chosen. It says first whether anything of the meal was eaten: nothing, and the meal
 * leaves its day with the whole portion going; a part, and the meal stays marked eaten with the part
 * eaten, what is left told as a part of the portion or by two weights. Of a shared meal the other
 * person's portion may go as well, with what is left of it. It needs the network.
 */
@Composable
internal fun LeftoverDialog(
    window: PlanDetailViewModel.LeftoverWindow,
    onSend: (slot: String, left: Double, theirLeft: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val meal = window.meal
    val offer = window.offer
    var mine by rememberSaveable(stateSaver = LeftSaver) { mutableStateOf(Left(eaten = meal.eaten)) }
    var theirs by rememberSaveable(stateSaver = LeftSaver) { mutableStateOf(Left(eaten = meal.shared?.with?.eaten == true)) }
    // For both while the other person has not marked their portion eaten, unless the user says otherwise.
    var forAllSaid by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var slot by rememberSaveable { mutableStateOf<String?>(null) }
    val other = offer?.other
    val forAll = other != null && (forAllSaid ?: !other.eaten)
    val places = offer?.places.orEmpty().filter { !forAll || it.slot in other!!.places }
    val chosen = places.firstOrNull { it.slot == slot }
    val myLeft = mine.share
    val theirLeft = if (forAll) theirs.share else 1.0
    val ready = offer != null && chosen != null && myLeft != null && theirLeft != null && !window.sending
    val today = LocalDate.now().toString()
    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.plans_leftover_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${mealTypeLabel(meal.slot)}$DOT${formatDayDate(window.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.plans_leftover_intro, meal.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    offer == null && window.error != null ->
                        Text(errorMessage(window.error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    offer == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> {
                        PortionLeft(stringResource(R.string.plans_leftover_eaten_legend), offer.portion, mine) { mine = it }
                        if (other != null) {
                            CheckRow(
                                label = stringResource(R.string.plans_leftover_for_all, other.profileName),
                                checked = forAll,
                                onChange = { forAllSaid = it },
                            )
                        }
                        if (other != null && forAll) {
                            PortionLeft(stringResource(R.string.plans_leftover_eaten_legend_of, other.profileName), other.portion, theirs) { theirs = it }
                        }
                        Text(stringResource(R.string.plans_leftover_place_legend, formatDayDate(offer.date)), style = MaterialTheme.typography.titleSmall)
                        Column(Modifier.selectableGroup()) {
                            places.forEach { place ->
                                Choice(
                                    stringResource(
                                        R.string.plans_leftover_place,
                                        mealTypeLabel(place.slot),
                                        place.name,
                                        formatNumber(place.calories.roundToInt()),
                                    ),
                                    selected = slot == place.slot,
                                ) { slot = place.slot }
                                // A place's notes stand under its words, past the round button.
                                Column(Modifier.padding(start = 32.dp)) {
                                    place.sharedWith?.takeUnless { forAll }?.let {
                                        Hint(stringResource(R.string.plans_leftover_place_shared, it))
                                    }
                                    apartNote(place.cookAhead, place.cookedWith, offer.date, today)?.let { Hint(it) }
                                }
                            }
                        }
                        myLeft?.let { left ->
                            Hint(
                                if (left >= 1.0) {
                                    stringResource(R.string.plans_leftover_summary_gone)
                                } else {
                                    stringResource(
                                        R.string.plans_leftover_summary_kept,
                                        servingsWord(shown((1 - left) * offer.portion.servings)),
                                        formatNumber(((1 - left) * offer.portion.calories).roundToInt()),
                                    )
                                },
                            )
                            Hint(
                                stringResource(
                                    R.string.plans_leftover_summary_day,
                                    servingsWord(shown(left * offer.portion.servings)),
                                    formatNumber((left * offer.portion.calories).roundToInt()),
                                ),
                            )
                        }
                        window.error?.let { Text(errorMessage(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = { onSend(chosen!!.slot, myLeft!!, theirLeft.takeIf { forAll }) }, enabled = ready) {
                    Text(stringResource(R.string.plans_leftover_submit))
                }
            }
        }
    }
}

/** What one person says of their portion: whether anything of it was eaten, and how much is left. */
@Composable
private fun PortionLeft(legend: String, portion: Portion, said: Left, onSay: (Left) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(legend, style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            Choice(stringResource(R.string.plans_leftover_nothing), selected = !said.eaten) { onSay(said.copy(eaten = false)) }
            Choice(stringResource(R.string.plans_leftover_part), selected = said.eaten) { onSay(said.copy(eaten = true)) }
        }
        if (said.eaten) {
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.plans_leftover_left_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    PARTS.forEach { (part, label) ->
                        Choice(stringResource(label), selected = !said.byWeight && said.part == part) { onSay(said.copy(part = part, byWeight = false)) }
                    }
                    Choice(stringResource(R.string.plans_leftover_by_weight), selected = said.byWeight) { onSay(said.copy(byWeight = true)) }
                }
                if (said.byWeight) {
                    FormField(
                        value = said.whole,
                        onValueChange = { onSay(said.copy(whole = it)) },
                        label = stringResource(R.string.plans_leftover_whole_weight),
                        keyboardType = KeyboardType.Decimal,
                    )
                    FormField(
                        value = said.left,
                        onValueChange = { onSay(said.copy(left = it)) },
                        label = stringResource(R.string.plans_leftover_left_weight),
                        problem = stringResource(R.string.plans_leftover_weight_wrong).takeIf { said.wrongWeights },
                        keyboardType = KeyboardType.Decimal,
                    )
                    said.share?.let { share ->
                        Hint(
                            stringResource(
                                R.string.plans_leftover_weight_share,
                                (share * 100).roundToInt(),
                                formatNumber((share * portion.calories).roundToInt()),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** One choice among others, the whole row taking a finger. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The window that undoes a leftover not eaten: the dish it replaced comes back, and so does the meal
 * it was left of. One made for both people is undone for both, or for this person alone; the buttons
 * stand one under another.
 */
@Composable
internal fun UndoLeftoverDialog(meal: Meal, onUndo: (forAll: Boolean) -> Unit, onDismiss: () -> Unit) {
    val both = meal.shared?.with?.eaten == false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_leftover_undo_title)) },
        text = { Text(stringResource(R.string.plans_leftover_undo_note)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (both) {
                    TextButton(onClick = { onUndo(true) }) { Text(stringResource(R.string.plans_leftover_undo_for_all)) }
                }
                TextButton(onClick = { onUndo(false) }) {
                    Text(stringResource(if (both) R.string.plans_leftover_undo_for_me else R.string.plans_leftover_undo))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
