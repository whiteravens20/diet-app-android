// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField

/**
 * The AI providers the account brings of its own, as the web's settings show them: each with its
 * model and whether its key is kept and can still be read, removed once that is confirmed; and a
 * form for one more, whose connection can be tested, and its models listed, before it is saved.
 */
@Composable
internal fun AiProvidersCard(state: AiViewModel.UiState, actions: AiProviderActions) {
    var removing by remember { mutableStateOf<AiProviderConfig?>(null) }
    removing?.let { provider ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.account_ai_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        actions.remove(provider.id)
                    },
                ) { Text(stringResource(R.string.account_ai_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_ai_providers), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_ai_providers_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.readFailed != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.account_ai_providers_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.retry) { Text(stringResource(R.string.common_retry)) }
            }
        }
        state.providers?.let { providers ->
            if (providers.isEmpty()) {
                Text(
                    stringResource(R.string.account_ai_providers_none),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                ) {
                    providers.forEachIndexed { index, provider ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProviderRow(provider, enabled = !state.removing, onRemove = { removing = provider })
                    }
                }
            }
        }
        state.removeError?.let {
            Text(
                errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.form == null) {
            OutlinedButton(onClick = actions.open, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.account_ai_add))
            }
        } else {
            ProviderFormFields(state, state.form, actions)
        }
    }
}

/** What the card's rows and form do, handed from the screen's view model. */
internal class AiProviderActions(
    val retry: () -> Unit,
    val open: () -> Unit,
    val cancel: () -> Unit,
    val edit: ((ProviderForm) -> ProviderForm) -> Unit,
    val chooseModel: (String) -> Unit,
    val test: () -> Unit,
    val save: () -> Unit,
    val remove: (String) -> Unit,
)

/**
 * One provider: its name and model, the address of an Ollama server, whether its key is kept or can
 * no longer be read, and its removal, which says whose it is.
 */
@Composable
private fun ProviderRow(provider: AiProviderConfig, enabled: Boolean, onRemove: () -> Unit) {
    val name = providerName(provider.provider)
    val described = stringResource(R.string.account_ai_remove_named, name)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$name · ${provider.model}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.semantics { contentDescription = described }) {
                Text(stringResource(R.string.account_ai_remove), color = MaterialTheme.colorScheme.error)
            }
        }
        provider.baseUrl?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(
                when {
                    provider.keyUnreadable -> R.string.account_ai_key_unreadable
                    provider.hasKey -> R.string.account_ai_key_set
                    else -> R.string.account_ai_no_key
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (provider.keyUnreadable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The form of a provider, as the web's is: the warning that the model chosen decides the quality,
 * the provider, its model (one of those a test listed, once one has), its key, shown as dots, or its
 * Ollama server's address, and its place in the order; saving, testing and leaving it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderFormFields(state: AiViewModel.UiState, form: ProviderForm, actions: AiProviderActions) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.account_ai_model_warning_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(R.string.account_ai_model_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        ChoiceField(
            label = stringResource(R.string.account_ai_provider),
            options = ProviderForm.PROVIDERS,
            selected = form.provider,
            optionLabel = { providerName(it) },
            onSelect = { chosen -> actions.edit { it.copy(provider = chosen) } },
        )
        state.replaced?.let {
            Text(
                stringResource(R.string.account_ai_replaces, providerName(it.provider)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val listed = state.tested?.takeIf { it.ok }?.models.orEmpty()
        if (listed.isNotEmpty()) {
            ChoiceField(
                label = stringResource(R.string.account_ai_pick_model),
                options = listed,
                selected = form.model,
                optionLabel = { it },
                onSelect = actions.chooseModel,
            )
        } else {
            FormField(
                value = form.model,
                onValueChange = { typed -> actions.edit { it.copy(model = typed.take(ProviderForm.MOST_MODEL_CHARACTERS)) } },
                label = stringResource(R.string.account_ai_model),
                placeholder = if (form.ollama) "llama3.1:8b" else "gpt-4o-mini",
                keyboardType = KeyboardType.Uri,
            )
        }
        if (form.ollama) {
            FormField(
                value = form.baseUrl,
                onValueChange = { typed -> actions.edit { it.copy(baseUrl = typed.take(ProviderForm.MOST_CHARACTERS)) } },
                label = stringResource(R.string.account_ai_ollama_url),
                placeholder = "http://localhost:11434",
                problem = stringResource(R.string.account_ai_ollama_url_invalid).takeIf { form.baseUrl.isNotBlank() && !form.addressValid },
                keyboardType = KeyboardType.Uri,
            )
        } else {
            PasswordField(
                value = form.apiKey,
                onValueChange = { typed -> actions.edit { it.copy(apiKey = typed.take(ProviderForm.MOST_CHARACTERS)) } },
                label = stringResource(R.string.account_ai_api_key),
            )
        }
        FormField(
            value = form.priority,
            onValueChange = { typed -> if (typed.length <= 3 && typed.all(Char::isDigit)) actions.edit { it.copy(priority = typed) } },
            label = stringResource(R.string.account_ai_priority),
            hint = stringResource(R.string.account_ai_priority_hint),
            problem = stringResource(R.string.profile_field_between, "0", "${ProviderForm.MOST_PRIORITY}").takeIf { form.place == null },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = actions.save, enabled = !state.saving && form.setup() != null, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(if (state.saving) R.string.account_ai_saving else R.string.account_ai_save))
            }
            OutlinedButton(onClick = actions.test, enabled = !state.testing && form.testable, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(if (state.testing) R.string.account_ai_testing else R.string.account_ai_test))
            }
            TextButton(onClick = actions.cancel) { Text(stringResource(R.string.common_cancel)) }
        }
        state.tested?.let { tested ->
            Text(
                when {
                    !tested.ok -> tested.error ?: stringResource(R.string.error_generic)
                    tested.models.isEmpty() -> stringResource(R.string.account_ai_test_ok)
                    else -> stringResource(R.string.account_ai_test_ok_models)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (tested.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        (state.testError ?: state.saveError)?.let {
            Text(errorMessage(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A provider as it calls itself; one this version does not know, as the API names it. */
internal fun providerName(provider: String): String = when (provider) {
    "openai" -> "OpenAI"
    "anthropic" -> "Anthropic"
    "openrouter" -> "OpenRouter"
    "ollama" -> "Ollama"
    else -> provider
}
