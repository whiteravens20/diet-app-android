// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.ui.screens.diary.OtherPlanMealRow
import net.whiteravens.dietapp.ui.screens.plans.MacrosOffWhyLines
import net.whiteravens.dietapp.ui.screens.plans.OtherPlanMealCard
import net.whiteravens.dietapp.ui.screens.plans.StaysFrozenLines
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingItemRow
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * What a plan's day says of a day off its macros, what its packs say stays in the freezer, what a list's row says of
 * whose pantry holds what is at home and of what the freezer keeps, and a meal eaten from another plan in the diary
 * and in a plan's day: in English, then in Polish, each kept for the eye as a phone in that language keeps it.
 */
@RunWith(AndroidJUnit4::class)
class DayAndListLinesTest {
    @get:Rule
    val compose = createComposeRule()

    /** The language the content is shown in. */
    private var language by mutableStateOf("en")

    /** [content] in the language shown, given that language. */
    private fun show(content: @Composable (language: String) -> Unit) {
        compose.setContent {
            DietAppTheme {
                Surface {
                    // Clear of the phone's bars, as a screen of the app is.
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                        val context = LocalContext.current.createConfigurationContext(configuration)
                        CompositionLocalProvider(
                            LocalContext provides context,
                            LocalConfiguration provides configuration,
                            LocalResources provides context.resources,
                        ) {
                            content(language)
                        }
                    }
                }
            }
        }
    }

    /**
     * Each language in turn: what is shown in it, as the eye reads it ([check]), and its picture, [name] and the
     * language, checked as a phone in that language checks its words: Polish holds its words of one letter to the next.
     */
    private fun inEachLanguage(name: String, check: (language: String, said: List<String>) -> Unit) {
        val phone = Locale.getDefault()
        try {
            for (each in listOf("en", "pl")) {
                language = each
                compose.waitForIdle()
                check(each, said())
                Locale.setDefault(Locale.forLanguageTag(each))
                compose.keepScreenshot("$name-$each")
                Locale.setDefault(phone)
            }
        } finally {
            Locale.setDefault(phone)
        }
    }

    /** Every text shown, as the eye reads it: a no-break space as a space. */
    private fun said(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.replace('\u00A0', ' ') } }

    @Test
    fun aDayOffItsMacrosSaysWhatItHoldsTooMuchAndTooLittleOfAndEachThingThatMakesItSo() {
        val why = MacrosOffWhy(
            over = "fat",
            under = "protein",
            causes = listOf(MacrosOffCause.Fixed(26), MacrosOffCause.CookTime(15), MacrosOffCause.NoWaste, MacrosOffCause.Repeats(1), MacrosOffCause.ProteinKinds),
        )
        show { MacrosOffWhyLines(why) }

        inEachLanguage("plans-macros-off-why") { language, lines ->
            val lean = if (language == "en") "Too much fat, too little protein." else "Za dużo tłuszczu, za mało białka."
            assertTrue(lines.toString(), lean in lines)
            // Each cause on a line of its own.
            assertEquals(lines.toString(), 5, lines.count { it.startsWith("• ") })
        }
    }

    @Test
    fun thePacksSayWhatOfThePlansFoodsStaysInTheFreezerAndWhy() {
        val names = mapOf("en" to "Bacon", "pl" to "Boczek")
        show { language ->
            StaysFrozenLines(
                listOf(
                    FrozenKept(names.getValue(language), "g", amount = 220, packages = 1, later = false, mostInTwoDays = 130),
                    FrozenKept(names.getValue(language), "g", amount = 500, packages = 2, later = true, mostInTwoDays = 0),
                ),
            )
        }

        inEachLanguage("plans-stays-frozen") { language, lines ->
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
        show { language ->
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

        inEachLanguage("shopping-from-pantries-and-the-freezer") { language, lines ->
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
    fun aMealEatenFromAnotherPlanStandsInTheDiaryAndInAPlansDay() {
        val names = mapOf("en" to "Porridge with apple", "pl" to "Owsianka z jabłkiem")
        show { language ->
            val meal = DiaryPlanMeal("m-$language", "breakfast", names.getValue(language), "r", eaten = true, calories = 420.0)
            OtherPlanMealRow(meal, onOpen = {})
            OtherPlanMealCard(EatenElsewhere(meal, "plan", Macros(14.0, 12.0, 62.0)), onOpen = {})
        }

        inEachLanguage("diary-and-plan-a-meal-from-another-plan") { language, lines ->
            val (line, chip) = if (language == "en") "From another plan" to "from another plan" else "Z innego planu" to "z innego planu"
            assertTrue(lines.toString(), lines.any { it.startsWith(line) })
            assertTrue(lines.toString(), chip in lines)
        }
    }
}
