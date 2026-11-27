// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.domain.CarriedTo
import net.whiteravens.dietapp.domain.EffectList
import net.whiteravens.dietapp.domain.LeftoverOffer
import net.whiteravens.dietapp.domain.LeftoverPlace
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlace
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.PlanEffects
import net.whiteravens.dietapp.domain.Portion
import net.whiteravens.dietapp.domain.Pot
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.testing.EachLanguage
import net.whiteravens.dietapp.ui.screens.pantry.RowItem
import net.whiteravens.dietapp.ui.screens.pantry.StillGoodDialog
import net.whiteravens.dietapp.ui.screens.plans.LeftoverDialog
import net.whiteravens.dietapp.ui.screens.plans.MealCard
import net.whiteravens.dietapp.ui.screens.plans.PlanDetailViewModel
import net.whiteravens.dietapp.ui.screens.plans.RebalanceCard
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
 * counts such stock: in English, then in Polish, each kept for the eye as a phone in that language keeps it.
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
    private fun Card(meal: Meal) = MealCard(
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
}
