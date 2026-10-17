// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

@file:OptIn(ExperimentalLayoutApi::class)

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton

/** Whose figures the calculator begins with until one is picked: the first profile's. */
private const val FIRST = "first"

/** The calculator begins with nobody's figures. */
private const val EMPTY = "empty"

/**
 * The calculator: what a body, an activity, a pace and a diet come to in a day, worked out as
 * they are typed and never kept. It begins with the figures of a profile of the account, to be
 * changed and looked at ("what if I moved more"), or with an empty form.
 */
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    onProfiles: () -> Unit,
    profilesViewModel: ProfilesViewModel = hiltViewModel(),
    viewModel: ProfileFormViewModel = hiltViewModel(key = "calculator"),
) {
    val profiles by profilesViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LaunchedEffect(profilesViewModel) { profilesViewModel.load() }

    var picked by rememberSaveable { mutableStateOf(FIRST) }
    val list = profiles.list
    val from = when (picked) {
        FIRST -> list.firstOrNull()
        EMPTY -> null
        else -> list.find { it.id == picked }
    }
    // The form is made anew for whoever it begins with, once the profiles are read.
    LaunchedEffect(from?.id, profiles.loading) { if (!profiles.loading) viewModel.startFrom(from) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.calculator_title), subtitle = stringResource(R.string.calculator_subhead), onBack = onBack)

        // The form waits for the profiles: one that began empty and then filled itself would take what was typed with it.
        if (profiles.loading || state.loading) {
            Skeleton(Modifier.fillMaxWidth().height(240.dp))
            return@Column
        }
        if (list.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.calculator_start_from),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                )
                list.forEach { profile ->
                    FilterChip(selected = from?.id == profile.id, onClick = { picked = profile.id }, label = { Text(profile.figures.name) })
                }
                FilterChip(selected = from == null, onClick = { picked = EMPTY }, label = { Text(stringResource(R.string.calculator_empty)) })
            }
        }

        val form = state.form
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyFields(form, state.unit, emptySet(), viewModel::edit)
                DietFields(form, emptySet(), rules, viewModel::edit, meals = false)
                PaceChoice(form, state.unit, state.preview?.paces, viewModel::edit)
                OwnTargetField(form, emptySet(), viewModel::edit)
                RiskBox(
                    reasons = state.risks,
                    acknowledged = form.acknowledgeRisk,
                    onAcknowledge = { ticked -> viewModel.edit { it.copy(acknowledgeRisk = ticked) } },
                    kept = false,
                )
            }
        }
        CaloriePreviewCard(state.preview, state.behind, state.waiting, state.previewFailed)

        val link = stringResource(R.string.calculator_not_kept_link)
        val sentence = stringResource(R.string.calculator_not_kept, link)
        val at = sentence.lastIndexOf(link)
        Text(
            buildAnnotatedString {
                append(sentence.substring(0, at))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "profiles",
                        styles = TextLinkStyles(SpanStyle(fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
                    ) { onProfiles() },
                ) { append(link) }
                append(sentence.substring(at + link.length))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
