// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every bound the phone repeats from the API is the one the API's contract says it takes: the phone refuses
 * nothing the API takes, and lets nothing through that it refuses. The diary's are held by [DiaryLimitsTest].
 */
class RepeatedLimitsTest {
    private val schemas = Json.parseToJsonElement(
        File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText(),
    ).jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

    private fun schema(name: String) = schemas.getValue(name).jsonObject

    private fun JsonObject.field(name: String): JsonObject = getValue("properties").jsonObject.getValue(name).jsonObject

    /** What a field takes where it may also be null: the other of its two. */
    private fun JsonObject.taken(): JsonObject =
        this["anyOf"]?.jsonArray?.map { it.jsonObject }?.first { it["type"]?.jsonPrimitive?.content != "null" } ?: this

    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.double

    @Test
    fun `a product of one's own`() {
        val body = schema("OwnProductBody")
        assertEquals(body.field("name").getValue("maxLength").jsonPrimitive.int, OwnProductLimits.NAME)
        assertEquals(body.field("brand").taken().getValue("maxLength").jsonPrimitive.int, OwnProductLimits.BRAND)
        assertEquals(body.field("pack").taken().getValue("maxLength").jsonPrimitive.int, OwnProductLimits.PACK)
        assertEquals(body.field("packAmount").taken().number("maximum"), OwnProductLimits.MOST_AMOUNT, 0.0)
        assertEquals(body.field("servingAmount").taken().number("maximum"), OwnProductLimits.MOST_AMOUNT, 0.0)
        val per100 = body.field("per100")
        assertEquals(per100.field("calories").number("maximum"), OwnProductLimits.MOST_CALORIES, 0.0)
        val grams = per100.getValue("properties").jsonObject.keys - "calories"
        assertEquals(setOf("protein", "fat", "carbs", "fiber", "sugars", "salt", "saturatedFat"), grams)
        for (part in grams) assertEquals(part, per100.field(part).taken().number("maximum"), OwnProductLimits.MOST_GRAMS, 0.0)
    }

    @Test
    fun `water written at once, and the target a profile starts with`() {
        assertEquals(schema("AddWaterRequest").field("ml").number("maximum"), MAX_WATER_ENTRY_ML.toDouble(), 0.0)
        assertEquals(schema("ProfileInput").field("waterTargetMl").getValue("default").jsonPrimitive.int, DEFAULT_WATER_TARGET_ML)
    }

    @Test
    fun `the portions a pantry row is frozen in`() {
        assertEquals(schema("FreezeInventoryItem").field("portions").number("maximum"), MAX_PORTIONS.toDouble(), 0.0)
    }

    @Test
    fun `the least and the most of each limit of a label`() {
        val limits = schema("LabelLimitsInput")
        for (limit in LabelLimit.entries) {
            val name = when (limit) {
                LabelLimit.SALT_AT_MOST -> "saltAtMost"
                LabelLimit.FIBER_AT_LEAST -> "fiberAtLeast"
                LabelLimit.SATURATED_FAT_AT_MOST -> "saturatedFatAtMost"
                LabelLimit.SUGARS_AT_MOST -> "sugarsAtMost"
            }
            val taken = limits.field(name).taken()
            assertEquals(name, taken.number("minimum"), limit.min, 0.0)
            assertEquals(name, taken.number("maximum"), limit.max, 0.0)
        }
    }
}
