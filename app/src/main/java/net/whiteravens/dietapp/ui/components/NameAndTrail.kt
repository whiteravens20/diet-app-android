// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A name and what follows it, such as an amount and the buttons of a row, on one line while the name keeps its
 * longest word whole beside them. Where it cannot, on a narrow screen or with a large font, the name has the line
 * to itself and what follows goes under it, at the end: a word is never broken letter by letter.
 */
@Composable
fun NameAndTrail(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    alignment: Alignment.Vertical = Alignment.CenterVertically,
    name: @Composable () -> Unit,
    trail: @Composable () -> Unit,
) {
    Layout(contents = listOf(name, trail), modifier = modifier) { (names, trails), constraints ->
        val named = names.first()
        val after = trails.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val room = if (width == Constraints.Infinity) Constraints.Infinity else width - after.width - gap.roundToPx()
        // What a name needs at the least is its longest word: the width below which a word itself would break.
        if (room != Constraints.Infinity && room < named.minIntrinsicWidth(Constraints.Infinity)) {
            val placed = named.measure(Constraints(maxWidth = width))
            layout(width, placed.height + after.height) {
                placed.place(0, 0)
                after.place(width - after.width, placed.height)
            }
        } else {
            val placed = named.measure(
                if (room == Constraints.Infinity) Constraints() else Constraints(minWidth = room, maxWidth = room),
            )
            val height = maxOf(placed.height, after.height)
            val total = if (width == Constraints.Infinity) placed.width + gap.roundToPx() + after.width else width
            layout(total, height) {
                placed.place(0, alignment.align(placed.height, height))
                after.place(total - after.width, alignment.align(after.height, height))
            }
        }
    }
}
