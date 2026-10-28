// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * Why the API refused what a field holds, as its refusal says it: under the least the field takes
 * ([TooSmall]) or over the most ([TooBig]), with that bound and whether the bound itself is taken;
 * or for something else, which the field says in words of its own ([Other]).
 */
sealed interface FieldRefusal {
    data class TooSmall(val least: Double, val inclusive: Boolean) : FieldRefusal

    data class TooBig(val most: Double, val inclusive: Boolean) : FieldRefusal

    data object Other : FieldRefusal
}
