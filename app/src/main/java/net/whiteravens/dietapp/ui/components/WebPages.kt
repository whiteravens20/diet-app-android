// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.signedInWebPage
import net.whiteravens.dietapp.data.config.webPage

/** The address of the instance's web application, which the signed-in screens open for what the app leaves to it; null before a server is chosen. */
val LocalWebApp = staticCompositionLocalOf<String?> { null }

/** Asks for a code that signs the browser in to the account; null when there is none to be had now. It blocks: call it off the main thread. */
val LocalBrowserCode = staticCompositionLocalOf<() -> String?> { { null } }

/** How long a page waits for its code before it opens without one, to be signed in to there. */
private const val CODE_WAIT_MILLIS = 5_000L

/**
 * Opens pages of the instance's web application in the browser, signed in to the account where a
 * code can be had for it: the browser's page then asks its reader whose account it is, and signs
 * in on their word.
 */
class WebPages internal constructor(
    private val webApp: String?,
    private val scope: CoroutineScope,
    private val browser: () -> Browser,
    private val code: () -> String?,
    private val show: (address: String, browser: String?, plain: String) -> Unit,
) {
    /** Opens [path] of the web application with [query]; nothing while no server is chosen. */
    fun open(path: String, vararg query: Pair<String, String>) {
        val app = webApp ?: return
        scope.launch {
            val plain = webPage(app, path, *query)
            val where = browser()
            // A code only for a browser the address cannot be taken from on its way.
            val given = if (where.safe) withTimeoutOrNull(CODE_WAIT_MILLIS) { scope.async(Dispatchers.IO) { code() }.await() } else null
            if (given == null) show(plain, null, plain) else show(signedInWebPage(app, given, path, *query), where.pkg, plain)
        }
    }

    /** Opens a page outside the instance, at [address], in the browser: no code goes with it. */
    fun openOutside(address: String) = show(address, null, address)
}

/**
 * Where an address with a code goes. [pkg] is the phone's default browser where it has one: the
 * address is given to it alone. Without one, Android 12 and newer hand an address of a site only
 * to browsers and to an app the site itself names; Android 10 and 11 also offer it to any app that
 * claims the site's addresses, which is not [safe].
 */
class Browser(val pkg: String?, val safe: Boolean)

internal fun browserOf(context: Context): Browser {
    val probe = Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val pm = context.packageManager
    val browsers = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }.toSet()
    // Without a default, what resolves is the system's chooser, which is no browser.
    val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it in browsers }
    return Browser(default, safe = default != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}

/**
 * Pages of the instance's web application, opened in the browser. Where no browser on the phone
 * opens one, a window says so and shows its address, without a code, to be copied or typed
 * elsewhere.
 */
@Composable
fun rememberWebPages(): WebPages {
    val context = LocalContext.current
    val webApp = LocalWebApp.current
    val code = LocalBrowserCode.current
    val scope = rememberCoroutineScope()
    var unopened by rememberSaveable { mutableStateOf<String?>(null) }
    unopened?.let { address -> NoBrowserDialog(address, onDismiss = { unopened = null }) }
    return remember(context, webApp, code, scope) {
        WebPages(webApp, scope, { browserOf(context) }, code) { address, browser, plain ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(browser))
            } catch (e: ActivityNotFoundException) {
                unopened = plain
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
