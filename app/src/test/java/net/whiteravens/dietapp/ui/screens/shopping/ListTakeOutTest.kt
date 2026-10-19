// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import net.whiteravens.dietapp.domain.RowPacks
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Test

/** A shopping list taken out of the app as a text to send. */
class ListTakeOutTest {
    private fun item(id: String, name: String, total: Double, atHome: Double = 0.0, have: Double? = null, packs: RowPacks? = null, checked: Boolean = false) =
        ShoppingItem(id, name, total, total - atHome, "g", "g", atHome, have, null, total * 2, packs, checked)

    /** Dairy with feta in two packs still to buy and butter ticked; the bakery all ticked. */
    private val list = ShoppingList(
        id = "list",
        planId = "plan",
        fromDate = "2026-10-13",
        toDate = "2026-10-19",
        groups = listOf(
            ShoppingGroup("dairy", listOf(item("feta", "Feta", 400.0, atHome = 50.0, packs = RowPacks(2, 200.0, "g", 400.0)), item("butter", "Butter", 20.5, have = 20.5, checked = true)), 841.0),
            ShoppingGroup("bakery", listOf(item("bread", "Bread", 300.0, have = 300.0, checked = true)), 600.0),
        ),
        totalEstimatedCalories = 1441.0,
        stale = false,
        createdAt = "2026-10-13T08:00:00.000Z",
        syncedAt = null,
    )

    private val words = ListWords(
        title = "Shopping list, 13–19 Oct 2026",
        nothing = "Everything on this list is ticked off.",
        aisles = mapOf("dairy" to "Dairy", "bakery" to "Bakery"),
        toBuy = mapOf("feta" to "350 g", "butter" to "0 g", "bread" to "0 g"),
        packs = mapOf("feta" to "2 × 200 g"),
    )

    @Test
    fun `the text to send is what is still to be bought under its aisles, with the packs it takes`() {
        assertEquals("Shopping list, 13–19 Oct 2026\n\nDairy\n- Feta: 350 g (2 × 200 g)", listText(list, words))
    }

    @Test
    fun `a list ticked off says so in its text`() {
        val done = list.copy(groups = list.groups.map { group -> group.copy(items = group.items.map { it.copy(checked = true) }) })

        assertEquals("Shopping list, 13–19 Oct 2026\n\nEverything on this list is ticked off.", listText(done, words))
    }
}
