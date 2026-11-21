// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R

/** A line that opens to say [what]: how something on the screen works, for whoever asks. */
@Composable
internal fun HowItWorks(what: String, open: Boolean, onOpen: (Boolean) -> Unit) =
    HowItWorks(open, onOpen) { Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

/** A line that opens to show [content], how something on the screen works, for whoever asks: the explanations stand behind it. */
@Composable
internal fun HowItWorks(open: Boolean, onOpen: (Boolean) -> Unit, content: @Composable () -> Unit) =
    Folded(stringResource(R.string.common_how_it_works), open, onOpen, content)

/** A line named [label] that opens to show [content]: what is there for whoever asks stands behind it. */
@Composable
internal fun Folded(label: String, open: Boolean, onOpen: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onOpen(!open) }
                .semantics { if (open) collapse { onOpen(false); true } else expand { onOpen(true); true } },
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) content()
    }
}
