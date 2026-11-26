// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Expenditure
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.testing.EachLanguage
import net.whiteravens.dietapp.ui.components.ExpenditureProposal
import net.whiteravens.dietapp.ui.screens.profiles.CaloriePreviewCard
import net.whiteravens.dietapp.ui.screens.profiles.OwnTargetField
import net.whiteravens.dietapp.ui.screens.profiles.PaceChoice
import net.whiteravens.dietapp.ui.screens.profiles.ProfileForm
import net.whiteravens.dietapp.ui.screens.profiles.ProfileMarks
import net.whiteravens.dietapp.ui.screens.profiles.RiskNote
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the goal's safety says on the phone: a body under 18 or underweight told that no loss is planned, with nothing
 * to tick; the paces from 65, a faster pace kept counted as the slower one, and what a slowed pace loses at first; the
 * least a target typed by hand may be; and the real expenditure's proposal stopped at the body's rest. In English,
 * then in Polish, each kept for the eye.
 */
@RunWith(AndroidJUnit4::class)
class GoalSafetyLinesTest {
    @get:Rule
    val compose = createComposeRule()

    private val eye = EachLanguage(compose)

    @Test
    fun aBodyUnder18OrUnderweightIsToldThatNoLossIsPlannedWithNothingToTick() {
        eye.show {
            RiskNote(listOf("minor"))
            RiskNote(listOf("underweight"))
        }

        eye.check("profiles-no-loss-planned") { language, lines ->
            val minor = if (language == "en") "Under 18 the application plans no loss" else "Poniżej 18 lat aplikacja nie planuje redukcji"
            val held = if (language == "en") "The goal is saved, but plans follow maintenance." else "Wybrany cel zostaje zapisany, ale plany trzymają się zapotrzebowania."
            val help = if (language == "en") {
                "If controlling food has become a way to cope with feelings, talk to a doctor."
            } else {
                "Jeśli kontrola jedzenia stała się sposobem radzenia sobie z emocjami, porozmawiaj z lekarzem."
            }
            assertTrue(lines.toString(), lines.any { it.startsWith(minor) })
            assertEquals(lines.toString(), 2, lines.count { it == held })
            // Where to turn is said to the underweight body alone.
            assertEquals(lines.toString(), 1, lines.count { it == help })
            assertTrue(compose.onAllNodes(isToggleable()).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun from65AFasterPaceKeptCountsAsTheSlowerOneAndASlowedPaceSaysWhatItLosesAtFirst() {
        val paces = olderPaces()
        // A woman of 70 who chose a kilogram a week before the rule: the pace is kept and counted as half a kilogram.
        val form = ProfileForm(sex = "female", weeklyLossTarget = "1.0")
        eye.show {
            PaceChoice(form, WeightUnit.KG, paces) {}
            CaloriePreviewCard(CaloriePreview(paces.last(), paces), behind = false, waiting = false, failed = false, refused = false)
        }

        eye.check("profiles-paces-from-65") { language, lines ->
            val (counted, age, loses, pregnancy) = if (language == "en") {
                listOf(
                    "counted as 0.5 kg",
                    "From 65 a plan is made for a loss of at most 0.5 kg a week, so that muscle is not lost with the fat.",
                    "at first about 0.45 kg a week",
                    "When pregnant or breastfeeding, do not plan a loss: ask a doctor.",
                )
            } else {
                listOf(
                    "liczone jako 0,5 kg",
                    "Od 65 lat plan zakłada redukcję najwyżej o 0,5 kg na tydzień, żeby razem z tłuszczem nie tracić mięśni.",
                    "na początku to około 0,45 kg tygodniowo",
                    "W ciąży i w czasie karmienia piersią nie planuj redukcji: zapytaj lekarza.",
                )
            }
            assertTrue(lines.toString(), lines.any { counted in it })
            assertTrue(lines.toString(), age in lines)
            assertTrue(lines.toString(), lines.any { loses in it })
            assertTrue(lines.toString(), pregnancy in lines)
            // Each line of the card follows from the one before: the pace chosen counts as half a kilogram, and what
            // that asks for is slowed.
            val (heldForAge, asks) = if (language == "en") {
                "The pace you chose, 1 kg a week, counts as 0.5 kg: from 65 a plan is made for a loss of no more than that." to
                    "A pace of 0.5 kg a week asks for 550 kcal a day less."
            } else {
                "Wybrane tempo 1 kg na tydzień liczy się jako 0,5 kg: od 65 lat plan zakłada redukcję najwyżej o tyle." to
                    "Tempo 0,5 kg na tydzień wymaga 550 kcal dziennie mniej."
            }
            assertTrue(lines.toString(), heldForAge in lines)
            assertTrue(lines.toString(), lines.any { it.startsWith(asks) })
            // The faster pace that is not the profile's is not offered.
            assertTrue(lines.toString(), lines.none { "0.75" in it || "0,75" in it })
        }
    }

    @Test
    fun aTargetTypedByHandSaysTheLeastThisBodyMayType() {
        eye.show { OwnTargetField(ProfileForm(ownTarget = "1100"), ProfileMarks(), {}, least = 1123.0) }

        eye.check("profiles-least-typed-target") { language, lines ->
            val hint = if (language == "en") "At least 1123 kcal. Leave it empty and the pace decides." else "Najmniej 1123 kcal. Zostaw puste, a zdecyduje tempo."
            assertTrue(lines.toString(), hint in lines)
        }
    }

    @Test
    fun theRealExpendituresProposalSaysWhereItStopsAndThatADiaryWrittenInPartGivesTooLowAFigure() {
        val shown = Expenditure(state = "proposed", estimate = 1500, maintenance = 2453, correction = -610, target = 1903, targetCorrected = 1752, held = true)
        eye.show { ExpenditureProposal(shown, deciding = false, failed = null, onAccept = {}, onLater = {}) }

        eye.check("dashboard-expenditure-held") { language, lines ->
            val held = if (language == "en") "The application goes no lower: the target stops at 1752 kcal." else "Niżej aplikacja nie zejdzie: cel zatrzymuje się na 1752 kcal."
            val caution = if (language == "en") "A diary with days written in part gives a figure too low." else "Dziennik z dniami zapisanymi tylko w części daje za niską liczbę."
            assertTrue(lines.toString(), held in lines)
            assertTrue(lines.toString(), lines.any { it.startsWith(caution) })
        }
    }

    /**
     * The paces of a woman of 70, 165 cm and 71 kg, moderately active, as the API works them out: from half a kilogram
     * a quarter of her maintenance holds the pace, and the two faster paces count as half a kilogram.
     */
    private fun olderPaces(): List<CalorieTarget> {
        fun pace(chosen: String?, target: Double, requested: Double, capped: Boolean = false, heldForAge: Boolean = false) = CalorieTarget(
            bmr = 1230.0,
            maintenance = 1968.0,
            dailyDeficit = 1968.0 - target,
            weeklyLossKg = Math.round((1968.0 - target) * 7 * 100 / 7700) / 100.0,
            weeklyLossTarget = chosen,
            requestedDeficit = requested,
            dailyTarget = target,
            targetMacros = Nutrition(calories = target, protein = 90.0, fat = target * 0.3 / 9, carbs = target * 0.45 / 4),
            source = "calculated",
            safetyFloor = 1200.0,
            safetyFloorApplied = false,
            deficitCapped = capped,
            paceHeldForAge = heldForAge,
            maintenanceUnderFloor = false,
            goalHeld = false,
            warnings = if (target < 1550) listOf("older", "low_target") else listOf("older"),
        )
        return listOf(
            pace(null, 1968.0, 0.0),
            pace("0.25", 1693.0, 275.0),
            pace("0.5", 1476.0, 550.0, capped = true),
            // From 65 the faster paces count as half a kilogram: what they ask for is said as they count.
            pace("0.75", 1476.0, 550.0, capped = true, heldForAge = true),
            pace("1.0", 1476.0, 550.0, capped = true, heldForAge = true),
        )
    }
}
