// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.ui.common.escaped

/**
 * A plan as a sheet of paper, every text of it in the reader's language already: what it is, what
 * a day of it holds on average, and its days one under another.
 */
data class PlanSheet(
    val title: String,
    val average: String,
    /** The heads of a day's columns: meal, dish, servings, kilocalories, protein, fat, carbohydrate. */
    val head: List<String>,
    val days: List<SheetDay>,
)

/** A day of the sheet: what it is called, what it holds against its target or that it is skipped, and its meals, none where it is skipped. */
data class SheetDay(val name: String, val note: String, val meals: List<SheetMeal>)

/** A meal of the sheet: when in the day it is, its dish and what goes into it, and its servings, kilocalories, protein, fat and carbohydrate. */
data class SheetMeal(val meal: String, val dish: String, val lines: String, val figures: List<String>)

/**
 * The sheet as a page for the platform's print service to lay out on paper: its days one under
 * another, a day kept on one page where it fits, and columns as wide on every day and no wider
 * than the paper. Every text goes in as text, never as markup: a meal's name is what the user typed.
 */
fun PlanSheet.html(language: String): String = buildString {
    append("<!doctype html><html lang=\"").append(escaped(language)).append("\"><head><meta charset=\"utf-8\">")
    append("<title>").append(escaped(title)).append("</title><style>").append(SHEET_STYLE).append("</style></head><body>")
    append("<h1>").append(escaped(title)).append("</h1>")
    append("<p class=\"average\">").append(escaped(average)).append("</p>")
    for (day in days) {
        append("<section><h2><span>").append(escaped(day.name)).append("</span><small>").append(escaped(day.note)).append("</small></h2>")
        if (day.meals.isNotEmpty()) {
            append("<table><colgroup>")
            COLUMN_WIDTHS.forEach { width -> append(if (width == null) "<col>" else "<col style=\"width:$width%\">") }
            append("</colgroup><thead><tr>")
            head.forEachIndexed { index, name -> append("<th scope=\"col\"").append(if (index >= 2) " class=\"n\"" else "").append(">").append(escaped(name)).append("</th>") }
            append("</tr></thead><tbody>")
            for (meal in day.meals) {
                append("<tr><td>").append(escaped(meal.meal)).append("</td>")
                append("<td><span class=\"dish\">").append(escaped(meal.dish)).append("</span>")
                if (meal.lines.isNotEmpty()) append("<span class=\"lines\">").append(escaped(meal.lines)).append("</span>")
                append("</td>")
                meal.figures.forEach { figure -> append("<td class=\"n\">").append(escaped(figure)).append("</td>") }
                append("</tr>")
            }
            append("</tbody></table>")
        }
        append("</section>")
    }
    append("</body></html>")
}

/** The share of the width each column takes; the dish has what is left. */
private val COLUMN_WIDTHS = listOf(14, null, 10, 8, 10, 9, 14)

private const val SHEET_STYLE =
    "@page{margin:14mm 12mm}" +
        "body{margin:0;font-family:sans-serif;font-size:10pt;color:#000;background:#fff}" +
        "h1{margin:0;font-size:15pt;font-weight:600}" +
        ".average{margin:4pt 0 0;font-size:9pt}" +
        "section{margin-top:14pt;break-inside:avoid}" +
        "h2{display:flex;justify-content:space-between;align-items:baseline;gap:12pt;margin:0;padding-bottom:3pt;border-bottom:1px solid #000;font-size:11.5pt;font-weight:600}" +
        "h2 small{font-size:9pt;font-weight:400}" +
        "table{width:100%;margin-top:3pt;table-layout:fixed;border-collapse:collapse;font-size:9pt;overflow-wrap:anywhere}" +
        "th{padding:3pt 6pt 3pt 0;text-align:left;font-weight:600}" +
        "td{padding:3pt 6pt 3pt 0;vertical-align:top;border-top:1px solid #ccc}" +
        "th:last-child,td:last-child{padding-right:0}" +
        ".n{text-align:right;font-variant-numeric:tabular-nums}" +
        ".dish{font-weight:500}" +
        ".lines{display:block;font-size:8pt;line-height:1.3}"
