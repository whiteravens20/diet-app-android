// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.ui.common.ATTRIBUTION
import net.whiteravens.dietapp.ui.common.ATTRIBUTION_URL
import net.whiteravens.dietapp.ui.common.LICENCE_URL
import net.whiteravens.dietapp.ui.common.SOURCE_URL
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.rememberWebPages

/**
 * What the instance publishes of itself, which version of the application this is, and the legal
 * notices its licence and the additional term of NOTICE require: the attribution with the address
 * of the original source, the licence, the absence of any warranty and where the source is.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onLibraries: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val version by viewModel.version.collectAsStateWithLifecycle()
    val pulled by viewModel.pulled.collectAsStateWithLifecycle()
    val web = rememberWebPages()
    // Read again on being shown again: the instance may have changed what it publishes.
    WhenShownAgain(viewModel::shownAgain)

    PullToRead(pulled, viewModel::pull) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader(title = stringResource(R.string.about_title), onBack = onBack)

            AppCard {
                Title(stringResource(R.string.about_instance))
                Fact(
                    stringResource(R.string.about_server_version),
                    when (val v = version) {
                        ServerVersion.Checked -> BuildConfig.API_VERSION
                        is ServerVersion.Other -> v.server
                        ServerVersion.Unsupported, ServerVersion.Unknown -> stringResource(R.string.about_server_version_unknown)
                    },
                )
                rules?.let { r ->
                    Fact(stringResource(R.string.about_max_profiles), r.maxProfiles.toString())
                    r.maxRepeatsPerRecipe?.let { most ->
                        Fact(stringResource(R.string.about_max_repeats), pluralStringResource(R.plurals.about_max_repeats_times, most, most))
                    }
                }
                Said(stringResource(R.string.about_operator), Modifier.padding(top = 12.dp))
                val contact = rules?.operatorContact
                if (contact != null) {
                    SelectionContainer { Text(contact, style = MaterialTheme.typography.bodyMedium) }
                } else {
                    Said(stringResource(R.string.about_operator_not_set))
                }
                rules?.supportUrl?.let { address ->
                    TextButton(onClick = { web.openOutside(address) }) { Text(stringResource(R.string.about_support)) }
                }
                TextButton(onClick = { web.open("terms") }) { Text(stringResource(R.string.about_terms)) }
            }

            AppCard {
                Title(stringResource(R.string.about_app))
                Fact(stringResource(R.string.about_app_version), BuildConfig.VERSION_NAME)
                Said(stringResource(R.string.about_api_checked, BuildConfig.API_VERSION))
            }

            AppCard {
                Title(stringResource(R.string.about_legal))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer { Said(stringResource(R.string.about_notice, ATTRIBUTION, ATTRIBUTION_URL)) }
                    Said(stringResource(R.string.about_licence))
                    Said(stringResource(R.string.about_warranty))
                    Said(stringResource(R.string.about_changed))
                    Said(stringResource(R.string.about_problems))
                }
                TextButton(onClick = { web.openOutside(LICENCE_URL) }) { Text(stringResource(R.string.about_licence_text)) }
                TextButton(onClick = { web.openOutside("$SOURCE_URL/issues") }) { Text(stringResource(R.string.about_issues)) }
                TextButton(onClick = { web.openOutside("$SOURCE_URL/security/policy") }) { Text(stringResource(R.string.about_security)) }
                TextButton(onClick = { web.openOutside(SOURCE_URL) }) { Text(stringResource(R.string.about_source)) }
                TextButton(onClick = onLibraries) { Text(stringResource(R.string.about_libraries)) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

/** A fact of the instance or of the application: what it is, and its value. */
@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Said(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
