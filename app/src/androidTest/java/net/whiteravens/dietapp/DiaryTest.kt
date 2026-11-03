// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.Manifest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * The diary: what was eaten today on the dashboard and written from there, a recipe and an ingredient
 * found and written, an entry changed and taken out, the plan's day and its meals seen from the diary
 * and the other way, a day copied, and what is written without a network.
 */
@RunWith(AndroidJUnit4::class)
class DiaryTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    private fun number(value: Int) = NumberFormat.getNumberInstance(Locale.getDefault()).apply { isGroupingUsed = false }.format(value)

    /** What is clickable in the window in front: the same words may stand on the screen under it. */
    private fun inTheWindow(words: String): SemanticsMatcher = hasText(words) and hasClickAction() and hasAnyAncestor(isDialog())

    /** Opens the diary from the bar's "More". */
    private fun openTheDiary() {
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.diary_title)).performClick()
        screen.waitForText(text(R.string.diary_subhead))
    }

    /** The way back, wherever the list was scrolled to: a header scrolled far enough is no longer composed. */
    private fun goBack() {
        screen.scrollTo(hasContentDescription(text(R.string.common_back)))
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
    }

    /** A meal of one's own written in the open window, by its calories alone. The first of its name is the kind's own chip. */
    private fun writeMeal(name: String, calories: String) {
        screen.onAllNodes(inTheWindow(text(R.string.diary_kind_meal)))[0].performClick()
        screen.onNode(hasText(text(R.string.diary_meal_name)) and hasSetTextAction()).performScrollTo().performTextInput(name)
        screen.onNode(hasText(text(R.string.diary_calories)) and hasSetTextAction()).performScrollTo().performTextInput(calories)
        screen.putKeyboardAway()
    }

    /** The open window's button that writes what it holds; the window goes. */
    private fun submit() {
        screen.onNode(inTheWindow(text(R.string.diary_submit))).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(inTheWindow(text(R.string.diary_submit))).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun whatWasEatenTodayIsOnTheDashboardAndWrittenFromIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = number(Instance.dailyTarget(account, profileId))
        screen.startSignedIn(account)

        screen.waitForText(text(R.string.diary_eaten_line, "0", target))
        screen.keepScreenshot("diary-today-on-the-dashboard")

        // Its button opens the window to write something, over the diary of today.
        screen.onNode(hasText(text(R.string.diary_add)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.diary_search_recipe))
        screen.keepScreenshot("diary-the-window-on-a-recipe")
        writeMeal("Grandma's soup", "300")
        screen.waitForText(text(R.string.diary_adds_line, "300"))
        screen.keepScreenshot("diary-the-window-on-a-meal-of-ones-own")
        submit()

        // Written by its calories alone, its macronutrients are the API's estimate, and said to be.
        screen.scrollToText("Grandma's soup")
        screen.waitForText("≈ ${text(R.string.diary_estimated)}")
        screen.keepScreenshot("diary-an-entry-of-ones-own")
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf("Grandma's soup") }

        // Back on the dashboard, the day is read anew.
        goBack()
        screen.waitForText("≈ ${text(R.string.diary_eaten_line, "300", target)}")
    }

    @Test
    fun aWindowSaysWhatIsWrongWithWhatItHolds() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        openTheDiary()
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()

        // Nothing is picked: a recipe cannot be written yet, and the window says what it waits for.
        screen.waitForText(text(R.string.diary_pick_recipe_first))

        screen.onNode(inTheWindow(text(R.string.diary_kind_meal))).performClick()
        screen.onNode(hasText(text(R.string.diary_calories)) and hasSetTextAction()).performScrollTo().performTextInput("99999")
        screen.putKeyboardAway()
        screen.onNode(inTheWindow(text(R.string.diary_submit))).performClick()

        screen.waitForText(text(R.string.refusal_empty))
        screen.waitForText(text(R.string.refusal_at_most, number(5000)))
        screen.keepScreenshot("diary-the-window-refusing")
        screen.onNode(inTheWindow(text(R.string.common_cancel))).performClick()
        screen.waitForText(text(R.string.diary_empty_title))
    }

    @Test
    fun aRecipeAndAnIngredientAreFoundWrittenChangedAndTakenOut() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val (_, recipe) = Instance.recipes(account, 1, "en").single()
        val (_, ingredient) = Instance.ingredient(account, "Large egg", "en")
        screen.startSignedIn(account)
        openTheDiary()

        // A recipe found by its title, in a serving and a half.
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        screen.onNode(hasText(text(R.string.diary_search_recipe)) and hasSetTextAction()).performTextInput(recipe)
        val foundRecipe = hasText(recipe) and hasClickAction() and !hasSetTextAction()
        screen.waitUntil(20_000) { screen.onAllNodes(foundRecipe).fetchSemanticsNodes().isNotEmpty() }
        screen.putKeyboardAway()
        screen.keepScreenshot("diary-a-recipe-found")
        screen.onNode(foundRecipe).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.diary_servings_field)) and hasSetTextAction()).performScrollTo().performTextReplacement("1,5")
        screen.putKeyboardAway()
        submit()
        screen.scrollToText(recipe)
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf(recipe) }

        // An ingredient found by its name, in pieces.
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performScrollTo().performClick()
        screen.onNode(inTheWindow(text(R.string.diary_kind_ingredient))).performClick()
        screen.onNode(hasText(text(R.string.diary_search_ingredient)) and hasSetTextAction()).performTextInput(ingredient)
        val foundIngredient = hasText(ingredient) and hasClickAction() and !hasSetTextAction()
        screen.waitUntil(20_000) { screen.onAllNodes(foundIngredient).fetchSemanticsNodes().isNotEmpty() }
        screen.putKeyboardAway()
        screen.onNode(foundIngredient).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.diary_quantity)) and hasSetTextAction()).performScrollTo().performTextInput("150")
        screen.putKeyboardAway()
        screen.keepScreenshot("diary-an-ingredient-picked")
        submit()
        screen.scrollToText(ingredient)
        screen.waitForTheApi { Instance.diaryEntries(account, profileId).toSet() == setOf(recipe, ingredient) }

        // Changed: the amount as it was given, and the figures the API works out anew.
        screen.onNodeWithContentDescription(text(R.string.diary_edit_label, ingredient)).performScrollTo().performClick()
        screen.waitForText(text(R.string.diary_edit_title))
        screen.keepScreenshot("diary-the-window-changing-an-entry")
        screen.onNode(hasText("150") and hasSetTextAction()).performTextReplacement("200")
        screen.putKeyboardAway()
        screen.onNode(inTheWindow(text(R.string.common_save))).performClick()
        screen.waitForText(text(R.string.amount_g, "200"), substring = true)
        screen.keepScreenshot("diary-a-day-with-entries")

        // Taken out, without a question: it is written again as easily.
        screen.onNodeWithContentDescription(text(R.string.diary_remove_label, ingredient)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf(recipe) }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(ingredient).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aPlansDaySaysWhatTheDiaryAddsAndItsMealsAreMarkedFromTheDiary() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val planId = Instance.newPlan(account, profileId)
        val (title, _) = Instance.firstMeal(account, planId)
        Instance.writeInTheDiary(account, profileId, "A large dinner", 3000)
        screen.startSignedIn(account)

        // The plan's day counts it, and says how much of what it holds it is.
        screen.waitForText(text(R.string.overview_title_active))
        screen.onNode(hasText(text(R.string.overview_open)) and hasClickAction()).performScrollTo().performClick()
        val line = plural(R.plurals.plans_diary_line, 1, 1, number(3000))
        screen.scrollToText(line)
        screen.keepScreenshot("diary-as-a-plans-day-says-it")

        // The entry itself stands in the plan's day, under the dinner, marked as the diary's.
        screen.scrollToText("A large dinner")
        screen.waitForText(text(R.string.plans_from_diary_chip))
        screen.keepScreenshot("diary-an-entry-in-the-plans-day")

        // From the plan's day to its diary.
        screen.scrollToText(text(R.string.plans_open_diary))
        screen.onNodeWithText(text(R.string.plans_open_diary)).performClick()
        screen.waitForText(text(R.string.diary_subhead))
        screen.scrollToText(text(R.string.diary_plan_over))
        screen.keepScreenshot("diary-the-plans-day-over-its-target")

        // A meal of the plan marked eaten from the diary is eaten on the plan.
        val eaten = hasContentDescription(text(R.string.diary_eaten_of, title))
        screen.scrollTo(eaten)
        screen.onAllNodes(eaten)[0].performClick()
        screen.waitForTheApi { Instance.firstMealEaten(account, planId) }
        screen.waitUntil(20_000) { screen.onAllNodes(eaten and isOn()).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("diary-a-meal-of-the-plan-eaten")
    }

    @Test
    fun anotherDayIsCopiedIntoThisOneOnce() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        Instance.writeInTheDiary(account, profileId, "Yesterday's stew", 400, LocalDate.now().minusDays(1))
        screen.startSignedIn(account)
        openTheDiary()

        screen.scrollToText(text(R.string.diary_copy_title))
        val (before, after) = text(R.string.diary_copy_button, "\u0000").split("\u0000")
        val button = hasClickAction() and hasText(before, substring = true) and hasText(after, substring = true)
        screen.onNode(button).performScrollTo().performClick()

        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf("Yesterday's stew") }
        screen.scrollToText(plural(R.plurals.diary_copied, 1, 1))
        screen.keepScreenshot("diary-a-day-copied")
        screen.scrollToText(text(R.string.diary_copy_done))
    }

    @Test
    fun whatIsWrittenWithoutANetworkWaitsAndIsInTheDiaryOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = number(Instance.dailyTarget(account, profileId))
        screen.startSignedIn(account)
        openTheDiary()
        screen.waitForText(text(R.string.diary_eaten_line, "0", target))

        screen.airplaneMode(true)
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()

        // A search needs a connection, and says what can be written without one.
        screen.onNode(inTheWindow(text(R.string.diary_kind_recipe))).performClick()
        screen.onNode(hasText(text(R.string.diary_search_recipe)) and hasSetTextAction()).performTextInput("oats")
        screen.waitForText(text(R.string.diary_search_offline))
        screen.putKeyboardAway()
        screen.keepScreenshot("diary-a-search-without-a-network")

        writeMeal("A sandwich", "400")
        submit()

        // It is there, marked, with what is known of it; the sum is the server's, and says what it leaves out.
        screen.scrollToText("A sandwich")
        screen.waitForText(text(R.string.change_waiting))
        screen.waitForText(text(R.string.diary_meal_kcal, "400"), substring = true)
        screen.scrollToText(text(R.string.diary_eaten_line, "0", target))
        screen.waitForText(plural(R.plurals.diary_sum_leaves_out, 1, 1))
        screen.keepScreenshot("diary-an-entry-waiting")

        // With the network back it goes by itself, and the day is read as the server has it.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) { Instance.diaryEntries(account, profileId) == listOf("A sandwich") }
        // Written by its calories alone, its figures are an estimate, and the line says so.
        screen.waitForText("≈ ${text(R.string.diary_eaten_line, "400", target)}", timeoutMillis = 30_000)
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("diary-an-entry-sent")
    }

    @Test
    fun anEntryThatWaitsIsTakenBackUnsent() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = number(Instance.dailyTarget(account, profileId))
        screen.startSignedIn(account)
        openTheDiary()
        screen.waitForText(text(R.string.diary_eaten_line, "0", target))

        screen.airplaneMode(true)
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        writeMeal("Second thoughts", "100")
        submit()
        screen.scrollToText("Second thoughts")

        screen.onNodeWithContentDescription(text(R.string.diary_remove_label, "Second thoughts")).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText("Second thoughts").fetchSemanticsNodes().isEmpty() }

        // With the network back nothing of it goes: what is written next is all the server has.
        screen.airplaneMode(false)
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performScrollTo().performClick()
        writeMeal("A sure thing", "100")
        submit()
        screen.waitForTheApi(30_000) { Instance.diaryEntries(account, profileId) == listOf("A sure thing") }
    }

    /** The kind of product chosen in the open window, and what is typed into its field. */
    private fun typeForAProduct(words: String) {
        screen.onNode(inTheWindow(text(R.string.diary_kind_product))).performClick()
        typeAgainForAProduct(words)
    }

    private fun typeAgainForAProduct(words: String) {
        screen.onNode(hasText(text(R.string.diary_product_field)) and hasSetTextAction()).performScrollTo().performTextReplacement(words)
    }

    @Test
    fun aProductOfOnesOwnIsWrittenWhereNothingIsFoundAndGoesIntoTheDay() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        openTheDiary()
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.diary_search_recipe))
        typeForAProduct("chleb babci")
        screen.waitForText(text(R.string.diary_product_nothing_here))
        screen.onNode(inTheWindow(text(R.string.diary_product_add_own))).performScrollTo().performClick()

        // What cannot be right is said of its field before anything is sent.
        screen.waitForText(text(R.string.products_add_title))
        screen.putKeyboardAway()
        screen.onNode(inTheWindow(text(R.string.common_save))).performClick()
        screen.waitForText(text(R.string.products_err_name))
        screen.waitForText(text(R.string.products_err_calories))
        screen.keepScreenshot("products-the-form-says-what-is-wrong")

        screen.onNode(hasText(text(R.string.products_name)) and hasSetTextAction()).performScrollTo().performTextReplacement("Chleb babci")
        screen.onNode(hasText(text(R.string.products_calories)) and hasSetTextAction()).performScrollTo().performTextReplacement("245")
        screen.putKeyboardAway()
        screen.onNode(inTheWindow(text(R.string.common_save))).performClick()

        // Picked at once, as what the entry is made of, and said to be one's own.
        screen.waitForText(text(R.string.diary_product_source_own))
        screen.keepScreenshot("diary-a-product-of-ones-own-picked")
        screen.onNode(hasText("${text(R.string.diary_quantity)} (g)") and hasSetTextAction()).performScrollTo().performTextReplacement("100")
        screen.waitForText(text(R.string.diary_adds_line, "245"))
        screen.putKeyboardAway()
        submit()
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf("Chleb babci") }
        // Its figures are said to be one's own, not those of Open Food Facts.
        screen.scrollToText("Chleb babci")
        screen.waitForText(text(R.string.diary_entry_source_own))

        // Found again by its name, marked as one's own.
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForText(text(R.string.diary_search_recipe))
        typeForAProduct("chleb babci")
        screen.waitForText("${text(R.string.diary_per_100, "245", "g")} · ${text(R.string.diary_product_own_mark)}")
    }

    @Test
    fun aProductIsFoundByItsNameWrittenInAnAmountAndChanged() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        openTheDiary()
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.diary_search_recipe))
        typeForAProduct("mleko probne")
        screen.waitForText("Mleko próbne 3,2%, Próbna Marka")
        screen.keepScreenshot("diary-a-product-found")
        screen.onNode(inTheWindow("Mleko próbne 3,2%, Próbna Marka")).performScrollTo().performClick()

        // What its label says, with what it does not say said to be so, and where it comes from.
        screen.waitForText(text(R.string.diary_label_energy))
        screen.waitForText(text(R.string.diary_label_unknown))
        screen.waitForText(text(R.string.diary_adds_line, "150"))
        screen.putKeyboardAway()
        screen.keepScreenshot("diary-a-product-picked")
        screen.onNodeWithText(text(R.string.diary_product_source_name)).performScrollTo()
        screen.keepScreenshot("diary-a-product-and-its-source")
        submit()

        screen.scrollToText("Mleko próbne 3,2%, Próbna Marka")
        screen.waitForText(text(R.string.diary_entry_source))
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf("Mleko próbne 3,2%, Próbna Marka") }
        screen.keepScreenshot("diary-a-product-written")

        // Changed in its own unit.
        screen.onNodeWithContentDescription(text(R.string.diary_edit_label, "Mleko próbne 3,2%, Próbna Marka")).performClick()
        screen.onNode(hasText("${text(R.string.diary_quantity)} (ml)") and hasSetTextAction()).performTextReplacement("500")
        screen.onNode(inTheWindow(text(R.string.common_save))).performClick()
        screen.waitForText(text(R.string.diary_meal_kcal, "300"), substring = true)
    }

    @Test
    fun anEntryOfAProductOfTheBaseGoesOnToTheReadersCorrectionWhichSearchesFindInItsPlace() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        val milk = "Mleko próbne 3,2%, Próbna Marka"

        openTheDiary()
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.diary_search_recipe))
        typeForAProduct("mleko probne")
        screen.onNode(inTheWindow(milk)).performScrollTo().performClick()
        screen.waitForText(text(R.string.diary_product_correct_hint))
        screen.putKeyboardAway()
        submit()
        screen.waitForTheApi { Instance.diaryEntries(account, profileId) == listOf(milk) }

        // The entry's window offers the correction; the form starts from what the base says.
        screen.onNodeWithContentDescription(text(R.string.diary_edit_label, milk)).performScrollTo().performClick()
        screen.onNode(inTheWindow(text(R.string.diary_entry_correct))).performScrollTo().performClick()
        // The entry's button is worded as the form's title: the form is open once its intro shows.
        screen.waitForText(text(R.string.products_correct_intro))
        screen.keepScreenshot("diary-a-product-corrected")
        screen.onNode(hasText(text(R.string.products_calories)) and hasSetTextAction()).performScrollTo().performTextReplacement("70")
        screen.putKeyboardAway()
        // The form lies over the entry's window, which has a Save of its own: the form's is the one with its intro.
        val theForm = isDialog() and hasAnyDescendant(hasText(text(R.string.products_correct_intro)))
        screen.onNode(hasText(text(R.string.common_save)) and hasClickAction() and hasAnyAncestor(theForm)).performClick()

        // The entry is of the correction now, and a search finds the correction in the base's place.
        screen.scrollToText(milk)
        screen.waitForText(text(R.string.diary_entry_source_own))
        screen.keepScreenshot("diary-an-entry-of-a-correction")
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performScrollTo().performClick()
        typeForAProduct("mleko probne")
        screen.waitForText(text(R.string.diary_product_own_mark), substring = true)
    }

    @Test
    fun aCodeIsAskedAboutOnAWordAndWhatIsWrongWithItIsSaid() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        openTheDiary()
        screen.onNode(hasText(text(R.string.diary_add_title)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.diary_search_recipe))

        // A slip of the finger is said at once, and asks nobody.
        typeForAProduct("5900512320358")
        screen.onNode(inTheWindow(text(R.string.diary_product_check))).performClick()
        screen.waitForText(text(R.string.diary_product_code_bad))
        screen.keepScreenshot("diary-a-code-mistyped")

        // Open Food Facts cannot be reached from the instance of these tests: that is said, with the way on.
        typeAgainForAProduct("5901112223330")
        screen.onNode(inTheWindow(text(R.string.diary_product_check))).performClick()
        screen.waitForText(text(R.string.diary_product_as_own))
        screen.keepScreenshot("diary-a-code-that-cannot-be-asked-about")

        // A product of the instance's own table, whose figures do not add up: it can be written, with a word.
        typeAgainForAProduct("5901234123457")
        screen.onNode(inTheWindow(text(R.string.diary_product_check))).performClick()
        screen.waitForText("Baton próbny")
        screen.waitForText(text(R.string.diary_product_doubtful))
        screen.putKeyboardAway()
        screen.keepScreenshot("diary-a-product-that-does-not-add-up")

        // One without figures cannot, and becomes a meal of its name.
        screen.onNode(inTheWindow(text(R.string.diary_change_pick))).performScrollTo().performClick()
        typeAgainForAProduct("4001724819806")
        screen.onNode(inTheWindow(text(R.string.diary_product_check))).performClick()
        screen.waitForText(text(R.string.diary_product_no_figures))
        screen.onNode(inTheWindow(text(R.string.diary_product_as_own))).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.diary_meal_name)) and hasSetTextAction()).performScrollTo()
        screen.waitForText("Bez etykiety próbny")
    }

    @Test
    fun theCameraIsOfferedAndCanBePutAside() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.CAMERA)
        screen.startSignedIn(account)

        screen.waitForText(text(R.string.diary_today_title))
        screen.onNode(hasText(text(R.string.diary_product_scan)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.scanner_title))
        screen.keepScreenshot("diary-the-camera")

        // Typing the code is always there, and leads back to the window, on a product.
        screen.onNodeWithText(text(R.string.scanner_type_instead)).performClick()
        screen.waitForText(text(R.string.diary_product_field))
    }
}
