// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.DiaryDay
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.ui.screens.diary.DayFigures
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The diary's figures of a day on low-carb: carbohydrate said against the diet's band where it stands, over it in the
 * colour of a warning, in it and under it as a note, in Polish and in English, in the light and in the dark.
 */
@RunWith(AndroidJUnit4::class)
class DiaryCarbsTest {
    @get:Rule
    val compose = createComposeRule()

    /** A day of low-carb whose band reaches from 59 g to 118 g, with [carbs] eaten. */
    private fun day(carbs: Double) = DiaryDay(
        profileId = "6f1d2a40-0000-4000-8000-000000000002",
        date = "2026-11-24",
        target = Nutrition(2350.0, 176.0, 130.0, 118.0, carbsFloor = 59.0),
        targetFromPlan = true,
        plan = null,
        entries = emptyList(),
        eaten = Nutrition(1040.0, 30.0, 30.0, carbs),
        estimated = false,
    )

    private fun show(dark: Boolean) {
        compose.setContent {
            DietAppTheme(darkTheme = dark) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf("pl" to 160.0, "pl" to 90.0, "pl" to 20.0, "en" to 160.0).forEach { (language, carbs) ->
                            // In the language named, whatever language a test before it left the application in.
                            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                            val context = LocalContext.current.createConfigurationContext(configuration)
                            CompositionLocalProvider(
                                LocalContext provides context,
                                LocalConfiguration provides configuration,
                                LocalResources provides context.resources,
                            ) {
                                DayFigures(day(carbs), leftOut = 0, labelLine = false)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun carbohydrateOnLowCarbSaysWhereItStandsAgainstTheBand() {
        show(dark = false)
        // Each figure is read as one: its name, what was eaten of it and what the day aims at.
        compose.onNodeWithContentDescription("Węglowodany, 160\u00A0g, ponad normę 59–\u2060118\u00A0g").assertExists()
        compose.onNodeWithContentDescription("Węglowodany, 90\u00A0g, w\u00A0normie 59–\u2060118\u00A0g").assertExists()
        compose.onNodeWithContentDescription("Węglowodany, 20\u00A0g, poniżej normy 59–\u2060118\u00A0g").assertExists()
        compose.onNodeWithContentDescription("Carbs, 160\u00A0g, above the range 59–\u2060118\u00A0g").assertExists()
        compose.onAllNodesWithContentDescription("normie", substring = true).assertCountEquals(1)
        compose.keepScreenshot("diary-carbs-against-the-band")
    }

    @Test
    fun carbohydrateOverTheBandIsSaidInTheColourOfAWarningInTheDarkToo() {
        show(dark = true)
        compose.onNodeWithContentDescription("Węglowodany, 160\u00A0g, ponad normę 59–\u2060118\u00A0g").assertExists()
        compose.keepScreenshot("diary-carbs-against-the-band-dark")
    }
}
