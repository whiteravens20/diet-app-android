// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.FrozenRest
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.ui.screens.plans.FoodLeft
import net.whiteravens.dietapp.ui.screens.plans.FoodLeftLine
import net.whiteravens.dietapp.ui.screens.plans.IntoFreezerLine
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The lines of the packs a plan leaves something in, one a food: one pack as it was said, several added up with of how
 * many of them more than a fifth is left; and what goes into the freezer, one item a food with each rest's day. In
 * English and in Polish.
 */
@RunWith(AndroidJUnit4::class)
class PacksLeftTest {
    @get:Rule
    val compose = createComposeRule()

    /** The lines of four foods in [language], named as [names] gives them, as the screen shows them. */
    private fun foodsLeftIn(language: String, names: List<String>): List<String> {
        compose.setContent {
            DietAppTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                        val context = LocalContext.current.createConfigurationContext(configuration)
                        CompositionLocalProvider(
                            LocalContext provides context,
                            LocalConfiguration provides configuration,
                            LocalResources provides context.resources,
                        ) {
                            listOf(
                                FoodLeft(names[0], "g", 500.0, left = 543, packs = 3, much = 3),
                                FoodLeft(names[1], "g", 200.0, left = 61, packs = 2, much = 1),
                                FoodLeft(names[2], "g", 400.0, left = 70, packs = 2, much = 0),
                                FoodLeft(names[3], "g", 150.0, left = 19, packs = 1, much = 0),
                            ).forEach { food -> FoodLeftLine(food) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // As the eye reads them: Polish holds its words of one letter to the next with a no-break space.
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.replace('\u00A0', ' ') } }
    }

    @Test
    fun aFoodLeftOfInSeveralPacksIsSaidOnceItsRestsAddedUpInEnglish() {
        assertEquals(
            listOf(
                "Chicken breast: 543 g left in total of 3 packs of 500 g, more than a fifth of each",
                "Spinach: 61 g left in total of 2 packs of 200 g, more than a fifth of one of them",
                "Firm tofu: 70 g left in total of 2 packs of 400 g",
                "Bacon: 19 g left of a pack of 150 g",
            ),
            foodsLeftIn("en", listOf("Chicken breast", "Spinach", "Firm tofu", "Bacon")),
        )
    }

    @Test
    fun aFoodLeftOfInSeveralPacksIsSaidOnceItsRestsAddedUpInPolish() {
        assertEquals(
            listOf(
                "Pierś z kurczaka: zostaje razem 543 g z 3 opakowań po 500 g, z każdego ponad jedna piąta",
                "Szpinak: zostaje razem 61 g z 2 opakowań po 200 g, z jednego ponad jedna piąta",
                "Tofu twarde: zostaje razem 70 g z 2 opakowań po 400 g",
                "Boczek: zostaje 19 g z opakowania 150 g",
            ),
            foodsLeftIn("pl", listOf("Pierś z kurczaka", "Szpinak", "Tofu twarde", "Boczek")),
        )
        // The picture of the Polish lines alone: on a phone in Polish no word of one letter of them may stand loose.
        compose.keepScreenshot("plans-packs-left-by-food")
    }

    @Test
    fun whatGoesIntoTheFreezerIsSaidOnceAFoodEachRestWithItsDay() {
        val names = linkedMapOf(
            "en" to listOf("Chicken breast", "Turkey breast"),
            "pl" to listOf("Pierś z kurczaka", "Pierś z indyka"),
        )
        fun rest(id: String, name: String, date: String, amount: Int) =
            FrozenRest(id, name, "g", date, null, amount, emptyList(), amount, inPantry = false)
        compose.setContent {
            DietAppTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        names.forEach { (language, name) ->
                            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                            val context = LocalContext.current.createConfigurationContext(configuration)
                            CompositionLocalProvider(
                                LocalContext provides context,
                                LocalConfiguration provides configuration,
                                LocalResources provides context.resources,
                            ) {
                                // As the server gives them, by the day each goes in: 2026-01-05 is a Monday, the 7th a
                                // Wednesday, the 8th a Thursday.
                                IntoFreezerLine(
                                    listOf(
                                        rest("chicken", name[0], "2026-01-05", 120),
                                        rest("turkey", name[1], "2026-01-07", 230),
                                        rest("chicken", name[0], "2026-01-08", 300),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val said = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.replace('\u00A0', ' ') } }
        assertEquals(
            listOf(
                "Into the freezer: Chicken breast, 120 g (Mon) and 300 g (Thu); Turkey breast, 230 g (Wed).",
                "Do zamrażarki: Pierś z kurczaka, 120 g (pon.) i 300 g (czw.); Pierś z indyka, 230 g (śr.).",
            ),
            said,
        )
        compose.keepScreenshot("plans-into-freezer-by-food")
    }
}
