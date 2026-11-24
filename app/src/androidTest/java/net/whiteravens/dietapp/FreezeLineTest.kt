// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.FrozenRest
import net.whiteravens.dietapp.domain.Thaw
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.ui.screens.plans.FreezeLine
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The line of a plan's day that says to freeze the rest of a pack the plan takes in several portions: every portion
 * with its day, the part for later last, joined as the language joins a list, in English and in Polish; and its
 * "Dodaj do spiżarni", offered from the day the rest goes in the freezer.
 */
@RunWith(AndroidJUnit4::class)
class FreezeLineTest {
    @get:Rule
    val compose = createComposeRule()

    private fun rest(name: String, vararg thawed: Thaw, left: Int) =
        FrozenRest("6f1d2a40-0000-4000-8000-000000000001", name, "g", "2026-11-17", null, 300, thawed.toList(), left, inPantry = false)

    @Test
    fun aRestInSeveralPortionsNamesEachWithItsDayAndThePartForLater() {
        val names = linkedMapOf("en" to "Chicken breast", "pl" to "Pierś z kurczaka")
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
                                // 2026-11-19 is a Thursday, 2026-11-21 a Saturday.
                                FreezeLine(rest(name, Thaw("2026-11-19", null, 122), left = 178), onKeep = {})
                                FreezeLine(rest(name, Thaw("2026-11-19", null, 122), Thaw("2026-11-21", null, 100), left = 78), onKeep = {})
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // As the eye reads them: Polish holds its words of one letter to the next with a no-break space.
        val said = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.replace(' ', ' ') } }
            .filter { it.startsWith("Freeze") || it.startsWith("Zamroź") }
        assertEquals(
            listOf(
                "Freeze the rest of the pack at once in separate portions: Chicken breast, 122 g (Thu) and 178 g for later.",
                "Freeze the rest of the pack at once in separate portions: Chicken breast, 122 g (Thu), 100 g (Sat), and 78 g for later.",
                "Zamroź od razu resztę opakowania w osobnych porcjach: Pierś z kurczaka, 122 g (czw.) i 178 g na później.",
                "Zamroź od razu resztę opakowania w osobnych porcjach: Pierś z kurczaka, 122 g (czw.), 100 g (sob.) i 78 g na później.",
            ),
            said,
        )
        compose.keepScreenshot("plans-freeze-in-portions")
    }

    @Test
    fun aRestOfWhatWasBoughtByWeightSaysSoWithTheSizeForLaterAndTheRulesFolded() {
        // The phone in English too, as the content is: the picture checks its words as a phone in that language does.
        val phone = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            compose.setContent {
                DietAppTheme {
                    Surface {
                        Column(Modifier.padding(16.dp)) {
                            // In English, whatever language a test before it left the application in.
                            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
                            val context = LocalContext.current.createConfigurationContext(configuration)
                            CompositionLocalProvider(
                                LocalContext provides context,
                                LocalConfiguration provides configuration,
                                LocalResources provides context.resources,
                            ) {
                                // 2026-11-19 is a Thursday. Two days of the plan eat 100 g at most: the 178 g for later go in two.
                                FreezeLine(rest("Turkey breast", Thaw("2026-11-19", null, 122), left = 178).copy(loose = true, portion = 100), onKeep = {})
                                // A rest for later no bigger than two days eat is not to be split.
                                FreezeLine(rest("Chicken breast", left = 90).copy(portion = 100), onKeep = {})
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText(
                "Freeze the rest of what you bought by weight at once in separate portions: Turkey breast, 122\u00A0g (Thu) and 178\u00A0g for later.",
            ).assertIsDisplayed()
            compose.onAllNodesWithText(
                "Split the part for later into portions of up to 100\u00A0g: that is the most this plan eats of it in two days, and a plan takes only whole packages out of the freezer.",
            ).assertCountEquals(1)
            compose.onAllNodesWithText("How to freeze").assertCountEquals(2)
            // The rules stand behind their line, for whoever asks.
            compose.onAllNodesWithText("• Do not freeze thawed raw meat or fish again.").assertCountEquals(0)
            compose.onAllNodesWithText("How to freeze")[0].performClick()
            compose.onNodeWithText("• Do not freeze thawed raw meat or fish again.").assertIsDisplayed()
            compose.keepScreenshot("plans-freeze-bought-by-weight")
        } finally {
            Locale.setDefault(phone)
        }
    }

    @Test
    fun aRestIsOfferedToThePantryFromTheDayThePlanFreezesIt() {
        val keep = text(R.string.plans_freeze_keep)
        // The rest goes in the freezer on 2026-11-17.
        var today by mutableStateOf("2026-11-16")
        compose.setContent {
            DietAppTheme {
                Surface { Column(Modifier.padding(16.dp)) { FreezeLine(rest("Chicken breast", left = 300), onKeep = {}, today = today) } }
            }
        }
        // The day before: given then, the pantry would hold what was not even bought.
        compose.onAllNodesWithText(keep).assertCountEquals(0)
        compose.keepScreenshot("plans-freeze-a-rest-of-a-later-day")
        today = "2026-11-17"
        compose.onNodeWithText(keep).assertIsDisplayed()
    }
}
