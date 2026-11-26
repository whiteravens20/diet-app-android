// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.whiteravens.dietapp.data.remote.CalorieCalculationDto
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The notes a calorie target comes with, as the web application's profile summary has them. The
 * targets are the calorie answer a running backend gave, with the fields each case is about set.
 */
class TargetNotesTest {
    private val json = AppModule.json()

    private fun target(vararg changes: Pair<String, Any>): CalorieTarget {
        val captured = json.parseToJsonElement(fixture("profile-calories")).jsonObject
        val changed = captured + changes.associate { (key, value) ->
            key to when (value) {
                is List<*> -> JsonArray(value.map { JsonPrimitive(it as String) })
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        return json.decodeFromJsonElement(CalorieCalculationDto.serializer(), JsonObject(changed)).toDomain()
    }

    /** Nothing behind the target: no hold, no floor, no slowed pace, no warning. */
    private val plain = arrayOf<Pair<String, Any>>(
        "goalHeld" to false,
        "deficitCapped" to false,
        "safetyFloorApplied" to false,
        "maintenanceUnderFloor" to false,
        "warnings" to emptyList<String>(),
    )

    @Test
    fun `a target that is maintenance less the pace comes with no note`() {
        assertEquals(emptyList<TargetNote>(), target(*plain).notes())
    }

    @Test
    fun `a held goal of somebody under 18 says so first, and the slowed pace is not named`() {
        val notes = target(*plain, "goalHeld" to true, "warnings" to listOf("minor"), "deficitCapped" to true).notes()

        assertEquals(listOf(TargetNote.GoalHeld, TargetNote.Minor), notes)
    }

    @Test
    fun `a form that gives the risk of a loss itself asks for the other notes alone`() {
        val notes = target(*plain, "goalHeld" to true, "warnings" to listOf("minor", "underweight", "below_floor"), "safetyFloor" to 1200).notes(risk = false)

        assertEquals(listOf(TargetNote.BelowFloor(1200.0), TargetNote.Help), notes)
    }

    @Test
    fun `a pace slowed to a quarter of maintenance is named with what was asked, what is taken and what it loses at first`() {
        val notes = target(*plain, "weeklyLossTarget" to "1.0", "deficitCapped" to true, "requestedDeficit" to 1100, "dailyDeficit" to 700, "weeklyLossKg" to 0.64).notes()

        assertEquals(listOf(TargetNote.DeficitCapped(1100.0, 700.0, 0.64, 1.0)), notes)
    }

    @Test
    fun `from 65 a faster pace counts as the slower one, and older people under a body-mass index of 30 are told what the guidelines advise`() {
        val notes = target(*plain, "weeklyLossTarget" to "1.0", "paceHeldForAge" to true, "warnings" to listOf("older"), "dailyDeficit" to 550).notes()
        assertEquals(listOf(TargetNote.Older, TargetNote.PaceHeldForAge(1.0)), notes)
        // Slowed further by a quarter of maintenance: what that asked for is the pace as it counts, half a kilogram.
        val slowed = target(
            *plain,
            "weeklyLossTarget" to "1.0",
            "paceHeldForAge" to true,
            "deficitCapped" to true,
            "requestedDeficit" to 550,
            "dailyDeficit" to 492,
            "weeklyLossKg" to 0.45,
        ).notes()
        assertEquals(listOf(TargetNote.PaceHeldForAge(1.0), TargetNote.DeficitCapped(550.0, 492.0, 0.45, 0.5)), slowed)
        // Keeping the weight, the advice has nothing to say; a form that gives the body's warnings itself asks for none.
        assertEquals(emptyList<TargetNote>(), target(*plain, "warnings" to listOf("older"), "dailyDeficit" to 0).notes())
        assertEquals(emptyList<TargetNote>(), target(*plain, "warnings" to listOf("older"), "dailyDeficit" to 550).notes(risk = false))
    }

    @Test
    fun `a correction held at the body's rest names the target it stops at`() {
        val held = target(*plain, "correctionHeld" to true, "dailyTarget" to 1780)
        assertEquals(listOf(TargetNote.CorrectionHeld(1780.0)), held.notes())
    }

    @Test
    fun `a low target says what it means, and a typed one under the floor or an underweight body where to turn`() {
        assertEquals(listOf(TargetNote.LowTarget), target(*plain, "warnings" to listOf("low_target")).notes())
        assertEquals(
            listOf(TargetNote.BelowFloor(1500.0), TargetNote.LowTarget, TargetNote.Help),
            target(*plain, "source" to "manual_override", "safetyFloor" to 1500, "warnings" to listOf("below_floor", "low_target")).notes(),
        )
        assertEquals(listOf(TargetNote.Underweight, TargetNote.Help), target(*plain, "warnings" to listOf("underweight")).notes())
        assertEquals(emptyList<TargetNote>(), target(*plain, "warnings" to listOf("underweight")).notes(risk = false))
    }

    @Test
    fun `a target raised to the floor names the floor and not the slowed pace`() {
        val notes = target(
            *plain,
            "deficitCapped" to true,
            "safetyFloorApplied" to true,
            "safetyFloor" to 1500,
            "dailyDeficit" to 400,
        ).notes()

        assertEquals(listOf(TargetNote.SafetyFloorApplied(1500.0, 400.0)), notes)
    }

    @Test
    fun `a typed target under the body's needs at rest is named only for a target that was typed`() {
        val typed = target(*plain, "source" to "manual_override", "warnings" to listOf("below_floor", "below_bmr"))
        val calculated = target(*plain, "source" to "calculated", "warnings" to listOf("below_bmr"))

        assertEquals(listOf(TargetNote.BelowFloor(typed.safetyFloor), TargetNote.BelowBmr(typed.bmr), TargetNote.Help), typed.notes())
        assertEquals(emptyList<TargetNote>(), calculated.notes())
    }
}
