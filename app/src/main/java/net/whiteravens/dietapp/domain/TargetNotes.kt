// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * What stands behind a calorie target that is not simply maintenance less the chosen pace, in
 * the order and on the terms the web application's profile summary says it: a goal that is held,
 * the warnings for somebody under 18 or underweight, a floor, a pace that was slowed, and a target
 * typed below the floor or below the body's needs at rest. [strong] notes are the ones to heed.
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

    data class MaintenanceUnderFloor(val floor: Double) : TargetNote

    data class SafetyFloorApplied(val floor: Double, val deficit: Double) : TargetNote

    data class DeficitCapped(val requested: Double, val deficit: Double) : TargetNote

    data class BelowFloor(val floor: Double) : TargetNote {
        override val strong get() = true
    }

    data class BelowBmr(val bmr: Double) : TargetNote
}

/** The notes of this target; a form that gives the risk of a loss itself, with the box that answers it, asks for the rest alone ([risk] false). */
fun CalorieTarget.notes(risk: Boolean = true): List<TargetNote> = buildList {
    if (risk && goalHeld) add(TargetNote.GoalHeld)
    for (warning in warnings) {
        when {
            !risk -> Unit
            warning == "minor" -> add(TargetNote.Minor)
            warning == "underweight" -> add(TargetNote.Underweight)
        }
    }
    if (maintenanceUnderFloor) add(TargetNote.MaintenanceUnderFloor(safetyFloor))
    if (safetyFloorApplied) add(TargetNote.SafetyFloorApplied(safetyFloor, dailyDeficit))
    // Under the floor the pace is not what set the target: the floor did.
    if (deficitCapped && !safetyFloorApplied && !maintenanceUnderFloor && !goalHeld) {
        add(TargetNote.DeficitCapped(requestedDeficit, dailyDeficit))
    }
    if ("below_floor" in warnings) add(TargetNote.BelowFloor(safetyFloor))
    if ("below_bmr" in warnings && source == "manual_override") add(TargetNote.BelowBmr(bmr))
}
