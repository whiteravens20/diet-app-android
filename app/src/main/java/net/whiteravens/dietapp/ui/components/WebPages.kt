// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.webPage

/** The address of the instance's web application, which the signed-in screens open for what the app leaves to it; null before a server is chosen. */
val LocalWebApp = staticCompositionLocalOf<String?> { null }

/** Opens pages of the instance's web application in the browser. */
class WebPages internal constructor(private val webApp: String?, private val show: (String) -> Unit) {
    /** Opens [path] of the web application with [query]; nothing while no server is chosen. */
    fun open(path: String, vararg query: Pair<String, String>) {
        webApp?.let { show(webPage(it, path, *query)) }
    }
}

/**
 * Pages of the instance's web application, opened in the browser. Where no browser on the phone
 * opens one, a window says so and shows its address, to be copied or typed elsewhere.
 */
@Composable
fun rememberWebPages(): WebPages {
    val context = LocalContext.current
    val webApp = LocalWebApp.current
    var unopened by rememberSaveable { mutableStateOf<String?>(null) }
    unopened?.let { address -> NoBrowserDialog(address, onDismiss = { unopened = null }) }
    return remember(context, webApp) {
        WebPages(webApp) { address ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()))
            } catch (e: ActivityNotFoundException) {
                unopened = address
            }
        }
    }
}

@Composable
private fun NoBrowserDialog(address: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_no_browser_title)) },
        text = {
            Column {
                Text(stringResource(R.string.web_no_browser_body), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer(Modifier.padding(top = 12.dp)) {
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
