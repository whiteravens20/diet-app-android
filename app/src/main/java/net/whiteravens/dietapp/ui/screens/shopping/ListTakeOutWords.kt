// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.RowPacks
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.PagePrinter
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

/**
 * Hands [list] to the platform's print service, which prints it or keeps it as a PDF, as the list is
 * when this is shown; [plan] says what plan it is of. [onDone] is told once the print service has it.
 */
@Composable
fun ListPrinter(list: ShoppingList, plan: String, language: String, onDone: () -> Unit) {
    PagePrinter(exportName(list), listSheet(list, plan).html(language), onDone)
}

/** A list as a sheet of paper, in the words and the formats of the app's language. */
@Composable
private fun listSheet(list: ShoppingList, plan: String): ListSheet {
    val have = stringResource(R.string.shopping_have_short)
    return ListSheet(
        title = stringResource(R.string.shopping_copy_title, formatDates(list.fromDate, list.toDate)),
        plan = plan,
        bought = stringResource(R.string.shopping_bought),
        aisles = list.groups.map { group ->
            SheetAisle(
                name = categoryLabel(group.category),
                rows = group.items.map { item ->
                    val amount = buildString {
                        append(amountLabel(item.toBuyQuantity, item.displayUnit))
                        if (item.atHome > 0) append(" ").append(stringResource(R.string.shopping_of_total, amountLabel(item.totalQuantity, item.displayUnit)))
                        if (item.packsStand) append(" · ").append(packsText(item.packs!!))
                    }
                    // A row the pantry meets has nothing to be bought: it is shown done, so that it is seen what the meals needed.
                    SheetRow(item.name, have.takeIf { item.atHome > 0 }, amount, done = item.checked || (item.toBuyQuantity == 0.0 && item.atHome > 0))
                },
            )
        },
    )
}
