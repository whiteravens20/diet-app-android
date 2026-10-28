// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Refused
import net.whiteravens.dietapp.ui.common.errorMessage
import javax.inject.Inject

/** How many changes made on the phone wait to be sent, for the bar a screen shows offline. */
val LocalWaitingChanges = compositionLocalOf { 0 }

/** The changes made on the phone as the whole application shows them: how many wait, and which the server refused. */
@HiltViewModel
class ChangesViewModel @Inject constructor(private val queue: ChangeQueue) : ViewModel() {
    val waiting: StateFlow<Int> = queue.waiting.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), 0)

    val refused: StateFlow<List<Refused>> = queue.refused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), emptyList())

    /** Forgets a refused change: its notice has been read. */
    fun dismiss(id: Long) {
        viewModelScope.launch { queue.dismiss(id) }
    }

    private companion object {
        /** How long the counts are kept read after the last screen stops showing them: a turn of the phone. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** Says, on what a change was made to, that the change waits to be sent. */
@Composable
fun WaitingMark(modifier: Modifier = Modifier) {
    LabelChip(stringResource(R.string.change_waiting), modifier = modifier)
}

/**
 * The changes the server refused, each with what it was and why, until it is seen: such a change
 * was not saved, and is not sent again.
 */
@Composable
fun RefusedChangesNotice(refused: List<Refused>, onSeen: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (refused.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            refused.forEach { change ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.change_refused, changeKind(change.change), refusalOf(change)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSeen(change.id) }) { Text(stringResource(R.string.change_refused_seen)) }
                }
            }
        }
    }
}

/** What a change was made to, as the notice of its refusal names it. */
@Composable
private fun changeKind(change: QueuedChange?): String = when (change) {
    is QueuedChange.ShoppingRow -> stringResource(R.string.change_kind_shopping_row)
    is QueuedChange.Eaten -> stringResource(R.string.change_kind_eaten)
    is QueuedChange.WeightLogged -> stringResource(R.string.change_kind_weight_logged)
    is QueuedChange.WeightDeleted -> stringResource(R.string.change_kind_weight_deleted)
    is QueuedChange.PantryAdded -> stringResource(R.string.change_kind_pantry_added)
    is QueuedChange.PantryChanged -> stringResource(R.string.change_kind_pantry_changed)
    is QueuedChange.PantryRemoved -> stringResource(R.string.change_kind_pantry_removed)
    is QueuedChange.OwnMealAdded -> stringResource(R.string.change_kind_own_meal_added)
    is QueuedChange.MealRemoved -> stringResource(R.string.change_kind_meal_removed)
    null -> stringResource(R.string.change_kind_unreadable)
}

/** Why the change was refused: the server's words for its code, or the app's for a change it could not send. */
@Composable
private fun refusalOf(change: Refused): String = when (change.code) {
    ChangeQueue.UNREADABLE -> stringResource(R.string.change_unreadable)
    ChangeQueue.UNSENDABLE -> stringResource(R.string.change_unsendable)
    else -> errorMessage(ApiException(statusCode = 400, code = change.code, message = change.message.orEmpty()))
}
