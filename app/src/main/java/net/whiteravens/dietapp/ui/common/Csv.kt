// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import java.math.BigDecimal

/**
 * How a language's spreadsheets read a file of separated values: what stands between two cells,
 * and the sign before the decimals of a number. Where the decimal sign is a comma, cells are parted
 * by a semicolon.
 */
data class CsvDialect(val separator: Char, val decimal: Char) {
    companion object {
        private val BY_LANGUAGE = mapOf("en" to CsvDialect(',', '.'), "pl" to CsvDialect(';', ','))

        /** The dialect of a language the app is shown in: English's for a language it has no texts in. */
        fun of(language: String): CsvDialect = BY_LANGUAGE[language] ?: BY_LANGUAGE.getValue("en")
    }
}

/** A text that a spreadsheet would take for a formula: it begins with a sign that starts one. */
private val STARTS_A_FORMULA = Regex("^[=+\\-@\\t\\r]")

/** A cell as the file writes it: a cell holds a text, a number, or nothing (null). */
private fun written(cell: Any?, dialect: CsvDialect): String = when (cell) {
    null -> ""
    // A number is written without grouping, to the hundredth, with the decimal sign of the dialect.
    is Number -> cell.toDouble().let { value ->
        if (value.isFinite()) BigDecimal.valueOf(Math.round(value * 100) / 100.0).stripTrailingZeros().toPlainString().replace('.', dialect.decimal) else ""
    }
    else -> {
        // A name somebody typed is text, whatever it begins with: with an apostrophe before it a spreadsheet does not work it out.
        val text = cell.toString().let { if (STARTS_A_FORMULA.containsMatchIn(it)) "'$it" else it }
        val quoted = text.any { it == '"' || it == '\r' || it == '\n' || it == dialect.separator } || text != text.trim()
        if (quoted) "\"${text.replace("\"", "\"\"")}\"" else text
    }
}

/**
 * A table as a file of separated values that a spreadsheet opens: one line a row, the lines ended
 * as such files end them, the whole preceded by the mark that says it is UTF-8 (without it a
 * spreadsheet reads "ż" as two signs).
 */
fun csvOf(rows: List<List<Any?>>, dialect: CsvDialect): String =
    rows.joinToString(separator = "\r\n", prefix = "\uFEFF", postfix = "\r\n") { row ->
        row.joinToString(dialect.separator.toString()) { written(it, dialect) }
    }
