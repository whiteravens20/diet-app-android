// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R

/**
 * The mark that a model had a part in something: a meal of a plan, a recipe, a plan. The chip says
 * "AI-assisted", and what the model did ([said]) is read out with it; [spelledOut], it stands
 * beside the chip as a sentence, where there is room for one.
 */
@Composable
fun AiMark(said: String, modifier: Modifier = Modifier, spelledOut: Boolean = false) {
    val label = stringResource(R.string.ai_mark_label)
    val container = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    if (!spelledOut) {
        LabelChip(label, modifier.clearAndSetSemantics { contentDescription = "$label: $said" }, container, MaterialTheme.colorScheme.onSurface)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelChip(label, container = container, content = MaterialTheme.colorScheme.onSurface)
        Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
