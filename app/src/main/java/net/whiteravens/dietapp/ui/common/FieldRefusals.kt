// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.FieldRefusal
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * How a field says the bound it was refused for: as a number to [Number.decimals] places, in the
 * field's own unit ([Number.inUnit] turns the API's figure into it, and [Number.unit] follows it
 * where the field's label does not say it), or as a count of characters.
 */
sealed interface Bound {
    data class Number(val decimals: Int = 0, val unit: String? = null, val inUnit: (Double) -> Double = { it }) : Bound

    data object Characters : Bound
}

/**
 * What a field says of the API's refusal of it, in the app's language: the kind of refusal and its
 * bound, said as [bound] says; [other] for a refusal of no kind the app words.
 */
@Composable
fun refusalText(refusal: FieldRefusal, bound: Bound = Bound.Number(), other: String = stringResource(R.string.refusal_other)): String =
    when (refusal) {
        is FieldRefusal.TooSmall -> when (bound) {
            Bound.Characters -> {
                val least = refusal.least.toInt()
                if (least <= 1) stringResource(R.string.refusal_empty) else pluralStringResource(R.plurals.refusal_characters_at_least, least, least)
            }
            is Bound.Number -> stringResource(if (refusal.inclusive) R.string.refusal_at_least else R.string.refusal_more_than, bound.say(refusal.least, up = true))
        }
        is FieldRefusal.TooBig -> when (bound) {
            Bound.Characters -> refusal.most.toInt().let { most -> pluralStringResource(R.plurals.refusal_characters_at_most, most, most) }
            is Bound.Number -> stringResource(if (refusal.inclusive) R.string.refusal_at_most else R.string.refusal_less_than, bound.say(refusal.most, up = false))
        }
        FieldRefusal.Other -> other
    }

/** What a field of numbers says: that what it holds is no number ([notANumber]), or why the API refused it. */
@Composable
fun numberProblem(notANumber: Boolean, refused: FieldRefusal?, bound: Bound = Bound.Number(), whole: Boolean = true): String? = when {
    notANumber -> stringResource(if (whole) R.string.refusal_not_whole else R.string.refusal_not_a_number)
    refused != null -> refusalText(refused, bound)
    else -> null
}

/**
 * [figure] in the field's unit and to its places, rounded towards what the field takes: a least
 * up and a most down, so that what the bound says is never itself refused.
 */
@Composable
private fun Bound.Number.say(figure: Double, up: Boolean): String {
    val scale = 10.0.pow(decimals)
    val shown = inUnit(figure) * scale
    // A figure that already has no more places stays as it is, whatever the last bit of its double says;
    // a nought rounded up from just under it is a nought, not a negative one.
    val rounded = (if (up) ceil(shown - EPSILON) else floor(shown + EPSILON)) / scale + 0.0
    val number = formatNumber(rounded, decimals)
    return if (unit == null) number else "$number $unit"
}

private const val EPSILON = 1e-9
