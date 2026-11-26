// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.DiaryPlanMeal
import net.whiteravens.dietapp.domain.EatenElsewhere
import net.whiteravens.dietapp.domain.FrozenKept
import net.whiteravens.dietapp.domain.ItemShare
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.MacrosOffCause
import net.whiteravens.dietapp.domain.MacrosOffWhy
import net.whiteravens.dietapp.domain.RowPantry
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.testing.EachLanguage
import net.whiteravens.dietapp.ui.screens.diary.OtherPlanMealRow
import net.whiteravens.dietapp.ui.screens.plans.DayChangeHelpDialog
import net.whiteravens.dietapp.ui.screens.plans.MacrosOffWhyLines
import net.whiteravens.dietapp.ui.screens.plans.OtherPlanMealCard
import net.whiteravens.dietapp.ui.screens.plans.ProteinShortLine
import net.whiteravens.dietapp.ui.screens.plans.StaysFrozenLines
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a plan's day says of a day off its macros, what its packs say stays in the freezer, what a list's row says of
 * whose pantry holds what is at home and of what the freezer keeps, and a meal eaten from another plan in the diary
 * and in a plan's day, and the window on how a day is changed: in English, then in Polish, each kept for the eye as
 * a phone in that language keeps it.
 */
@RunWith(AndroidJUnit4::class)
class DayAndListLinesTest {
    @get:Rule
    val compose = createComposeRule()

    private val eye = EachLanguage(compose)

    @Test
    fun aDayOffItsMacrosSaysWhatItHoldsTooMuchAndTooLittleOfAndEachThingThatMakesItSo() {
        val why = MacrosOffWhy(
            over = "fat",
            under = "protein",
            causes = listOf(MacrosOffCause.Fixed(26), MacrosOffCause.CookTime(15), MacrosOffCause.NoWaste, MacrosOffCause.Repeats(1), MacrosOffCause.ProteinKinds),
        )
        eye.show { MacrosOffWhyLines(why) }

        eye.check("plans-macros-off-why") { language, lines ->
            val lean = if (language == "en") "Too much fat, too little protein." else "Za dużo tłuszczu, za mało białka."
            assertTrue(lines.toString(), lean in lines)
            // Each cause on a line of its own.
            assertEquals(lines.toString(), 5, lines.count { it.startsWith("• ") })
        }
    }

    @Test
    fun thePacksSayWhatOfThePlansFoodsStaysInTheFreezerAndWhy() {
        val names = mapOf("en" to "Bacon", "pl" to "Boczek")
        eye.show { language ->
            StaysFrozenLines(
                listOf(
                    FrozenKept(names.getValue(language), "g", amount = 220, packages = 1, later = false, mostInTwoDays = 130),
                    FrozenKept(names.getValue(language), "g", amount = 500, packages = 2, later = true, mostInTwoDays = 0),
                ),
            )
        }

        eye.check("plans-stays-frozen") { language, lines ->
            if (language == "en") {
                assertTrue(lines.toString(), "Stays in the freezer:" in lines)
                assertTrue(lines.toString(), lines.any { it.startsWith("• Bacon, 220 g: the plan eats at most 130 g of it in 2 days") })
            } else {
                assertTrue(lines.toString(), "Zostaje w zamrażarce:" in lines)
                assertTrue(lines.toString(), lines.any { it.startsWith("• Boczek, 220 g: w dwa dni plan zje z tego najwyżej 130 g") })
            }
            assertEquals(lines.toString(), 2, lines.count { it.startsWith("• ") })
        }
    }

    @Test
    fun aRowSaysWhosePantryHoldsWhatIsAtHomeAndWhatTheFreezerStillKeeps() {
        val names = mapOf("en" to "Firm tofu", "pl" to "Tofu naturalne")
        eye.show { language ->
            ShoppingItemRow(
                ShoppingItem(
                    id = "row-$language",
                    name = names.getValue(language),
                    totalQuantity = 1080.0,
                    toBuyQuantity = 380.0,
                    unit = "g",
                    displayUnit = "g",
                    atHome = 700.0,
                    purchasedQuantity = null,
                    pantryBestBefore = null,
                    estimatedCalories = 1500.0,
                    packs = null,
                    checked = false,
                    shares = listOf(ItemShare("a", "Adam", 540.0), ItemShare("e", "Ewa", 540.0)),
                    pantries = listOf(RowPantry("a", "Adam", 700.0)),
                    frozenKept = 220.0,
                ),
                busy = false,
                readOnly = false,
                waiting = false,
                onChange = {},
            )
        }

        eye.check("shopping-from-pantries-and-the-freezer") { language, lines ->
            if (language == "en") {
                assertTrue(lines.toString(), "From the pantry: Adam 700 g" in lines)
                assertTrue(lines.toString(), "220 g more stays in the freezer: the plan does not thaw it." in lines)
            } else {
                assertTrue(lines.toString(), "Ze spiżarni: Adam 700 g" in lines)
                assertTrue(lines.toString(), "W zamrażarce zostaje jeszcze 220 g: plan tego nie rozmraża." in lines)
            }
        }
    }

    @Test
    fun aDayThatLacksMuchProteinSaysHowMuchOfWhatItAimsAt() {
        eye.show { ProteinShortLine(134.4, 178.0) }

        eye.check("plans-protein-short") { language, lines ->
            val said =
                if (language == "en") "Too little protein: 134 of 178 g, more than a fifth less than the day aims at."
                else "Za mało białka: 134 z 178 g, ponad jedną piątą mniej, niż zakłada dzień."
            assertTrue(lines.toString(), said in lines)
        }
    }

    @Test
    fun aRowBoughtByWeightSaysSoAndKeepsItsBoxUnderItsAmounts() {
        val names = mapOf("en" to "Turkey breast", "pl" to "Pierś z indyka")
        eye.show { language ->
            ShoppingItemRow(
                ShoppingItem(
                    id = "turkey-$language",
                    name = names.getValue(language),
                    totalQuantity = 240.0,
                    toBuyQuantity = 240.0,
                    unit = "g",
                    displayUnit = "g",
                    atHome = 0.0,
                    purchasedQuantity = 1000.0,
                    pantryBestBefore = null,
                    estimatedCalories = 260.0,
                    packs = null,
                    checked = true,
                    boughtLooseOn = "2026-11-24",
                    byWeight = true,
                ),
                busy = false,
                readOnly = false,
                waiting = false,
                onChange = {},
            )
        }

        eye.check("shopping-bought-by-weight") { language, lines ->
            val name = names.getValue(language)
            val (closed, box, amounts) =
                if (language == "en") Triple("bought by weight 1000 g", "Bought by weight", "Amounts of $name")
                else Triple("kupione na wagę 1000 g", "Kupione na wagę", "Ilości: $name")
            assertTrue(lines.toString(), closed in lines)
            compose.onNodeWithContentDescription(amounts).performClick()
            compose.onNodeWithText(box).assertIsDisplayed().assertIsOn()
        }
    }

    @Test
    fun aMealEatenFromAnotherPlanStandsInTheDiaryAndInAPlansDay() {
        val names = mapOf("en" to "Porridge with apple", "pl" to "Owsianka z jabłkiem")
        eye.show { language ->
            val meal = DiaryPlanMeal("m-$language", "breakfast", names.getValue(language), "r", eaten = true, calories = 420.0)
            OtherPlanMealRow(meal, onOpen = {})
            OtherPlanMealCard(EatenElsewhere(meal, "plan", Macros(14.0, 12.0, 62.0)), onOpen = {})
        }

        eye.check("diary-and-plan-a-meal-from-another-plan") { language, lines ->
            val (line, chip) = if (language == "en") "From another plan" to "from another plan" else "Z innego planu" to "z innego planu"
            assertTrue(lines.toString(), lines.any { it.startsWith(line) })
            assertTrue(lines.toString(), chip in lines)
        }
    }

    @Test
    fun theWindowOnHowADayIsChangedSaysWhatStaysTheWaysWhenAndWhatItChangesAndOpensTheWays() {
        eye.show { DayChangeHelpDialog(date = "2026-11-25", joint = true, onVariants = {}, onReroll = {}, onDismiss = {}) }

        eye.check("plans-how-to-change-a-day") { language, lines ->
            val (title, ways, change) = if (language == "en") {
                Triple("Changing a day and keeping your own meals", "Other ways…", "Change day…")
            } else {
                Triple("Jak zmienić dzień i zachować własne posiłki", "Inne warianty…", "Zmień dzień…")
            }
            assertTrue(lines.toString(), title in lines)
            assertTrue(lines.toString(), ways in lines)
            assertTrue(lines.toString(), change in lines)
            // Four ways, and five things a change takes with it in a joint plan.
            assertEquals(lines.toString(), 9, lines.count { it.startsWith("• ") })
        }
    }
}
