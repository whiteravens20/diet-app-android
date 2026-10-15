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
import net.whiteravens.dietapp.ui.common.unitName

/** What a row comes to in packs, as a shelf label says it: `2 × 250 g`. */
@Composable
fun packsText(packs: RowPacks): String = "${packs.count} × ${amountLabel(packs.size, packs.sizeUnit)}"

/** The words [list] is written out with, in the language the app is shown in. */
@Composable
fun listWords(list: ShoppingList): ListWords {
    val items = list.groups.flatMap { it.items }
    return ListWords(
        title = stringResource(R.string.shopping_copy_title, formatDates(list.fromDate, list.toDate)),
        nothing = stringResource(R.string.shopping_copy_nothing),
        aisles = list.groups.associate { it.category to categoryLabel(it.category) },
        toBuy = items.associate { it.id to amountLabel(it.stillToBuy, it.displayUnit) },
        packs = items.filter { it.packsStand }.associate { item -> item.id to packsText(item.packs!!) },
        head = listOf(
            stringResource(R.string.shopping_csv_aisle),
            stringResource(R.string.shopping_csv_entry),
            stringResource(R.string.shopping_csv_needed),
            stringResource(R.string.shopping_csv_unit),
            stringResource(R.string.shopping_csv_at_home),
            stringResource(R.string.shopping_csv_have),
            stringResource(R.string.shopping_csv_to_buy),
            stringResource(R.string.shopping_csv_packs),
            stringResource(R.string.shopping_csv_ticked),
            stringResource(R.string.shopping_csv_energy),
        ),
        units = items.map { it.displayUnit }.distinct().associateWith { unitName(it) },
        yes = stringResource(R.string.common_yes),
        no = stringResource(R.string.common_no),
    )
}

/** Hands [text] to the platform's window that shares a text with another app, or copies it, under [title]. */
fun shareText(context: Context, title: String, text: String, chooser: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooser))
}
