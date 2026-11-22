// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.ui.screens.shopping.ShoppingItemRow
import net.whiteravens.dietapp.ui.theme.DietAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A row of a shopping list keeps what its fields hold only for itself, as the server has it: a card of rows scrolled
 * away and back shows the rows it holds now, never what another row in its place held (a new list made while the card
 * was out of sight), nor what the row held before the server changed it.
 */
@RunWith(AndroidJUnit4::class)
class ListRowKeptTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A closed row's line of an amount, "w domu 340 g", as the row writes it: held together. */
    private fun line(@StringRes part: Int, grams: Int): String =
        "${context.getString(part)} ${context.getString(R.string.amount_g, grams.toString())}".replace(' ', ' ')

    private fun row(id: String, atHome: Double, purchased: Double?) = ShoppingItem(
        id = id,
        name = "Chicken breast",
        totalQuantity = 1085.0,
        toBuyQuantity = 745.0,
        unit = "g",
        displayUnit = "g",
        atHome = atHome,
        purchasedQuantity = purchased,
        pantryBestBefore = null,
        estimatedCalories = 1200.0,
        packs = null,
        checked = false,
    )

    /** A card of [rows], as a list shows an aisle, with a long way below it to scroll it out of sight. */
    private fun show(rows: () -> List<ShoppingItem>, list: LazyListState) {
        compose.setContent {
            DietAppTheme {
                LazyColumn(Modifier.height(400.dp), state = list) {
                    item(key = "group-meat") {
                        // As a caller that names no row: what a row keeps has to be its own all the same.
                        Column {
                            rows().forEach { item ->
                                ShoppingItemRow(item, busy = false, readOnly = false, waiting = false, onChange = {})
                            }
                        }
                    }
                    item(key = "below") { Spacer(Modifier.height(3000.dp)) }
                }
            }
        }
    }

    /** The card scrolled out of sight, where the list keeps what it held, [meanwhile] done, and the card scrolled back. */
    private fun scrollAwayAndBack(list: LazyListState, meanwhile: () -> Unit) {
        compose.runOnIdle { runBlocking { list.scrollToItem(1, 2000) } }
        compose.waitForIdle()
        meanwhile()
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { list.scrollToItem(0) } }
        compose.waitForIdle()
    }

    @Test
    fun aRowOfANewListShowsItsOwnAmountsOnceItsCardIsScrolledBack() {
        var rows by mutableStateOf(listOf(row("old", atHome = 0.0, purchased = 900.0)))
        val list = LazyListState()
        show({ rows }, list)
        compose.onNodeWithText(line(R.string.shopping_bought, 900), substring = true).assertExists()

        scrollAwayAndBack(list) { rows = listOf(row("new", atHome = 340.0, purchased = 340.0)) }

        compose.onNodeWithText(line(R.string.shopping_at_home, 340), substring = true).assertExists()
        compose.onNodeWithText(line(R.string.shopping_bought, 900), substring = true).assertDoesNotExist()
    }

    @Test
    fun aRowTheServerChangedWhileItsCardWasAwayShowsWhatTheServerHas() {
        var rows by mutableStateOf(listOf(row("same", atHome = 0.0, purchased = 900.0)))
        val list = LazyListState()
        show({ rows }, list)

        scrollAwayAndBack(list) { rows = listOf(row("same", atHome = 340.0, purchased = 340.0)) }

        compose.onNodeWithText(line(R.string.shopping_at_home, 340), substring = true).assertExists()
        compose.onNodeWithText(line(R.string.shopping_bought, 900), substring = true).assertDoesNotExist()
    }
}
