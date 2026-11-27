// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.ShownAmount
import net.whiteravens.dietapp.ui.common.PantryQuestion
import net.whiteravens.dietapp.ui.common.PantryWindow
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.errorMessage

/**
 * The window that takes what a meal or an entry ate off the pantry ("Zdejmij ze spiżarni to, co zjedzono"), or gives
 * back what it took ("Oddaj do spiżarni"): food by food, in the amounts the API shows, and nothing until it is
 * confirmed. A meal eaten by both people of a joint plan is two portions, each under its person's name. Before an
 * entry that took food is removed, it offers to give that back first. The buttons stand one under another.
 */
@Composable
fun EatenPantryDialog(window: PantryWindow, onConfirm: () -> Unit, onRemoveOnly: () -> Unit, onDismiss: () -> Unit) {
    val question = window.question
    val both = window.portions.size > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (question) {
                        PantryQuestion.TAKE -> R.string.eaten_pantry_take_title
                        PantryQuestion.GIVE_BACK -> R.string.eaten_pantry_give_back_title
                        PantryQuestion.REMOVE -> R.string.eaten_pantry_remove_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        when (question) {
                            PantryQuestion.TAKE -> R.string.eaten_pantry_take_note
                            PantryQuestion.GIVE_BACK -> R.string.eaten_pantry_give_back_note
                            PantryQuestion.REMOVE -> R.string.eaten_pantry_remove_note
                        },
                    ),
                )
                if (both) Text(stringResource(R.string.eaten_pantry_by_person))
                // Scrolls on its own where the window would not fit the screen: the title and the buttons stay in sight.
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (portion in window.portions) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (both) {
                                Text(
                                    portion.pantry.profileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }
                            for (food in portion.foods(question)) {
                                if (question == PantryQuestion.TAKE) {
                                    Column {
                                        Text(
                                            food.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            stringResource(R.string.eaten_pantry_line, shown(food.eaten), shown(food.held), shown(food.taken)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.eaten_pantry_taken_line, food.name, shown(food.taken)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                window.error?.let {
                    Text(errorMessage(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onConfirm, enabled = !window.sending) {
                    Text(
                        stringResource(
                            when (question) {
                                PantryQuestion.TAKE -> R.string.eaten_pantry_take
                                PantryQuestion.GIVE_BACK -> R.string.eaten_pantry_give_back
                                PantryQuestion.REMOVE -> R.string.eaten_pantry_give_back_and_remove
                            },
                        ),
                    )
                }
                if (question == PantryQuestion.REMOVE) {
                    TextButton(onClick = onRemoveOnly, enabled = !window.sending) {
                        Text(stringResource(R.string.eaten_pantry_remove_only))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(
                            when (question) {
                                PantryQuestion.TAKE -> R.string.eaten_pantry_not_now
                                PantryQuestion.GIVE_BACK -> R.string.eaten_pantry_keep
                                PantryQuestion.REMOVE -> R.string.common_cancel
                            },
                        ),
                    )
                }
            }
        },
    )
}

/** An amount as the API shows it, with what pieces weigh beside it, kept on one line: a line breaks only after a dot. */
@Composable
private fun shown(amount: ShownAmount) = held(amountLabel(amount.quantity, amount.unit, amount.grams))
