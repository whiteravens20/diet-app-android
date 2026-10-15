// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A field that shows a day, none where [day] is null, and opens a calendar to choose another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, day: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = day?.let { formatDate(it.toString()) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = label) { picking = true })
    }

    if (picking) {
        // The picker counts days in UTC: a day made into a moment there, and back, is the same day.
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { millis -> onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
                        picking = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = picker) }
    }
}
