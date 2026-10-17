// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.SecondFactorSetup
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.QrCode
import net.whiteravens.dietapp.ui.screens.account.SecondFactorViewModel.Purpose
import net.whiteravens.dietapp.ui.screens.account.SecondFactorViewModel.Step
import java.time.ZoneId

/** With this few recovery codes left the card says so. */
private const val FEW_CODES = 3

/**
 * Two-step sign-in of the account, as the web's settings show it: whether it is on, setting it up
 * with an authenticator app, the recovery codes, and switching it off again. On the phone the secret
 * is handed to an authenticator app on the same phone by its link, and shown as text to type, and the
 * QR code is drawn as well, for an app on another device. [onOthersSignedOut] is told when switching
 * it on signed the account's other sessions out.
 */
@Composable
internal fun SecondFactorCard(
    state: SecondFactorViewModel.UiState,
    actions: SecondFactorActions,
    onOthersSignedOut: () -> Unit,
) {
    val step = state.step
    LaunchedEffect(step) {
        if ((step as? Step.Codes)?.fresh == true) onOthersSignedOut()
    }
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_second_factor), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_second_factor_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.readFailed != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.account_second_factor_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.load) { Text(stringResource(R.string.common_retry)) }
            }
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                Step.Rest -> AtRest(state, actions)
                Step.Password -> PasswordStep(state, actions)
                is Step.Scan -> ScanStep(step.setup, state, actions)
                is Step.Codes -> CodesStep(step, actions)
                is Step.Proof -> ProofStep(step.purpose, state, actions)
            }
        }
    }
}

/** What the card's steps do, handed from the screen's view model. */
internal class SecondFactorActions(
    val load: () -> Unit,
    val setUp: () -> Unit,
    val prove: (Purpose) -> Unit,
    val cancel: () -> Unit,
    val saved: () -> Unit,
    val typePassword: (String) -> Unit,
    val typeCode: (String) -> Unit,
    val continueWithPassword: () -> Unit,
    val switchOn: () -> Unit,
    val submitProof: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AtRest(state: SecondFactorViewModel.UiState, actions: SecondFactorActions) {
    val now = state.secondFactor ?: return
    Text(
        if (now.enabled && now.since != null) {
            stringResource(R.string.account_second_factor_on_since, formatDate(now.since.atZone(ZoneId.systemDefault()).toLocalDate().toString()))
        } else {
            stringResource(R.string.account_second_factor_off)
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
    if (now.enabled) {
        val left = pluralStringResource(R.plurals.account_second_factor_codes_left, now.recoveryCodesLeft, now.recoveryCodesLeft)
        Text(
            if (now.recoveryCodesLeft <= FEW_CODES) "$left ${stringResource(R.string.account_second_factor_codes_low)}" else left,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Problem(state.error)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (now.enabled) {
            OutlinedButton(onClick = { actions.prove(Purpose.NEW_CODES) }, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.account_second_factor_new_codes))
            }
            OutlinedButton(onClick = { actions.prove(Purpose.SWITCH_OFF) }, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.account_second_factor_switch_off))
            }
        } else {
            Button(onClick = actions.setUp, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.account_second_factor_set_up))
            }
        }
    }
}

@Composable
private fun PasswordStep(state: SecondFactorViewModel.UiState, actions: SecondFactorActions) {
    PasswordField(state.password, actions.typePassword, stringResource(R.string.account_password_current))
    Problem(state.error)
    Buttons(
        submit = stringResource(R.string.account_second_factor_continue),
        enabled = !state.busy && state.password.isNotEmpty(),
        onSubmit = actions.continueWithPassword,
        busy = state.busy,
        onCancel = actions.cancel,
    )
}

/**
 * The secret, three ways: by its link, for an authenticator app on this phone; as text, grouped in
 * fours, to type into one; and as a QR code, for one on another device. Then its first code.
 */
@Composable
private fun ScanStep(setup: SecondFactorSetup, state: SecondFactorViewModel.UiState, actions: SecondFactorActions) {
    val context = LocalContext.current
    var noApp by remember(setup) { mutableStateOf(false) }
    val keyLabel = stringResource(R.string.account_second_factor_key)
    Text(stringResource(R.string.account_second_factor_open_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(
        onClick = {
            noApp = try {
                context.startActivity(Intent(Intent.ACTION_VIEW, setup.uri.toUri()))
                false
            } catch (e: ActivityNotFoundException) {
                true
            }
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(stringResource(R.string.account_second_factor_open))
    }
    if (noApp) {
        Text(
            stringResource(R.string.account_second_factor_no_app),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(stringResource(R.string.account_second_factor_type_instead), style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(grouped(setup.secret), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        TextButton(onClick = { copySensitive(context, keyLabel, setup.secret) }) {
            Text(stringResource(R.string.account_second_factor_copy_key))
        }
    }
    Text(stringResource(R.string.account_second_factor_scan), style = MaterialTheme.typography.bodyMedium)
    QrCode(setup.uri, stringResource(R.string.account_second_factor_qr))
    CodeField(
        state.code,
        actions.typeCode,
        stringResource(R.string.account_second_factor_code),
        hint = stringResource(R.string.account_second_factor_code_hint),
        keyboardType = KeyboardType.NumberPassword,
    )
    Problem(state.error)
    Buttons(
        submit = stringResource(R.string.account_second_factor_switch_on),
        enabled = !state.busy && state.code.isNotBlank(),
        onSubmit = actions.switchOn,
        busy = state.busy,
        onCancel = actions.cancel,
    )
}

/** The recovery codes, this once, to copy or write down; the card leaves them once they are saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodesStep(step: Step.Codes, actions: SecondFactorActions) {
    val context = LocalContext.current
    var copied by remember(step) { mutableStateOf(false) }
    val title = stringResource(R.string.account_second_factor_recovery_title)
    if (step.fresh) {
        Text(
            stringResource(R.string.account_second_factor_on_now),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Text(
        stringResource(R.string.account_second_factor_recovery_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SelectionContainer {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            step.recoveryCodes.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { code ->
                        Text(code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Text("", modifier = Modifier.weight(1f))
                }
            }
        }
    }
    // A line of its own for a button the other leaves too little room, as the web's buttons wrap.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                copySensitive(context, title, step.recoveryCodes.joinToString("\n"))
                copied = true
            },
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(stringResource(R.string.account_second_factor_copy))
        }
        Button(onClick = actions.saved, shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.account_second_factor_saved))
        }
    }
    if (copied) {
        Text(
            stringResource(R.string.account_second_factor_copied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProofStep(purpose: Purpose, state: SecondFactorViewModel.UiState, actions: SecondFactorActions) {
    Text(
        stringResource(if (purpose == Purpose.SWITCH_OFF) R.string.account_second_factor_switch_off_body else R.string.account_second_factor_new_codes_body),
        style = MaterialTheme.typography.bodyMedium,
    )
    PasswordField(state.password, actions.typePassword, stringResource(R.string.account_password_current))
    CodeField(state.code, actions.typeCode, stringResource(R.string.account_second_factor_code_or_recovery), keyboardType = KeyboardType.Password)
    Problem(state.error)
    Buttons(
        submit = stringResource(if (purpose == Purpose.SWITCH_OFF) R.string.account_second_factor_switch_off else R.string.account_second_factor_new_codes),
        enabled = !state.busy && state.password.isNotEmpty() && state.code.isNotBlank(),
        onSubmit = actions.submitProof,
        busy = state.busy,
        onCancel = actions.cancel,
    )
}

/** A code typed: one the authenticator app shows, or a recovery code; at most [CODE_MAX] characters, as the API takes. */
@Composable
private fun CodeField(value: String, onValueChange: (String) -> Unit, label: String, hint: String? = null, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= CODE_MAX) onValueChange(it) },
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The most characters of a code, or of a recovery code, the API takes. */
private const val CODE_MAX = 40

@Composable
private fun Buttons(submit: String, enabled: Boolean, onSubmit: () -> Unit, busy: Boolean, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSubmit, enabled = enabled, shape = MaterialTheme.shapes.medium) { Text(submit) }
        TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.common_cancel)) }
    }
}

@Composable
private fun Problem(error: Throwable?) {
    error?.let {
        Text(errorMessage(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** A key in groups of four, as it is easier to read off one screen and type into another. */
private fun grouped(key: String): String = key.chunked(4).joinToString(" ")

/**
 * Puts [text] where it can be pasted, marked sensitive: the system's preview of what was copied
 * does not show it (`ClipDescription.EXTRA_IS_SENSITIVE`, a key older systems read as well).
 */
private fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
