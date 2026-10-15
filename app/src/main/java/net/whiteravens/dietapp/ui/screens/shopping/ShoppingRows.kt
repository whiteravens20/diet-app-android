// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingItem

/** A number somebody typed into a field of a row: nothing for an empty field, never under nothing; a comma is a decimal sign too. */
fun typed(field: String): Double? =
    if (field.isBlank()) null else (field.trim().replace(',', '.').toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)

/** What was bought for a row beyond what is at home, as the API has it. */
fun boughtOf(item: ShoppingItem): Double = (item.have - item.atHome).coerceAtLeast(0.0)

/**
 * What a row sends for its two fields, [home] and [bought] as they are typed: what is at home where
 * it changed, sent alone, so that the server leaves what was bought beyond it as it was; and where
 * what was bought changed, everything the user then has for the row, the part at home as the server
 * counts it (no more than the need) and what was bought.
 */
fun rowChange(item: ShoppingItem, home: Double, bought: Double): RowChange {
    val atHome = home.takeIf { it != item.atHome }
    if (bought == boughtOf(item)) return RowChange(atHome = atHome)
    return RowChange(atHome = atHome, have = minOf(home, item.totalQuantity) + bought)
}

/**
 * The packs of a row taken for bought: everything the user has for it is then what was at home and
 * what the packs give, which is the need at least, so the row is done.
 */
fun packsBought(item: ShoppingItem): RowChange? = item.packs?.let { RowChange(have = item.atHome + it.quantity, checked = true) }
