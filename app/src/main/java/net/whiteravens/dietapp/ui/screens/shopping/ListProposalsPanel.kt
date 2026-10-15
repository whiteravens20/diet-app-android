// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.ListProposal
import net.whiteravens.dietapp.domain.ListProposals
import net.whiteravens.dietapp.ui.common.MARK
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.emphasized
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.components.AiMark

/**
 * What a shopping list could do without, asked for on the list: rows that one ingredient could
 * stand for, and packs that will be left over. The API works them out; this shows them, asks a
 * model to weigh them where the user wants that, and leads to the plan at the day it concerns,
 * where a substitution is made or what would use a pack up is found.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListProposalsPanel(
    found: ListProposals?,
    asking: Boolean,
    aiOn: Boolean?,
    enabled: Boolean,
    listFrom: String,
    onAsk: (weigh: Boolean) -> Unit,
    onOpenPlanAt: (day: String) -> Unit,
) {
    val any = found?.proposals?.isNotEmpty() == true
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAsk(false) }, enabled = enabled && !asking) {
                Text(stringResource(if (asking && found == null) R.string.shopping_proposals_asking else R.string.shopping_proposals_ask))
            }
            if (any) {
                TextButton(onClick = { onAsk(true) }, enabled = enabled && aiOn == true && !asking) {
                    Text(stringResource(if (asking) R.string.weigh_asking else R.string.weigh_ask))
                }
            }
        }
        if (any) Note(stringResource(if (aiOn == false) R.string.ai_off_note else R.string.weigh_tooltip))
        // How asking a model went, said where the proposals are and read out.
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when {
                found == null -> Unit
                found.weighed -> Note(stringResource(R.string.weigh_weighed))
                found.fallbackReason != null -> fallbackText(found.fallbackReason)?.let { Note(it, MaterialTheme.colorScheme.tertiary) }
            }
        }
        if (found != null && !any) Note(stringResource(R.string.shopping_proposals_none))
        found?.proposals?.forEach { proposal ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (proposal) {
                        is ListProposal.Merge -> {
                            val meals = pluralStringResource(R.plurals.shopping_proposal_meals, proposal.meals.size, proposal.meals.size)
                            val packs = pluralStringResource(R.plurals.shopping_proposal_packs, proposal.packsSaved, proposal.packsSaved)
                            Text(emphasized(stringResource(R.string.shopping_proposal_merge, MARK, proposal.from, meals, packs), proposal.to), style = MaterialTheme.typography.bodyMedium)
                            proposal.reason?.let { AiMark(stringResource(R.string.ai_mark_weighed, it), spelledOut = true) }
                            proposal.meals.forEach { meal -> Note("${formatDayDate(meal.date)} · ${meal.title}") }
                            proposal.meals.firstOrNull()?.let { first ->
                                TextButton(onClick = { onOpenPlanAt(first.date) }) { Text(stringResource(R.string.shopping_proposal_merge_how)) }
                            }
                            Note(stringResource(R.string.shopping_proposal_merge_then))
                        }
                        is ListProposal.Leftover -> {
                            Text(
                                emphasized(stringResource(R.string.shopping_proposal_leftover, MARK, amountLabel(proposal.left.toDouble(), proposal.unit), amountLabel(proposal.packSize, proposal.unit)), proposal.name),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            proposal.reason?.let { AiMark(stringResource(R.string.ai_mark_weighed, it), spelledOut = true) }
                            TextButton(onClick = { onOpenPlanAt(listFrom) }) { Text(stringResource(R.string.shopping_proposal_leftover_how)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
