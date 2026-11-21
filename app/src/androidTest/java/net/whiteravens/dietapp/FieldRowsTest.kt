// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.domain.DIFFICULTIES
import net.whiteravens.dietapp.testing.assertInOneRow
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.ui.common.cuisineLabel
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.FieldRow
import net.whiteravens.dietapp.ui.components.SideBySide
import net.whiteravens.dietapp.ui.screens.plans.DATE_AND_LENGTH
import net.whiteravens.dietapp.ui.screens.plans.MACROS_SIDE_BY_SIDE
import net.whiteravens.dietapp.ui.screens.profiles.BODY_FIGURES
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.ProfileChoices
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import net.whiteravens.dietapp.ui.screens.recipes.DIFFICULTY_AND_CUISINE
import net.whiteravens.dietapp.ui.screens.recipes.SERVINGS_AND_TIMES
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * Every row of short fields at the narrowest width it stands side by side in, at the usual size of text, in English and
 * in Polish: each field's label takes one line, so the frames stand level. The fields are the screens' own, with their
 * labels, units and the longest of what a choice may show.
 */
@RunWith(AndroidJUnit4::class)
class FieldRowsTest {
    @get:Rule
    val compose = createComposeRule()

    /** [id] as [language] words it. */
    private fun said(language: String, @StringRes id: Int, vararg args: Any): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(configuration).getString(id, *args)
    }

    /** Lays [row] out in exactly the width it needs, in [language] and at the usual size of text, [fields] placed in it. */
    private fun atItsNeed(row: FieldRow, language: String, fields: @Composable (place: (Int) -> Modifier) -> Unit) {
        compose.setContent {
            DietAppTheme {
                Surface {
                    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
                    val context = LocalContext.current.createConfigurationContext(configuration)
                    CompositionLocalProvider(
                        LocalContext provides context,
                        LocalConfiguration provides configuration,
                        LocalResources provides context.resources,
                        LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f),
                    ) {
                        Box(Modifier.padding(16.dp).width(row.need)) { SideBySide(row, fields = fields) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The fields labelled [labels] stand in one row, one height, and the picture is kept as [name] in [language]. */
    private fun standLevel(name: String, language: String, vararg labels: String) {
        compose.assertInOneRow(*labels.map { hasText(it) }.toTypedArray())
        compose.keepScreenshot("rows-$name-$language")
    }

    private fun dateAndLength(language: String) {
        atItsNeed(DATE_AND_LENGTH, language) { place ->
            DateField(said(language, R.string.plans_start_date), LocalDate.of(2026, 11, 28), onPick = {}, modifier = place(0))
            FormField(value = "14", onValueChange = {}, label = said(language, R.string.plans_duration_days), modifier = place(1))
        }
        standLevel("date-and-length", language, said(language, R.string.plans_start_date), said(language, R.string.plans_duration_days))
    }

    @Test fun whenAPlanBeginsAndForHowLongStandLevelInEnglish() = dateAndLength("en")

    @Test fun whenAPlanBeginsAndForHowLongStandLevelInPolish() = dateAndLength("pl")

    private fun macros(language: String) {
        val labels = listOf(R.string.plans_custom_protein, R.string.plans_custom_fat, R.string.plans_custom_carbs).map { said(language, it) }
        atItsNeed(MACROS_SIDE_BY_SIDE, language) { place ->
            labels.forEachIndexed { index, label ->
                FormField(value = "120", onValueChange = {}, label = label, suffix = said(language, R.string.unit_g), modifier = place(index))
            }
        }
        standLevel("macros", language, *labels.toTypedArray())
    }

    @Test fun theMacronutrientsOfAMealOfOnesOwnStandLevelInEnglish() = macros("en")

    @Test fun theMacronutrientsOfAMealOfOnesOwnStandLevelInPolish() = macros("pl")

    private fun body(language: String) {
        val labels = listOf(
            said(language, R.string.profile_age),
            said(language, R.string.profile_height_cm),
            said(language, R.string.profile_weight, WeightUnit.KG.code),
        )
        atItsNeed(BODY_FIGURES, language) { place ->
            listOf("31", "182.5", "80.5").forEachIndexed { index, value ->
                FormField(value = value, onValueChange = {}, label = labels[index], modifier = place(index))
            }
        }
        standLevel("body", language, *labels.toTypedArray())
    }

    @Test fun aProfilesAgeHeightAndWeightStandLevelInEnglish() = body("en")

    @Test fun aProfilesAgeHeightAndWeightStandLevelInPolish() = body("pl")

    private fun servingsAndTimes(language: String) {
        val labels = listOf(R.string.own_recipe_field_servings, R.string.own_recipe_field_prep, R.string.own_recipe_field_cook).map { said(language, it) }
        atItsNeed(SERVINGS_AND_TIMES, language) { place ->
            FormField(value = "4", onValueChange = {}, label = labels[0], modifier = place(0))
            FormField(value = "120", onValueChange = {}, label = labels[1], suffix = said(language, R.string.unit_min), modifier = place(1))
            FormField(value = "120", onValueChange = {}, label = labels[2], suffix = said(language, R.string.unit_min), modifier = place(2))
        }
        standLevel("servings-and-times", language, *labels.toTypedArray())
    }

    @Test fun aRecipesServingsAndTimesStandLevelInEnglish() = servingsAndTimes("en")

    @Test fun aRecipesServingsAndTimesStandLevelInPolish() = servingsAndTimes("pl")

    private fun difficultyAndCuisine(language: String) {
        val difficulty = said(language, R.string.recipes_difficulty)
        val cuisine = said(language, R.string.recipes_cuisine)
        atItsNeed(DIFFICULTY_AND_CUISINE, language) { place ->
            // Each shows the longest of its options in the language, the cuisine's own word for none among them.
            val hardest = DIFFICULTIES.maxBy { difficultyLabel(it).length }
            ChoiceField(difficulty, DIFFICULTIES, hardest, { difficultyLabel(it) }, onSelect = {}, modifier = place(0))
            val cuisines = listOf<String?>(null) + ProfileChoices.CUISINES
            val none = said(language, R.string.own_recipe_cuisine_none)
            val cuisineOf: @Composable (String?) -> String = { code -> if (code == null) none else cuisineLabel(code) }
            val longest = cuisines.maxBy { code -> if (code == null) none.length else cuisineLabel(code).length }
            ChoiceField(cuisine, cuisines, longest, cuisineOf, onSelect = {}, modifier = place(1))
        }
        standLevel("difficulty-and-cuisine", language, difficulty, cuisine)
    }

    @Test fun aRecipesDifficultyAndCuisineStandLevelInEnglish() = difficultyAndCuisine("en")

    @Test fun aRecipesDifficultyAndCuisineStandLevelInPolish() = difficultyAndCuisine("pl")
}
