// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.unlock

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.AppMark
import net.whiteravens.dietapp.ui.components.Text

/**
 * What the application shows while the gate is closed: its name, why it is closed, and what can be done. Nothing of
 * the account is composed behind it: the screens wait until the gate opens.
 */
@Composable
fun LockedScreen(gate: Gate, onUnlock: () -> Unit, onSignOut: () -> Unit, onVoidedSeen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // The product's own mark where it introduces itself, as the launcher shows it.
        AppMark()
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        when (gate) {
            Gate.Voided -> {
                Text(stringResource(R.string.unlock_voided), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Button(onClick = onVoidedSeen, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auth_sign_in_button))
                }
            }
            Gate.CannotAsk -> {
                Text(stringResource(R.string.unlock_cannot_ask), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Button(onClick = onSignOut, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_sign_out))
                }
            }
            else -> {
                Text(stringResource(R.string.unlock_locked), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.unlock_locked_about else R.string.unlock_locked_about_fingerprint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onUnlock, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.unlock_button))
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.account_sign_out)) }
            }
        }
    }
}
