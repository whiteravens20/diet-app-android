// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.RowPacks
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.domain.HouseholdList
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.ui.components.DOT
import net.whiteravens.dietapp.ui.components.held

/** What a row comes to in packs, as a shelf label says it: `2 × 250 g`. */
@Composable
fun packsText(packs: RowPacks): String = "${packs.count} × ${amountLabel(packs.size, packs.sizeUnit)}"

/** The words [list] is written out with, in the language the app is shown in. */
@Composable
fun listWords(list: ShoppingList): ListWords = wordsOf(list.fromDate, list.toDate, list.groups)

/** The words a household [list] is written out with: a plan's list's, and what each profile needs of each row. */
@Composable
fun householdWords(list: HouseholdList): ListWords = wordsOf(list.fromDate, list.toDate, list.groups)

@Composable
private fun wordsOf(fromDate: String, toDate: String, groups: List<ShoppingGroup>): ListWords {
    val items = groups.flatMap { it.items }
    return ListWords(
        title = stringResource(R.string.shopping_copy_title, formatDates(fromDate, toDate)),
        nothing = stringResource(R.string.shopping_copy_nothing),
        aisles = groups.associate { it.category to categoryLabel(it.category) },
        toBuy = items.associate { it.id to amountLabel(it.stillToBuy, it.displayUnit, it.gramsOf(it.stillToBuy)) },
        packs = items.filter { it.packsStand }.associate { item -> item.id to packsText(item.packs!!) },
        shares = items.filter { it.shares.isNotEmpty() }.associate { item -> item.id to sharesLine(item) },
    )
}

/** What each profile's plan needs of a row of a household list, in one line, as the web says it: `Adam 345 g · Ewa 80 g`. */
@Composable
fun sharesLine(item: ShoppingItem): String =
    item.shares.map { share -> held("${share.profile} ${amountLabel(share.totalQuantity, item.displayUnit, item.gramsOf(share.totalQuantity))}") }.joinToString(DOT)

/** Hands [text] to the platform's window that shares a text with another app, or copies it, under [title]. */
fun shareText(context: Context, title: String, text: String, chooser: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooser))
}
