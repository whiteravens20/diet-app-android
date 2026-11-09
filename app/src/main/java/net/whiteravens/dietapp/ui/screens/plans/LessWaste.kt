// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.LessWasteEffect
import net.whiteravens.dietapp.ui.common.formatNumber

/**
 * The choice of a plan that wastes less, with what it costs said beside it, as the web's: in figures
 * for the plan's diet, as the instance measured them ([effect]), and in words where there are none,
 * for the custom diet or while the figures are on their way; then the limits it works inside.
 */
@Composable
internal fun LessWasteSwitch(effect: LessWasteEffect?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cost = effect?.let {
        stringResource(
            R.string.plans_less_waste_hint,
            formatNumber(it.pantryLess),
            formatNumber(it.pantryNow),
            formatNumber(it.packsLess),
            formatNumber(it.packsNow),
            formatNumber(it.fewerRecipes),
        )
    } ?: stringResource(R.string.plans_less_waste_hint_custom)
    // The box stands at its label, the first of many lines, as a box in a row of one line stands; the
    // lines below say what it costs. The whole is one control, a finger high at least.
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.padding(start = 8.dp, top = 14.dp, bottom = 12.dp)) {
            // Its own colour: a dialog's text is muted, and a label is not.
            Text(
                stringResource(R.string.plans_less_waste),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(cost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.plans_less_waste_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
