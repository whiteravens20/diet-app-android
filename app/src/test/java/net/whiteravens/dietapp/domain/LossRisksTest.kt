// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

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
 * Why the loss a calorie target was asked for is a risk, as the API's answer says it. The answers
 * are those a running backend gave for a girl of 16 losing weight, losing it with the risk
 * acknowledged, and keeping it; a target of her own is the last with the fields of that case set.
 */
class LossRisksTest {
    private val json = AppModule.json()

    private fun target(name: String, vararg changes: Pair<String, Any>): CalorieTarget {
        val captured = json.parseToJsonElement(fixture(name)).jsonObject.getValue("calculation").jsonObject
        val changed = captured + changes.associate { (key, value) ->
            key to when (value) {
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        return json.decodeFromJsonElement(CalorieCalculationDto.serializer(), JsonObject(changed)).toDomain()
    }

    @Test
    fun `a loss asked for by a pace is a risk under 18, held or acknowledged`() {
        assertEquals(listOf("minor"), target("profile-calories-preview-minor-losing").lossRisks)
        assertEquals(listOf("minor"), target("profile-calories-preview-minor-losing-acknowledged").lossRisks)
    }

    @Test
    fun `keeping one's weight loses nothing, and no risk is said of it although the API warns of the age`() {
        val keeping = target("profile-calories-preview-minor-keeping")

        assertEquals(listOf("minor"), keeping.warnings)
        assertEquals(emptyList<String>(), keeping.lossRisks)
    }

    @Test
    fun `a target of one's own is a loss while it is held at maintenance or once it is under it, and none at maintenance or over it`() {
        val keeping = "profile-calories-preview-minor-keeping"

        assertEquals(listOf("minor"), target(keeping, "source" to "manual_override", "goalHeld" to true).lossRisks)
        assertEquals(listOf("minor"), target(keeping, "source" to "manual_override", "dailyTarget" to 1800).lossRisks)
        assertEquals(emptyList<String>(), target(keeping, "source" to "manual_override", "dailyTarget" to 2124).lossRisks)
        assertEquals(emptyList<String>(), target(keeping, "source" to "manual_override", "dailyTarget" to 2500).lossRisks)
    }

    @Test
    fun `an adult of a healthy weight is told of no risk`() {
        assertEquals(emptyList<String>(), json.decodeFromString<CalorieCalculationDto>(fixture("profile-calories")).toDomain().lossRisks)
    }
}
