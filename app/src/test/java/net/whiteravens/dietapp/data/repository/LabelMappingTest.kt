// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.LabelLimitNoteDto
import net.whiteravens.dietapp.data.remote.LabelTotalsDto
import net.whiteravens.dietapp.data.remote.ProfilePreferencesDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.LabelFigure
import net.whiteravens.dietapp.domain.LabelLimitNote
import net.whiteravens.dietapp.domain.LabelLimits
import net.whiteravens.dietapp.domain.LabelNutrient
import net.whiteravens.dietapp.domain.LabelTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fibre, sugars, salt and saturated fat, and the limits a profile sets of them, read as the API
 * writes them (packages/shared nutrition.ts, profile.ts).
 */
class LabelMappingTest {
    private val json = AppModule.json()

    @Test
    fun `a sum is read with what is only at least so much and what is not known`() {
        val totals = json.decodeFromString<LabelTotalsDto>(
            """{"fiber":{"grams":12,"atLeast":false},"sugars":{"grams":26.2,"atLeast":true},"salt":null,"saturatedFat":{"grams":0,"atLeast":false}}""",
        ).toDomain()

        assertEquals(LabelTotals(LabelFigure(12.0, false), LabelFigure(26.2, true), null, LabelFigure(0.0, false)), totals)
        assertTrue(totals.anyAtLeast)
        assertTrue(totals.anyUnknown)
        assertEquals(LabelFigure(26.2, true), totals[LabelNutrient.SUGARS])
    }

    @Test
    fun `a sum every part of which is known says neither at least nor not known`() {
        val totals = LabelTotals(LabelFigure(1.0, false), LabelFigure(2.0, false), LabelFigure(0.3, false), LabelFigure(0.0, false))

        assertFalse(totals.anyAtLeast)
        assertFalse(totals.anyUnknown)
    }

    @Test
    fun `the notes of a day name their nutrient and which way the limit goes, and one of a nutrient this version does not know is left out`() {
        val notes = listOf(
            LabelLimitNoteDto("sugars", "atMost", 30.0, json.decodeFromString("""{"grams":60.2,"atLeast":true}""")),
            LabelLimitNoteDto("fiber", "atLeast", 25.0, json.decodeFromString("""{"grams":13.9,"atLeast":false}""")),
            LabelLimitNoteDto("iron", "atLeast", 10.0, json.decodeFromString("""{"grams":2,"atLeast":false}""")),
        ).toNotes()

        assertEquals(
            listOf(
                LabelLimitNote(LabelNutrient.SUGARS, atMost = true, limit = 30.0, figure = LabelFigure(60.2, true)),
                LabelLimitNote(LabelNutrient.FIBER, atMost = false, limit = 25.0, figure = LabelFigure(13.9, false)),
            ),
            notes,
        )
    }

    @Test
    fun `a profile's limits are read as it set them, and a profile of an instance without them has none`() {
        val set = json.decodeFromString<ProfilePreferencesDto>(
            """{"labelLimits":{"saltAtMost":5,"fiberAtLeast":null,"saturatedFatAtMost":null,"sugarsAtMost":30}}""",
        )
        assertEquals(LabelLimits(saltAtMost = 5.0, sugarsAtMost = 30.0), set.labelLimits.toDomain())
        assertEquals(LabelLimits(), json.decodeFromString<ProfilePreferencesDto>("{}").labelLimits.toDomain())
        assertEquals(set.labelLimits, LabelLimits(saltAtMost = 5.0, sugarsAtMost = 30.0).toDto())
    }
}
