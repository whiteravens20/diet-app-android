// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.LessWasteEffect
import net.whiteravens.dietapp.domain.ProfilePreferences
import net.whiteravens.dietapp.domain.WasteLevel
import net.whiteravens.dietapp.domain.WasteRules
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.Text

/** A profile's own limits on repeats: the most days running and times in seven days of one recipe. */
data class Repeats(val daysInARow: Int, val timesAWeek: Int) {
    companion object {
        fun of(preferences: ProfilePreferences) = Repeats(preferences.maxConsecutiveDaysSameMeal, preferences.maxTimesPerWeekSameMeal)
    }
}

/**
 * The choice of how much a plan does to waste less, as the web's: each level with what it gives and
 * costs said beside it, in figures for the plan's diet as the instance measured them ([less], [none]),
 * and in words where there are none, for the custom diet or while the figures are on their way. Of a
 * plan that wastes nothing it says which limits of the profile ([repeats], once known) it loosens, and
 * to what.
 */
@Composable
internal fun WasteLevelChoice(
    less: LessWasteEffect?,
    none: LessWasteEffect?,
    rules: WasteRules?,
    repeats: Repeats?,
    level: WasteLevel,
    onChange: (WasteLevel) -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(stringResource(R.string.plans_waste_level), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Level(
            stringResource(R.string.plans_waste_usual),
            listOf(stringResource(R.string.plans_waste_usual_hint)),
            selected = level == WasteLevel.USUAL,
        ) { onChange(WasteLevel.USUAL) }
        Level(
            stringResource(R.string.plans_less_waste),
            listOfNotNull(
                less?.let {
                    stringResource(
                        R.string.plans_less_waste_hint,
                        formatNumber(it.pantryLess),
                        formatNumber(it.pantryNow),
                        formatNumber(it.packsLess),
                        formatNumber(it.packsNow),
                        formatNumber(it.fewerRecipes),
                    )
                } ?: stringResource(R.string.plans_less_waste_hint_custom),
                rules?.let { stringResource(R.string.plans_less_waste_rests, formatNumber(it.restsLess)) },
                stringResource(R.string.plans_less_waste_limits),
            ),
            selected = level == WasteLevel.LESS,
        ) { onChange(WasteLevel.LESS) }
        Level(
            stringResource(R.string.plans_no_waste),
            rules?.let { noWasteSaid(none, it, repeats) }.orEmpty(),
            selected = level == WasteLevel.NONE,
        ) { onChange(WasteLevel.NONE) }
    }
}

/** What a plan that wastes nothing gives, what it loosens of the profile's limits and what it keeps. */
@Composable
private fun noWasteSaid(effect: LessWasteEffect?, rules: WasteRules, repeats: Repeats?): List<String> {
    val meatDays = formatNumber(rules.meatAndFishDays)
    val otherDays = formatNumber(rules.otherDays)
    return listOfNotNull(
        effect?.let {
            stringResource(
                R.string.plans_no_waste_hint,
                meatDays,
                otherDays,
                formatNumber(it.packsLess),
                formatNumber(it.packsNow),
                formatNumber(it.pantryLess),
                formatNumber(it.pantryNow),
                formatNumber(it.fewerRecipes),
            )
        } ?: stringResource(R.string.plans_no_waste_hint_custom, meatDays, otherDays),
        repeats?.let { own ->
            // The planner takes the looser of the two limits: the level's or the profile's own.
            val days = maxOf(own.daysInARow, rules.daysInARow)
            val times = maxOf(own.timesAWeek, rules.timesAWeek)
            val inARow = pluralStringResource(R.plurals.plans_no_waste_days, days, formatNumber(days))
            val aWeek = pluralStringResource(R.plurals.plans_no_waste_times, times, formatNumber(times))
            stringResource(
                R.string.plans_no_waste_repeats,
                if (own.daysInARow < rules.daysInARow) stringResource(R.string.plans_no_waste_loosened, inARow, formatNumber(own.daysInARow)) else inARow,
                if (own.timesAWeek < rules.timesAWeek) stringResource(R.string.plans_no_waste_loosened, aWeek, formatNumber(own.timesAWeek)) else aWeek,
            )
        },
        stringResource(R.string.plans_no_waste_kept, formatNumber(rules.restsNone)),
    )
}

/**
 * One level: its button stands at its label, the first of many lines, as a button in a row of one line
 * stands; the lines below say what the level gives and costs. The whole is one control, a finger high at least.
 */
@Composable
private fun Level(label: String, said: List<String>, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.padding(start = 8.dp, top = 14.dp, bottom = 12.dp)) {
            // Its own colour: a dialog's text is muted, and a label is not.
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            said.forEachIndexed { index, line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (index > 0) Modifier.padding(top = 4.dp) else Modifier,
                )
            }
        }
    }
}
