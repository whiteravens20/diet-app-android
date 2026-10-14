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
    fun `a pace slowed to a quarter of maintenance is named with what was asked and what is taken`() {
        val notes = target(*plain, "deficitCapped" to true, "requestedDeficit" to 1100, "dailyDeficit" to 700).notes()

        assertEquals(listOf(TargetNote.DeficitCapped(1100.0, 700.0)), notes)
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

        assertEquals(listOf(TargetNote.BelowFloor(typed.safetyFloor), TargetNote.BelowBmr(typed.bmr)), typed.notes())
        assertEquals(emptyList<TargetNote>(), calculated.notes())
    }
}
