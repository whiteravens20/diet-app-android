// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R

/**
 * What a change of a plan says before it is made where a shopping list covers a day it changes: the
 * list will say it is out of date, and making it again counts what is at home already. Nothing where
 * no list covers them; [several] where the change is of more than one day.
 */
@Composable
internal fun ListNote(listed: Boolean, several: Boolean = false) {
    if (!listed) return
    Text(
        stringResource(if (several) R.string.plans_list_out_of_date_days else R.string.plans_settings_list_out_of_date),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
