// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The space between two fields that stand side by side, or one under the other. */
private val FIELD_GAP = 8.dp

/** A row of short fields: the width the row asks at the phone's usual size of text, and how its fields share it. */
internal class FieldRow(val need: Dp, vararg val weights: Float)

/**
 * Short fields side by side, sharing the [row] by its weights, where they fit at the font's size: the row's need grows
 * with the font. Where the row is narrower, the fields stand one under another, each the whole width. [fields] places
 * each field by its index among the weights.
 */
@Composable
internal fun SideBySide(
    row: FieldRow,
    modifier: Modifier = Modifier,
    fields: @Composable (place: (Int) -> Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= row.need * LocalDensity.current.fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
                fields { index -> Modifier.weight(row.weights[index]) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
                fields { Modifier }
            }
        }
    }
}
