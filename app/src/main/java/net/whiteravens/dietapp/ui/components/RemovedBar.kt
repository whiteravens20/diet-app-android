// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.UNDO_FOR_MILLIS

/** What was removed a moment ago, and the way to take it back while it waits to be sent. */
@Composable
fun RemovedBar(name: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        // The button goes under the words where they need the line, as with a large font.
        NameAndTrail(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            name = { Text(stringResource(R.string.removed_named, name), style = MaterialTheme.typography.bodyMedium) },
        ) {
            TextButton(onClick = onUndo) { Text(stringResource(R.string.common_undo)) }
        }
    }
}

/** How long a removal waits to be taken back: as long as the phone's accessibility settings keep a message with a control. */
@Composable
fun undoForMillis(): Long = LocalAccessibilityManager.current
    ?.calculateRecommendedTimeoutMillis(UNDO_FOR_MILLIS, containsIcons = false, containsText = true, containsControls = true)
    ?: UNDO_FOR_MILLIS
