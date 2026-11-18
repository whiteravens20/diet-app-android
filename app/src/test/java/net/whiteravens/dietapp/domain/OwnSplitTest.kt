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
    fun `grams are the shares of their energy, in whole per cent that add up to a hundred`() {
        // 150 g of protein, 74 of fat and 249 of carbohydrate: 600, 666 and 996 kcal of 2262.
        assertEquals(2262.0, kcalOfGrams(150.0, 74.0, 249.0), 0.0)
        assertEquals(MacroSplit(protein = 27, fat = 29, carbs = 44), splitOfGrams(150.0, 74.0, 249.0))
        assertNull(splitOfGrams(0.0, 0.0, 0.0))
    }

    @Test
    fun `a split comes to grams at a target, a half rounded up`() {
        assertEquals(MacroGrams(protein = 138, fat = 74, carbs = 249), gramsAt(MacroSplit(25, 30, 45), 2209))
        // 10 % of 2020 kcal is 202 kcal, 50.5 g of protein: up to 51, as the web and the API round it, not down to the even 50.
        assertEquals(MacroGrams(protein = 51, fat = 101, carbs = 227), gramsAt(MacroSplit(10, 45, 45), 2020))
    }

    @Test
    fun `shares are rounded the way that misses them least, and a split is held to its limits`() {
        assertEquals(MacroSplit(34, 33, 33), wholePercent(1 / 3.0, 1 / 3.0, 1 / 3.0))
        assertEquals(MacroSplit(22, 30, 48), wholePercent(0.224, 0.3, 0.476))
        assertTrue(insideLimits(MacroSplit(25, 30, 45)))
        assertFalse(insideLimits(MacroSplit(9, 30, 61)))
        assertFalse(insideLimits(MacroSplit(10, 15, 75)))
    }

    @Test
    fun `protein per kilogram gives protein the grams of the weight, fat its share and carbohydrate the rest, as the API does`() {
        // 1.6 g a kilogram of 80 kg at 2400 kcal: 128 g, 512 kcal; the API answers 21, 30 and 49 for it.
        val at = perKgSplit(1.6, 30, 80.0, 2400)
        assertEquals(MacroSplit(21, 30, 49), at.split)
        assertEquals(128.0, at.protein, 1e-9)
        assertEquals(128.0, at.grams, 1e-9)
        assertFalse(at.held)
    }

    @Test
    fun `protein is held at a limit and the grams a kilogram that fit are named, as the API refuses them`() {
        // 3 g a kilogram of 80 kg is 960 kcal, 53 % of 1800: held at 45 %, and 0.8 to 2.5 g fit.
        val past = perKgSplit(3.0, 30, 80.0, 1800)
        assertTrue(past.held)
        assertEquals(45, past.split.protein)
        assertEquals(0.8..2.5, past.fits)
        // The grams said beside the 45 % are those it stands for, 810 kcal of 1800, not the 240 g asked for.
        assertEquals(240.0, past.protein, 1e-9)
        assertEquals(202.5, past.grams, 1e-9)
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
