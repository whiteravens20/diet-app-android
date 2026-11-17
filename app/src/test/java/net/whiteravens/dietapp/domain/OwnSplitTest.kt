// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The custom diet's two ways, worked out on the phone as the API and the web work them out, on the same cases. */
class OwnSplitTest {
    @Test
    fun `shares are rounded the way that misses them least`() {
        assertEquals(MacroSplit(34, 33, 33), wholePercent(1 / 3.0, 1 / 3.0, 1 / 3.0))
        assertEquals(MacroSplit(22, 30, 48), wholePercent(0.224, 0.3, 0.476))
    }

    @Test
    fun `protein per kilogram gives protein the grams of the weight, fat its share and carbohydrate the rest, as the API does`() {
        // 1.6 g a kilogram of 80 kg at 2400 kcal: 128 g, 512 kcal; the API answers 21, 30 and 49 for it.
        val at = perKgSplit(1.6, 30, 80.0, 2400)
        assertEquals(MacroSplit(21, 30, 49), at.split)
        assertEquals(128.0, at.protein, 1e-9)
        assertFalse(at.held)
    }

    @Test
    fun `protein is held at a limit and the grams a kilogram that fit are named, as the API refuses them`() {
        // 3 g a kilogram of 80 kg is 960 kcal, 53 % of 1800: held at 45 %, and 0.8 to 2.5 g fit.
        val past = perKgSplit(3.0, 30, 80.0, 1800)
        assertTrue(past.held)
        assertEquals(45, past.split.protein)
        assertEquals(0.8..2.5, past.fits)
    }

    @Test
    fun `a bound that is a tenth but for the error of floating point is that tenth`() {
        // Fat at 15 % leaves protein 15 % of 2000 kcal at least: 75 g, exactly 1 g for each of 75 kg.
        assertEquals(1.0..3.0, proteinPerKgThatFits(0.15, 2000, 75.0))
    }

    @Test
    fun `no grams are named where none fit`() {
        assertNull(perKgSplit(0.8, 75, 200.0, 1000).fits)
        // Fat at 75 % leaves protein 20 % of 1200 kcal at most: 60 g, 0.4 g for each of 150 kg.
        assertNull(proteinPerKgThatFits(0.75, 1200, 150.0))
    }

    @Test
    fun `the limits are the ones the API's contract holds a split and protein per kilogram to`() {
        val schemas = Json.parseToJsonElement(
            File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText(),
        ).jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject
        val profile = schemas.getValue("Profile").jsonObject.getValue("properties").jsonObject
        // The split is a schema of its own, named where a profile holds it.
        val named = profile.getValue("customMacroSplit").jsonObject.objectOf().getValue("\$ref").jsonPrimitive.content.substringAfterLast('/')
        val split = schemas.getValue(named).jsonObject.getValue("properties").jsonObject
        fun range(name: String) = split.getValue(name).jsonObject.let { it.getValue("minimum").jsonPrimitive.int..it.getValue("maximum").jsonPrimitive.int }
        assertEquals(SplitLimits.PROTEIN, range("protein"))
        assertEquals(SplitLimits.FAT, range("fat"))
        assertEquals(SplitLimits.CARBS, range("carbs"))
        val perKg = profile.getValue("customProteinPerKg").jsonObject.objectOf()
        assertEquals(ProteinPerKg.LEAST, perKg.getValue("minimum").jsonPrimitive.double, 0.0)
        assertEquals(ProteinPerKg.MOST, perKg.getValue("maximum").jsonPrimitive.double, 0.0)
    }

    /** The schema itself, where the contract writes a nullable one as a choice of it or null. */
    private fun JsonObject.objectOf(): JsonObject =
        get("anyOf")?.let { choices -> (choices as kotlinx.serialization.json.JsonArray).map { it.jsonObject }.first { it["type"]?.jsonPrimitive?.content != "null" } }
            ?: this
}
