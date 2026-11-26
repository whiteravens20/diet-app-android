// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** From 65 years the fastest pace a profile is planned at, in kilograms a week, as the API's engine holds it. */
const val OLDER_FASTEST_PACE_KG = 0.5

/**
 * What stands behind a calorie target that is not simply maintenance less the chosen pace, in
 * the order and on the terms the web application's profile summary says it: a goal that is held,
 * the warnings for somebody under 18, underweight or from 65, a pace that counts as a slower one,
 * a floor, a pace that was slowed, a correction held, and a target typed below the floor or below
 * the body's needs at rest. [strong] notes are the ones to heed.
 */
sealed interface TargetNote {
    val strong: Boolean get() = false

    data object GoalHeld : TargetNote {
        override val strong get() = true
    }

    data object Minor : TargetNote {
        override val strong get() = true
    }

    data object Underweight : TargetNote {
        override val strong get() = true
    }

    /** From 65, under a body-mass index of 30: the guidelines for older people advise against losing. */
    data object Older : TargetNote

    /** From 65 the pace chosen, [chosenKg] a week, counts as half a kilogram. */
    data class PaceHeldForAge(val chosenKg: Double) : TargetNote

    data class MaintenanceUnderFloor(val floor: Double) : TargetNote

    data class SafetyFloorApplied(val floor: Double, val deficit: Double) : TargetNote

    /**
     * The pace as it counts, [countedKg] a week, asks for [requested] kcal a day less; it was slowed to [deficit] kcal,
     * which loses [weeklyLossKg] a week at first.
     */
    data class DeficitCapped(val requested: Double, val deficit: Double, val weeklyLossKg: Double, val countedKg: Double) : TargetNote

    /** A correction from the real expenditure would take the target lower: it stops at [target]. */
    data class CorrectionHeld(val target: Double) : TargetNote

    data class BelowFloor(val floor: Double) : TargetNote {
        override val strong get() = true
    }

    data class BelowBmr(val bmr: Double) : TargetNote
}

/** The notes of this target; a form that gives the risks of the body itself, beside the pace, asks for the rest alone ([risk] false). */
fun CalorieTarget.notes(risk: Boolean = true): List<TargetNote> = buildList {
    // The pace chosen, and the pace as it counts: from 65 a faster one counts as the fastest a plan keeps.
    val chosen = weeklyLossTarget?.toDoubleOrNull() ?: 0.0
    val counted = if (paceHeldForAge) OLDER_FASTEST_PACE_KG else chosen
    if (risk && goalHeld) add(TargetNote.GoalHeld)
    for (warning in warnings) {
        when {
            !risk -> Unit
            warning == "minor" -> add(TargetNote.Minor)
            warning == "underweight" -> add(TargetNote.Underweight)
        }
    }
    if (risk && "older" in warnings && dailyDeficit > 0) add(TargetNote.Older)
    if (paceHeldForAge) add(TargetNote.PaceHeldForAge(chosen))
    if (maintenanceUnderFloor) add(TargetNote.MaintenanceUnderFloor(safetyFloor))
    if (safetyFloorApplied) add(TargetNote.SafetyFloorApplied(safetyFloor, dailyDeficit))
    // Under the floor the pace is not what set the target: the floor did.
    if (deficitCapped && !safetyFloorApplied && !maintenanceUnderFloor && !goalHeld) {
        add(TargetNote.DeficitCapped(requestedDeficit, dailyDeficit, weeklyLossKg, counted))
    }
    if (correctionHeld) add(TargetNote.CorrectionHeld(dailyTarget))
    if ("below_floor" in warnings) add(TargetNote.BelowFloor(safetyFloor))
    if ("below_bmr" in warnings && source == "manual_override") add(TargetNote.BelowBmr(bmr))
}
