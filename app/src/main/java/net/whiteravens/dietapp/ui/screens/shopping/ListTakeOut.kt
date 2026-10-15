// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.escaped

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

/**
 * A shopping list as a sheet of paper to take to the shop, every text of it in the reader's
 * language already: its aisles in two columns, each row with a box to tick, what is to be bought
 * of it and a line to write in what was.
 */
data class ListSheet(val title: String, val plan: String, val bought: String, val aisles: List<SheetAisle>)

/** An aisle of the sheet and its rows. */
data class SheetAisle(val name: String, val rows: List<SheetRow>)

/**
 * A row of the sheet: its name and, where something of it is at home, a word that says so ([have]);
 * what is to be bought of it; and whether it is done, ticked or met by what is at home.
 */
data class SheetRow(val name: String, val have: String?, val amount: String, val done: Boolean)

/** The sheet as a page for the platform's print service. Every text goes in as text, never as markup. */
fun ListSheet.html(language: String): String = buildString {
    append("<!doctype html><html lang=\"").append(escaped(language)).append("\"><head><meta charset=\"utf-8\">")
    append("<title>").append(escaped(title)).append("</title><style>").append(SHEET_STYLE).append("</style></head><body>")
    append("<header><h1>").append(escaped(title)).append("</h1><p>").append(escaped(plan)).append("</p></header>")
    append("<main>")
    for (aisle in aisles) {
        append("<section><h2>").append(escaped(aisle.name)).append("</h2>")
        for (row in aisle.rows) {
            append("<div class=\"row\"><div class=\"top\"><span class=\"box").append(if (row.done) " done" else "").append("\"></span>")
            append("<span class=\"name\">").append(escaped(row.name))
            row.have?.let { append(" <small>(").append(escaped(it)).append(")</small>") }
            append("</span><span class=\"amount\">").append(escaped(row.amount)).append("</span></div>")
            append("<div class=\"write\">").append(escaped(bought)).append(":<span class=\"line\"></span></div></div>")
        }
        append("</section>")
    }
    append("</main></body></html>")
}

private const val SHEET_STYLE =
    "@page{margin:12mm}" +
        "body{margin:0;font-family:sans-serif;font-size:9.5pt;color:#000;background:#fff}" +
        "header{display:flex;justify-content:space-between;align-items:flex-end;gap:12pt;padding-bottom:4pt;margin-bottom:8pt;border-bottom:1px solid #000}" +
        "h1{margin:0;font-size:14pt;font-weight:600}" +
        "header p{margin:0;font-size:8.5pt}" +
        "main{columns:2;column-gap:16pt}" +
        "section{break-inside:avoid;margin-bottom:10pt}" +
        "h2{margin:0 0 3pt;font-size:8.5pt;font-weight:600;letter-spacing:.06em;text-transform:uppercase}" +
        ".row{break-inside:avoid;padding:3pt 0;border-bottom:1px solid #ddd}" +
        ".top{display:flex;align-items:baseline;gap:6pt}" +
        ".box{flex:none;width:8pt;height:8pt;border:1px solid #000;align-self:center}" +
        ".box.done{background:#000}" +
        ".name{flex:1;overflow-wrap:anywhere}" +
        ".name small{font-size:7.5pt;color:#444}" +
        ".amount{flex:none;text-align:right;font-variant-numeric:tabular-nums}" +
        ".write{display:flex;align-items:baseline;gap:4pt;margin:3pt 0 0 14pt;font-size:7.5pt;color:#444}" +
        ".line{flex:1;border-bottom:1px dashed #888}"
