// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.FieldRefusal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which folded sections of a profile's form open by themselves: those with something to answer. */
class ProfileSectionsTest {
    @Test
    fun nothingToAnswerOpensNothing() {
        assertEquals(emptySet<ProfileSection>(), sectionsNeeding(ProfileMarks(), risks = emptyList(), acknowledged = false))
    }

    @Test
    fun aFieldWithNoNumberOpensItsOwnSection() {
        assertEquals(setOf(ProfileSection.GOAL), sectionsNeeding(ProfileMarks(notNumbers = setOf(ProfileField.TARGET_WEIGHT)), emptyList(), false))
        assertEquals(setOf(ProfileSection.BODY), sectionsNeeding(ProfileMarks(notNumbers = setOf(ProfileField.HEIGHT)), emptyList(), false))
    }

    @Test
    fun aFieldTheApiRefusedOpensItsOwnSection() {
        val refused = ProfileRefusals(fields = mapOf(ProfileField.LABEL_SALT to FieldRefusal.Other, ProfileField.PER_KG to FieldRefusal.Other))
        assertEquals(setOf(ProfileSection.LABELS, ProfileSection.FOOD), sectionsNeeding(ProfileMarks(refusals = refused), emptyList(), false))
    }

    @Test
    fun aKindOfProteinsLimitOpensTheVariety() {
        assertEquals(setOf(ProfileSection.VARIETY), sectionsNeeding(ProfileMarks(kindNotNumbers = mapOf("poultry" to setOf(KindPart.DAY))), emptyList(), false))
        val refused = ProfileRefusals(kinds = mapOf("fish" to mapOf(KindPart.WEEK to FieldRefusal.Other)))
        assertEquals(setOf(ProfileSection.VARIETY), sectionsNeeding(ProfileMarks(refusals = refused), emptyList(), false))
    }

    @Test
    fun aRiskOpensTheGoalUntilItIsAcknowledged() {
        assertEquals(setOf(ProfileSection.GOAL), sectionsNeeding(ProfileMarks(), risks = listOf("pace"), acknowledged = false))
        assertEquals(emptySet<ProfileSection>(), sectionsNeeding(ProfileMarks(), risks = listOf("pace"), acknowledged = true))
    }

    @Test
    fun everyFieldStandsInTheSectionThatShowsIt() {
        val body = ProfileField.entries.filter { sectionOf(it) == ProfileSection.BODY }
        assertEquals(listOf(ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT), body)
        assertEquals(
            ProfileField.entries.filter { it.path.startsWith("preferences.labelLimits.") },
            ProfileField.entries.filter { sectionOf(it) == ProfileSection.LABELS },
        )
    }
}
