// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R

/** The rules of freezing at home, in the order they come up: packing, freezing, thawing. */
private val RULES = listOf(
    R.string.freezing_rule_label,
    R.string.freezing_rule_use_by,
    R.string.freezing_rule_thaw,
    R.string.freezing_rule_refreeze,
)

/**
 * What to know when freezing a food: the size to split it into where plans eat it ([portionLine], said by whoever knows
 * the plans; null where nothing is to be split), and, folded under "Jak mrozić", the rules of freezing and thawing it
 * safely. A plan takes a package out of the freezer only whole, and what is thawed is eaten within two days: a package
 * no bigger than two days eat is one the plan can use. [key] names what the fold stands for, so that it keeps its state
 * as its own and no other's.
 */
@Composable
internal fun FreezingTips(portionLine: String?, key: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(key = "freezing-tips:$key") { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        portionLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Folded(stringResource(R.string.freezing_rules_title), open, onOpen = { open = it }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RULES.forEach { rule ->
                    Text("• ${stringResource(rule)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
