// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.Borrowed
import net.whiteravens.dietapp.domain.CarriedTo
import net.whiteravens.dietapp.domain.EatenFood
import net.whiteravens.dietapp.domain.EatenOf
import net.whiteravens.dietapp.domain.EatenPantry
import net.whiteravens.dietapp.domain.EffectList
import net.whiteravens.dietapp.domain.LeftoverOffer
import net.whiteravens.dietapp.domain.LeftoverPlace
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlace
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.OtherPortion
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.PlanEffects
import net.whiteravens.dietapp.domain.Portion
import net.whiteravens.dietapp.domain.Pot
import net.whiteravens.dietapp.domain.SharedMeal
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShownAmount
import net.whiteravens.dietapp.testing.EachLanguage
import net.whiteravens.dietapp.testing.shown
import net.whiteravens.dietapp.ui.common.PantryQuestion
import net.whiteravens.dietapp.ui.common.PantryWindow
import net.whiteravens.dietapp.ui.components.EatenPantryDialog
import net.whiteravens.dietapp.ui.screens.pantry.RowItem
import net.whiteravens.dietapp.ui.screens.pantry.StillGoodDialog
import net.whiteravens.dietapp.ui.screens.plans.LeftoverDialog
import net.whiteravens.dietapp.ui.screens.plans.MealCard
import net.whiteravens.dietapp.ui.screens.plans.PlanDetailViewModel
import net.whiteravens.dietapp.ui.screens.plans.RebalanceCard
import net.whiteravens.dietapp.ui.screens.plans.SplitDialog
import net.whiteravens.dietapp.ui.screens.shopping.PastInPantryBanner
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingItemRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * What a meal eaten from a pot cooked already says of the pot, the window its rest goes from, the card after a change
 * answered with the plan and after a day made again, the pantry's stock past its day and its window, and a list that
 * counts such stock, and the window that takes what was eaten off the pantry: in English, then in Polish, each kept for
 * the eye as a phone in that language keeps it.
 */
@RunWith(AndroidJUnit4::class)
class SeamsLinesTest {
    @get:Rule
    val compose = createComposeRule()

    private val eye = EachLanguage(compose)

    private val today = LocalDate.now().toString()
    private val tomorrow = LocalDate.now().plusDays(1).toString()
    private val dayAfter = LocalDate.now().plusDays(2).toString()

    private fun meal(
        id: String,
        name: String,
        servings: Double = 1.0,
        scale: Double = 1.0,
        pot: Pot? = null,
        carriedTo: CarriedTo? = null,
        sameAgain: Boolean = false,
    ) = Meal(
        id = id,
        slot = "lunch",
        name = name,
        recipeId = "recipe-$id",
        servings = servings,
        eaten = false,
        offDiet = false,
        nutrition = Nutrition(560.0 * servings * scale, 32.0, 18.0, 70.0),
        lines = listOf(MealLine("lentils", "Lentils", 120.0, "g")),
        allergens = emptyList(),
        conflicts = emptyList(),
        disliked = emptyList(),
        custom = false,
        quantityScale = scale,
        cookAhead = "cooked",
        byModel = null,
        outsideLimits = emptyList(),
        onlyRecipe = false,
        sameAgain = sameAgain,
        pot = pot,
        carriedTo = carriedTo,
    )

    @androidx.compose.runtime.Composable
    private fun Card(meal: Meal, onlyFor: String? = null) = MealCard(
        meal = meal,
        date = tomorrow,
        today = today,
        changeable = true,
        parted = null,
        swapping = false,
        waiting = false,
        added = false,
        readOnly = false,
        busy = false,
        aiOn = false,
        onEaten = {},
        onChange = {},
        onPickFavorite = {},
        onSubstitute = {},
        onRemove = {},
        onOpen = {},
        onlyFor = onlyFor,
    )

    @Test
    fun aMealFromAPotSaysWhatThePotWillHaveLeftOrThatItHoldsThePortionAndKeepsItsPortionOnceItsRestWent() {
        eye.show { language ->
            val name = if (language == "en") "Red lentil stew" else "Gulasz soczewicowy"
            Card(meal("rest", name, scale = 0.9, pot = Pot(cooked = 1.2, rest = 0.3, held = false, offer = "ok")))
            Card(meal("held", name, servings = 2.0, scale = 1.1, pot = Pot(cooked = 2.2, rest = 0.0, held = true, offer = null)))
            Card(
                meal(
                    "carried",
                    name,
                    servings = 1.0,
                    scale = 1.9,
                    pot = Pot(cooked = 2.2, rest = 0.0, held = true, offer = null),
                    carriedTo = CarriedTo(dayAfter, "lunch", fromPot = true),
                ),
            )
        }

        eye.check("plans-pot-lines") { language, lines ->
            val (rest, held, kept) =
                if (language == "en") Triple("The pot will have 0.3 servings left", "From the pot at most 2.2 servings: no more was cooked.", "1.9 servings")
                else Triple("W garnku zostanie 0,3 porcji", "Z garnka najwyżej 2,2 porcji: więcej nie ugotowano.", "1,9 porcji")
            assertTrue(lines.toString(), lines.any { it.startsWith(rest) })
            // Held once alone: the meal whose rest went says where it went instead.
            assertTrue(lines.toString(), lines.count { it == held } == 1)
            assertTrue(lines.toString(), kept in lines)
        }
    }

    // The window is one of its own, drawn in the phone's configuration rather than the test's: the names put in it hold
    // no word of one letter, which only a Polish configuration holds.
    @Test
    fun aMealOfAPlanMadeTogetherSaysWhetherBothEatItAndHowItIsCookedOrWhoseAloneItIs() {
        val ewa = OtherPortion("meal-9", "plan-2", "Ewa", 1.0, 520.0, eaten = false)
        eye.show { language ->
            val (stew, soup, oats) = if (language == "en") Triple("Lentil stew", "Tomato soup", "Porridge") else Triple("Gulasz z soczewicy", "Zupa pomidorowa", "Owsianka")
            Column {
                Card(meal("apart", stew).copy(shared = SharedMeal("s-1", apart = true, with = ewa, pot = emptyList())), onlyFor = "Paweł")
                Card(meal("pot", soup).copy(shared = SharedMeal("s-2", apart = false, with = ewa, pot = emptyList())), onlyFor = "Paweł")
                Card(meal("alone", oats).copy(slot = "breakfast"), onlyFor = "Paweł")
            }
        }

        eye.check("together-chips") { language, lines ->
            val chips = if (language == "en") listOf("Shared · apart", "Shared", "Only Paweł") else listOf("Wspólne · osobno", "Wspólne", "Tylko Paweł")
            for (chip in chips) assertTrue(lines.toString(), chip in lines)
        }
    }

    @Test
    fun aSplitLeavesOutThePortionsEatenAlreadyNamingThemAndSplitsWhatStaysInThePotAsAPortionOfItsOwn() {
        eye.show { language ->
            val stew = if (language == "en") "Lentil stew" else "Gulasz z soczewicy"
            // Cooked today for three days: today's portion eaten, the pot holding half a serving more than tomorrow takes.
            SplitDialog(
                listOf(
                    meal("today", stew).copy(eaten = true, cookAhead = "cook") to today,
                    meal("tomorrow", stew, pot = Pot(cooked = 1.5, rest = 0.5, held = false, offer = "ok")) to tomorrow,
                    meal("after", stew) to dayAfter,
                ),
                ownName = "Paweł",
                onDismiss = {},
            )
        }

        eye.check("split-eaten-and-rest") { language, lines ->
            val (leftOut, rest) = if (language == "en") "Leaving out the portions eaten already: " to "Left in the pot" else "Bez porcji już zjedzonych: " to "Zostaje w garnku"
            assertTrue(lines.toString(), lines.any { it.startsWith(leftOut) })
            assertTrue(lines.toString(), rest in lines)
            // No pot is kept: it is weighed before it is filled.
            val emptyPot = if (language == "en") "Weigh the empty pot before you start cooking: the split needs it." else "Zważ pusty garnek, zanim zaczniesz gotować: przyda się przy podziale."
            assertTrue(lines.toString(), emptyPot in lines)
        }
    }

    @Test
    fun aSplitOfADishWhosePlatesDifferOpensOnOnePartAndSaysWhyTheWholeDishDoesNotFit() {
        val ewa = OtherPortion("meal-9", "plan-2", "Ewa", 1.0, 520.0, eaten = false, lines = listOf(MealLine("lentils", "Lentils", 90.0, "g", share = 0.8)))
        eye.show { language ->
            val stew = if (language == "en") "Lentil stew" else "Gulasz z soczewicy"
            val mine = meal("mine", stew).let { it.copy(lines = it.lines.map { line -> line.copy(share = 1.2) }, shared = SharedMeal("s-1", apart = false, with = ewa, pot = emptyList())) }
            SplitDialog(listOf(mine to today), ownName = "Paweł", onDismiss = {})
        }

        eye.check("split-plated") { language, lines ->
            val (part, plated) =
                if (language == "en") "One part" to "The plates of this meal hold their parts in different proportions: split it one part at a time."
                else "Jeden składnik" to "Talerze tego posiłku mają różne proporcje: dziel go składnik po składniku."
            assertTrue(lines.toString(), plated in lines)
            compose.onNode(hasText(part) and isSelected() and hasAnyAncestor(isDialog())).assertExists()
        }
    }

    @Test
    fun theRestOfAPotGoesToTheNextDayFromItsOwnWindowAskingNothingOfWhatWasEaten() {
        eye.show { language ->
            val name = if (language == "en") "Red lentil stew" else "Gulasz soczewicowy"
            LeftoverDialog(
                window = PlanDetailViewModel.LeftoverWindow(
                    meal = meal("rest", name, pot = Pot(cooked = 1.2, rest = 0.3, held = false, offer = "ok")),
                    date = tomorrow,
                    offer = LeftoverOffer(
                        planId = "plan",
                        date = dayAfter,
                        portion = Portion(0.3, 168.0),
                        places = listOf(LeftoverPlace("lunch", if (language == "en") "Chickpea salad" else "Sałatka ciecierzycowa", 610.0, null, null, emptyList())),
                        other = null,
                    ),
                    fromPot = true,
                ),
                onSend = { _, _, _ -> },
                onDismiss = {},
            )
        }

        eye.check("plans-pot-window") { language, lines ->
            val (title, summary) = if (language == "en") "The rest of the pot" to "This meal stays as it is." else "Resztka z garnka" to "Ten posiłek zostaje taki, jaki jest."
            assertTrue(lines.toString(), title in lines)
            assertTrue(lines.toString(), summary in lines)
        }
    }

    @Test
    fun theCardAfterAChangeAnsweredWithThePlanIsNamedByItAndADayMadeAgainSaysWhichMealsKeptTheirRecipe() {
        eye.show {
            RebalanceCard(
                rebalance = null,
                day = null,
                notice = null,
                busy = false,
                onUndo = {},
                onRest = {},
                onDismiss = {},
                cause = "day-settings",
                effects = PlanEffects(listOf(EffectList("list", "plan", "profile", today, dayAfter, null)), emptyList(), emptyList()),
            )
            RebalanceCard(
                rebalance = null,
                day = null,
                notice = null,
                busy = false,
                onUndo = {},
                onRest = {},
                onDismiss = {},
                cause = "reroll",
                same = listOf(MealPlace(tomorrow, "breakfast")),
            )
            Card(meal("same", "Buckwheat bowl", sameAgain = true))
        }

        eye.check("plans-change-cards") { language, lines ->
            val (settings, reroll, same, hint) =
                if (language == "en") {
                    listOf(
                        "Settings of the day",
                        "New recipes",
                        "The same recipe as before: breakfast. No other fits the profile and the plan's limits.",
                        "The same recipe as before the day was made again: no other fits the profile and the plan's limits.",
                    )
                } else {
                    listOf(
                        "Ustawienia dnia",
                        "Nowe przepisy",
                        "Ten sam przepis co wcześniej: śniadanie. Inne nie pasują do profilu i limitów planu.",
                        "Ten sam przepis co przed ułożeniem dnia od nowa: inne nie pasują do profilu i limitów planu.",
                    )
                }
            assertTrue(lines.toString(), settings in lines)
            assertTrue(lines.toString(), reroll in lines)
            assertTrue(lines.toString(), same in lines)
            assertTrue(lines.toString(), hint in lines)
        }
    }

    @Test
    fun stockPastItsDaySaysSoAndOffersToSayItIsStillGoodAndStockSaidSoSaysThat() {
        val yesterday = LocalDate.now().minusDays(1)
        eye.show { language ->
            val bread = if (language == "en") "Wholemeal bread" else "Chleb pełnoziarnisty"
            val milk = if (language == "en") "Whole milk" else "Mleko 3,2%"
            RowItem(
                row = PantryRow("bread", "bread", bread, "grains", 10.0, "slice", yesterday, shownGrams = 400.0, unit = "piece"),
                number = null,
                waiting = false,
                added = false,
                enabled = true,
                onChange = {},
                onRemove = {},
                onFreeze = {},
            )
            RowItem(
                row = PantryRow("milk", "milk", milk, "dairy", 1000.0, "ml", yesterday, keptPastDate = true),
                number = null,
                waiting = false,
                added = false,
                enabled = true,
                onChange = {},
                onRemove = {},
                onFreeze = {},
            )
        }

        eye.check("pantry-past-date") { language, lines ->
            val (past, still, kept) =
                if (language == "en") Triple("past its date", "Still good", "past its date, still good")
                else Triple("termin minął", "Nadal dobre", "po terminie, nadal dobre")
            assertTrue(lines.toString(), past in lines)
            assertTrue(lines.toString(), still in lines)
            assertTrue(lines.toString(), kept in lines)
        }
    }

    @Test
    fun theStillGoodWindowAsksForANewDayOrNone() {
        eye.show { language ->
            val bread = if (language == "en") "Wholemeal bread" else "Chleb pełnoziarnisty"
            StillGoodDialog(
                row = PantryRow("bread", "bread", bread, "grains", 10.0, "slice", LocalDate.now().minusDays(1), shownGrams = 400.0, unit = "piece"),
                day = null,
                sending = false,
                failure = null,
                onDay = {},
                onKeep = {},
                onDismiss = {},
            )
        }

        eye.check("pantry-still-good-window") { language, lines ->
            // The field says it may stay empty in its own mark, heard as its state, as every optional field of the app does.
            val (date, optional) = if (language == "en") "New best-before day" to "optional" else "Nowa data ważności" to "opcjonalnie"
            assertTrue(lines.toString(), date in lines)
            compose.onNode(hasStateDescription(optional) and hasAnyAncestor(isDialog())).assertExists()
        }
    }

    @Test
    fun aListThatCountsStockPastItsDaySaysSoAboveItsRowsAndOnTheRow() {
        eye.show { language ->
            PastInPantryBanner(onUpdate = {}, busy = false)
            ShoppingItemRow(
                ShoppingItem(
                    id = "bread-$language",
                    name = if (language == "en") "Wholemeal bread" else "Chleb pełnoziarnisty",
                    totalQuantity = 6.0,
                    toBuyQuantity = 0.0,
                    unit = "piece",
                    displayUnit = "slice",
                    atHome = 6.0,
                    purchasedQuantity = null,
                    pantryBestBefore = null,
                    estimatedCalories = 480.0,
                    packs = null,
                    checked = false,
                    pastInPantry = true,
                ),
                busy = false,
                readOnly = false,
                waiting = false,
                onChange = {},
            )
        }

        eye.check("shopping-past-in-pantry") { language, lines ->
            val (banner, row) =
                if (language == "en") "Some of the stock this list counts at home is past its best-before day." to "Some of this at home is past its best-before day."
                else "Część zapasu, na który liczy ta lista, jest po terminie." to "Część tego w domu jest po terminie."
            assertTrue(lines.toString(), banner in lines)
            assertTrue(lines.toString(), row in lines)
        }
    }

    private fun food(id: String, name: String, eaten: ShownAmount, held: ShownAmount, taken: ShownAmount = eaten) = EatenFood(id, name, eaten, held, taken)

    /** What a breakfast of oats with milk and two eggs ate, as a pantry that counts eggs in pieces shows it; less milk held than drunk. */
    private fun breakfast(language: String) = listOf(
        food("eggs", if (language == "en") "Eggs" else "Jajka", ShownAmount(2.0, "piece", 110.0), ShownAmount(6.0, "piece", 330.0)),
        food("oats", if (language == "en") "Rolled oats" else "Płatki owsiane", ShownAmount(60.0, "g"), ShownAmount(500.0, "g")),
        food("milk", if (language == "en") "Milk" else "Mleko", ShownAmount(250.0, "ml"), ShownAmount(150.0, "ml"), ShownAmount(150.0, "ml")),
    )

    /** What an entry of a shakshuka took off the pantry. */
    private fun shakshuka(language: String) = listOf(
        food("eggs", if (language == "en") "Eggs" else "Jajka", ShownAmount(3.0, "piece", 165.0), ShownAmount(6.0, "piece", 330.0)),
        food("passata", if (language == "en") "Tomato passata" else "Passata pomidorowa", ShownAmount(400.0, "g"), ShownAmount(800.0, "g")),
    )

    private fun pantry(name: String, offer: List<EatenFood> = emptyList(), taken: List<EatenFood> = emptyList()) = EatenPantry(name, offer, taken)

    /** The window of [question] about [of], naming [pantries]: two of a meal both ate. */
    private fun window(question: PantryQuestion, vararg pantries: EatenPantry, of: EatenOf = EatenOf.Meal("plan", "meal")) =
        PantryWindow(question, of, pantries.toList(), both = pantries.size > 1)

    @Test
    fun theWindowOffersToTakeWhatAMealAteOffThePantryFoodByFood() {
        eye.show { language ->
            EatenPantryDialog(window(PantryQuestion.TAKE, pantry("Paweł", offer = breakfast(language))), onConfirm = {}, onRemoveOnly = {}, onDismiss = {})
        }

        eye.check("pantry-eaten-take") { language, lines ->
            val (title, milk) =
                if (language == "en") "Take what was eaten off the pantry" to "eaten 250 ml · in the pantry 150 ml · you take 150 ml"
                else "Zdejmij ze spiżarni to, co zjedzono" to "zjedzone 250 ml · w spiżarni 150 ml · zdejmiesz 150 ml"
            assertTrue(lines.toString(), title in lines)
            assertTrue(lines.toString(), milk in lines)
            // One person's portion is named by nobody.
            assertTrue(lines.toString(), "Paweł" !in lines)
        }
    }

    @Test
    fun aMealBothAteIsTwoPortionsEachUnderItsPersonsName() {
        eye.show { language ->
            EatenPantryDialog(
                window(PantryQuestion.TAKE, pantry("Paweł", offer = breakfast(language)), pantry("Ewa", offer = breakfast(language).take(2))),
                onConfirm = {},
                onRemoveOnly = {},
                onDismiss = {},
            )
        }

        eye.check("pantry-eaten-both") { language, lines ->
            val note =
                if (language == "en") "Each person's portion comes off their own pantry first, and what it lacks off the other person's."
                else "Porcja każdej osoby schodzi najpierw z jej spiżarni, a to, czego w niej brakuje, ze spiżarni drugiej osoby."
            assertTrue(lines.toString(), note in lines)
            for (name in listOf("Paweł", "Ewa")) {
                compose.onNode(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasAnyAncestor(isDialog())).assertExists()
            }
        }
    }

    @Test
    fun whatAPortionsOwnPantryLacksComesOffTheOtherPersonsAndItsLineSaysWhose() {
        eye.show { language ->
            // Paweł's pantry holds one egg of the two he ate: the other comes off Ewa's, as her milk does of his.
            val eggs = food("eggs", if (language == "en") "Eggs" else "Jajka", ShownAmount(2.0, "piece", 110.0), ShownAmount(1.0, "piece", 55.0))
                .copy(borrowed = Borrowed(ShownAmount(1.0, "piece", 55.0), "Ewa"))
            val his = breakfast(language).map { if (it.ingredientId == "eggs") eggs else it }
            EatenPantryDialog(window(PantryQuestion.TAKE, pantry("Paweł", offer = his), pantry("Ewa", offer = breakfast(language).take(2))), onConfirm = {}, onRemoveOnly = {}, onDismiss = {})
        }

        eye.check("pantry-eaten-borrowed") { language, lines ->
            val (eggs, lent) =
                if (language == "en") "eaten 2 pieces (110 g) · in the pantry 1 piece (55 g) · you take 2 pieces (110 g)" to "of which 1 piece (55 g) from Ewa's pantry"
                else "zjedzone 2 sztuki (110 g) · w spiżarni 1 sztuka (55 g) · zdejmiesz 2 sztuki (110 g)" to "w tym 1 sztuka (55 g) ze spiżarni: Ewa"
            assertTrue(lines.toString(), eggs in lines)
            assertTrue(lines.toString(), lent in lines)
        }
    }

    @Test
    fun aMarkTakenBackOffersToGiveBackWhatItTook() {
        eye.show { language ->
            EatenPantryDialog(window(PantryQuestion.GIVE_BACK, pantry("Paweł", taken = breakfast(language))), onConfirm = {}, onRemoveOnly = {}, onDismiss = {})
        }

        eye.check("pantry-eaten-give-back") { language, lines ->
            val (note, milk) =
                if (language == "en") "This meal took what is below off the pantry. Unmarking it gives nothing back by itself." to "Milk: 150 ml"
                else "Ten posiłek zdjął ze spiżarni to, co poniżej. Samo odznaczenie niczego nie oddaje." to "Mleko: 150 ml"
            assertTrue(lines.toString(), note in lines)
            assertTrue(lines.toString(), milk in lines)
        }
    }

    @Test
    fun anEntryThatTookFoodAsksBeforeItGoesWhetherToGiveItBack() {
        eye.show { language ->
            EatenPantryDialog(
                // A window of its own takes the phone's configuration, not the language the look draws in: a name with a
                // word of one letter would be held only on a phone in Polish. HeldLettersTest sees the window write
                // through the app's Text, which holds it there.
                window(PantryQuestion.REMOVE, pantry("Paweł", taken = shakshuka(language)), of = EatenOf.Entry("entry")),
                onConfirm = {},
                onRemoveOnly = {},
                onDismiss = {},
            )
        }

        eye.check("pantry-eaten-remove") { language, lines ->
            assertTrue(lines.toString(), (if (language == "en") "Tomato passata: 400 g" else "Passata pomidorowa: 400 g") in lines)
            val buttons = if (language == "en") listOf("Give back and remove", "Remove without giving back", "Cancel") else listOf("Oddaj i usuń", "Usuń bez oddawania", "Anuluj")
            for (button in buttons) compose.onNode(hasText(shown(button)) and hasClickAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
    }

    @Test
    fun aLongListScrollsInsideTheWindowItsTitleAndButtonsInSight() {
        eye.show { language ->
            val many = (1..14).map { at -> food("food-$at", (if (language == "en") "Ingredient " else "Składnik ") + at, ShownAmount(40.0 + at, "g"), ShownAmount(500.0, "g")) }
            EatenPantryDialog(window(PantryQuestion.TAKE, pantry("Paweł", offer = many)), onConfirm = {}, onRemoveOnly = {}, onDismiss = {})
        }

        eye.check("pantry-eaten-long") { language, _ ->
            val (title, take) = if (language == "en") "Take what was eaten off the pantry" to "Take off the pantry" else "Zdejmij ze spiżarni to, co zjedzono" to "Zdejmij ze spiżarni"
            compose.onNode(hasText(shown(title)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText(shown(take)) and hasClickAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
    }
}
