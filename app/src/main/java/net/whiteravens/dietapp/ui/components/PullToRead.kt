// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import net.whiteravens.dietapp.R

/**
 * What a screen shows, read anew when it is pulled down from its top, the indicator turning while
 * [pulled]. The content has to scroll, even when it is short, for the pull to reach this box. A
 * screen reader cannot pull, so it is offered the same as an action of its own.
 */
@Composable
fun PullToRead(
    pulled: Boolean,
    onPull: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val action = stringResource(R.string.pull_to_read)
    val pull by rememberUpdatedState(onPull)
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = { pull() },
        modifier = modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(action) {
                        pull()
                        true
                    },
                )
            },
        content = content,
    )
}
