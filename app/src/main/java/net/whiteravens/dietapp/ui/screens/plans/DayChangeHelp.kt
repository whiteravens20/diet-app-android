// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.components.Text

/**
 * How a day of a plan is changed with the user's own meals kept: what stays, which way to choose when, from when, and
 * what a change takes with it; with the two ways that give the day other dishes, opened for the day at hand. A way
 * is null where the plan cannot be changed now, and its button is left out.
 */
@Composable
internal fun DayChangeHelpDialog(
    /** The day it is opened on, `2026-11-25`. */
    date: String,
    /** The plan is one of a joint plan: a day is made again for both, or for one. */
    joint: Boolean,
    onVariants: (() -> Unit)?,
    onReroll: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_day_help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDayDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HelpPart(R.string.plans_day_help_kept_title, listOf(R.string.plans_day_help_kept), listed = false)
                HelpPart(
                    R.string.plans_day_help_ways_title,
                    listOf(
                        R.string.plans_day_help_way_resize,
                        R.string.plans_day_help_way_swap,
                        R.string.plans_day_help_way_variants,
                        R.string.plans_day_help_way_reroll,
                    ),
                )
                HelpPart(R.string.plans_day_help_when_title, listOf(R.string.plans_day_help_when), listed = false)
                HelpPart(
                    R.string.plans_day_help_affects_title,
                    listOfNotNull(
                        R.string.plans_day_help_affects_list,
                        R.string.plans_day_help_affects_diary,
                        R.string.plans_day_help_affects_cooking,
                        R.string.plans_day_help_affects_joint.takeIf { joint },
                        R.string.plans_day_help_affects_undo,
                    ),
                )
                onVariants?.let { variants ->
                    OutlinedButton(onClick = variants, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.plans_other_ways))
                    }
                }
                onReroll?.let { reroll ->
                    OutlinedButton(onClick = reroll) { Text(stringResource(R.string.plans_change_day)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** A part of the window: its heading, and what it says, a line to each where it is a list. */
@Composable
private fun HelpPart(@StringRes title: Int, said: List<Int>, listed: Boolean = true) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    said.forEach { one ->
        val text = stringResource(one)
        Text(
            if (listed) "• $text" else text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
