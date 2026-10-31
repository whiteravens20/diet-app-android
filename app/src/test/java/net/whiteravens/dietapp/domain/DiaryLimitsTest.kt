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
import java.time.LocalTime

/** What the phone holds an entry to before it is sent is what the API's contract says it takes. */
class DiaryLimitsTest {
    private val schemas = Json.parseToJsonElement(
        File(checkNotNull(System.getProperty("contract.dir")) { "The build sets contract.dir" }, "openapi.json").readText(),
    ).jsonObject.getValue("components").jsonObject.getValue("schemas").jsonObject

    /** The variant of a new entry of the kind [kind]: what it takes. */
    private fun entryOf(kind: String): JsonObject = schemas.getValue("CreateDiaryEntryRequest").jsonObject.getValue("oneOf").jsonArray
        .map { it.jsonObject.getValue("properties").jsonObject }
        .first { it.getValue("kind").jsonObject.getValue("const").jsonPrimitive.content == kind }

    private fun JsonObject.number(name: String) = getValue(name).jsonPrimitive.double

    @Test
    fun `the servings of an entry`() {
        val servings = entryOf("recipe").getValue("servings").jsonObject
        assertEquals(servings.number("minimum"), DiaryLimits.leastServings, 0.0)
        assertEquals(servings.number("maximum"), DiaryLimits.mostServings, 0.0)
    }

    @Test
    fun `the amount of an ingredient`() {
        val quantity = entryOf("ingredient").getValue("amount").jsonObject.getValue("properties").jsonObject.getValue("quantity").jsonObject
        assertEquals(quantity.number("maximum"), DiaryLimits.mostWeighed, 0.0)
    }

    @Test
    fun `the name and the figures of a meal of one's own`() {
        assertEquals(entryOf("meal").getValue("name").jsonObject.getValue("maxLength").jsonPrimitive.int, DiaryLimits.name)
        val figures = schemas.getValue("DiaryMealNutrition").jsonObject.getValue("properties").jsonObject
        assertEquals(figures.getValue("calories").jsonObject.number("maximum"), DiaryLimits.calories, 0.0)
        assertEquals(figures.getValue("protein").jsonObject.number("maximum"), DiaryLimits.protein, 0.0)
        assertEquals(figures.getValue("fat").jsonObject.number("maximum"), DiaryLimits.fat, 0.0)
        assertEquals(figures.getValue("carbs").jsonObject.number("maximum"), DiaryLimits.carbs, 0.0)
    }

    @Test
    fun `the most entries a day holds`() {
        val entries = schemas.getValue("DiaryDay").jsonObject.getValue("properties").jsonObject.getValue("entries").jsonObject
        assertEquals(entries.getValue("maxItems").jsonPrimitive.int, MOST_DIARY_ENTRIES)
    }

    @Test
    fun `the meal of the hour goes by the phone's clock, each up to the hour it ends`() {
        assertEquals("breakfast", mealAt(LocalTime.of(0, 0)))
        assertEquals("breakfast", mealAt(LocalTime.of(9, 59)))
        assertEquals("second_breakfast", mealAt(LocalTime.of(10, 0)))
        assertEquals("second_breakfast", mealAt(LocalTime.of(11, 59)))
        assertEquals("lunch", mealAt(LocalTime.of(12, 0)))
        assertEquals("lunch", mealAt(LocalTime.of(14, 59)))
        assertEquals("snack", mealAt(LocalTime.of(15, 0)))
        assertEquals("snack", mealAt(LocalTime.of(17, 59)))
        assertEquals("dinner", mealAt(LocalTime.of(18, 0)))
        assertEquals("dinner", mealAt(LocalTime.of(23, 59)))
    }
}
