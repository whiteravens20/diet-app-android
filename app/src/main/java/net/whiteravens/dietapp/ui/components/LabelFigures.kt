// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.LabelFigure
import net.whiteravens.dietapp.domain.LabelLimitNote
import net.whiteravens.dietapp.domain.LabelNutrient
import net.whiteravens.dietapp.domain.LabelTotals
import net.whiteravens.dietapp.ui.common.formatNumber

/** The name of one of the four, as labels give it. */
@Composable
fun labelNutrientName(nutrient: LabelNutrient): String = stringResource(
    when (nutrient) {
        LabelNutrient.FIBER -> R.string.label_fiber
        LabelNutrient.SUGARS -> R.string.label_sugars
        LabelNutrient.SALT -> R.string.label_salt
        LabelNutrient.SATURATED_FAT -> R.string.label_saturated_fat
    },
)

/** Grams as a figure of the four is written: `6 g`, `0,3 g`. */
@Composable
private fun labelGrams(grams: Double): String = stringResource(R.string.amount_g, formatNumber(grams, 1))

/**
 * A figure as a person reads it: `6 g`, `≥ 13,1 g` for a sum a part of which gives no figure, `—`
 * where nothing of it does. The API worked it out and rounded it; nothing here does.
 */
@Composable
fun labelFigureText(figure: LabelFigure?): String =
    if (figure == null) "—" else (if (figure.atLeast) "≥ " else "") + labelGrams(figure.grams)

/** Fibre, sugars, salt and saturated fat of a serving, two by two: a value above its name, as the macronutrients are shown. */
@Composable
fun LabelGrid(totals: LabelTotals, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabelNutrient.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { nutrient ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(labelFigureText(totals[nutrient]), style = MaterialTheme.typography.labelLarge)
                        Text(
                            labelNutrientName(nutrient),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        LabelLegend(totals)
    }
}

/** The same in one line, for a day or a meal: `Fibre 12 g · Sugars ≥ 26,2 g · …`. */
@Composable
fun LabelLine(totals: LabelTotals, modifier: Modifier = Modifier) {
    val parts = LabelNutrient.entries.map { "${labelNutrientName(it)} ${labelFigureText(totals[it])}" }
    Text(
        inOneLine(parts),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Pairs of a name and its figure in one line, `Fibre 12 g · Sugars ≥ 26,2 g`, which a narrow screen
 * breaks only after a dot: a pair keeps its name, figure and unit together, and the dot stays with
 * the pair before it, so no line starts with a lone unit, figure or dot.
 */
fun inOneLine(pairs: List<String>): String =
    pairs.joinToString("$NO_BREAK_SPACE· ") { it.replace(' ', NO_BREAK_SPACE) }

private const val NO_BREAK_SPACE = '\u00A0'

/** What `≥` and `—` stand for, said once under the figures that use them; nothing where none does. */
@Composable
fun LabelLegend(totals: LabelTotals, modifier: Modifier = Modifier) {
    val atLeast = stringResource(R.string.label_legend_at_least)
    val unknown = stringResource(R.string.label_legend_unknown)
    val said = listOfNotNull(atLeast.takeIf { totals.anyAtLeast }, unknown.takeIf { totals.anyUnknown })
    if (said.isEmpty()) return
    Text(
        said.joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The limits of the profile a day crosses, a line each, in the colour the app writes its notes in. */
@Composable
fun LabelNotes(notes: List<LabelLimitNote>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        notes.forEach { note ->
            Text(
                stringResource(
                    if (note.atMost) R.string.label_note_at_most else R.string.label_note_at_least,
                    labelNutrientName(note.nutrient),
                    labelFigureText(note.figure),
                    labelGrams(note.limit),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
