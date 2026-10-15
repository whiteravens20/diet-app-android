// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import net.whiteravens.dietapp.domain.ShoppingList

/** The words a shopping list is written out with, in the reader's language, with what each row comes to already in them. */
data class ListWords(
    /** What the list is: `Shopping list, 13–19 Oct 2026`. */
    val title: String,
    /** What the text says where everything is ticked off. */
    val nothing: String,
    /** What each aisle is called, by its code. */
    val aisles: Map<String, String>,
    /** What is still to be bought of each row, by the row's id, as the language writes an amount. */
    val toBuy: Map<String, String>,
    /** The packs of each row whose packs are what is still to be bought, by the row's id: `2 × 250 g`. */
    val packs: Map<String, String>,
    /** The heads of a table's columns: aisle, entry, needed, unit, at home, have in all, still to buy, packs, ticked, kcal. */
    val head: List<String>,
    /** What each unit is called beside a number, by its code. */
    val units: Map<String, String>,
    val yes: String,
    val no: String,
) {
    fun aisle(code: String): String = aisles[code] ?: code
}

/** The list as a text somebody can send: what is still to be bought, under its aisles. What is ticked is done with. */
fun listText(list: ShoppingList, words: ListWords): String {
    val open = list.groups.map { group -> group to group.items.filter { !it.checked } }.filter { (_, left) -> left.isNotEmpty() }
    val lines = mutableListOf(words.title)
    if (open.isEmpty()) lines += listOf("", words.nothing)
    for ((group, left) in open) {
        lines += ""
        lines += words.aisle(group.category)
        // What is sold in packs says how many, for whoever stands at the shelf.
        left.forEach { item -> lines += "- ${item.name}: ${words.toBuy[item.id].orEmpty()}${words.packs[item.id]?.let { " ($it)" }.orEmpty()}" }
    }
    return lines.joinToString("\n")
}

/** The whole list as a table for a spreadsheet: every row, ticked or not, with its numbers as numbers. */
fun listTable(list: ShoppingList, words: ListWords): List<List<Any?>> =
    listOf(words.head) + list.groups.flatMap { group ->
        group.items.map { item ->
            listOf(
                words.aisle(group.category),
                item.name,
                item.totalQuantity,
                words.units[item.displayUnit] ?: item.displayUnit,
                item.atHome,
                item.have,
                item.stillToBuy,
                words.packs[item.id].orEmpty(),
                if (item.checked) words.yes else words.no,
                item.estimatedCalories,
            )
        }
    }

/** The name a list taken out of the app is kept under: its first and its last day. */
fun exportName(list: ShoppingList): String = "shopping-list-${list.fromDate}_${list.toDate}"
