// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.JsonPrimitive
import net.whiteravens.dietapp.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cloudflare Turnstile, the anti-abuse check the server asks of a sign-up or a sign-in, solved in
 * a WebView as on the web application's pages, with the server's site key. Cloudflare gives no
 * check of its own for an app. The page is given the server's own address ([serverUrl]'s origin),
 * which the site key is issued for, and loads nothing but the check; its answer goes to [onToken].
 */
@Composable
fun AntiAbuseCheckDialog(
    siteKey: String,
    serverUrl: String,
    onToken: (String) -> Unit,
    onFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val token by rememberUpdatedState(onToken)
    val failed by rememberUpdatedState(onFailed)
    AlertDialog(
        onDismissRequest = onDismiss,
        // As wide as the screen but for a margin: the platform's width of a dialog leaves the check
        // less than the 300 dp of Cloudflare's widget on a phone.
        modifier = Modifier.padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.auth_check_title)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size = checkSizeFor(maxWidth)
                key(size) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(size.height),
                        factory = { context ->
                            val view = WebView(context)
                            showCheck(view, checkPage(siteKey, size), origin(serverUrl), { token(it) }, { failed() })
                            view
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The sizes of Cloudflare's widget, a CSS pixel of the check's page being a dp: as wide as its
 * place, 300 at least, and 65 high; or the compact one, 150 by 140, for a narrower place.
 */
internal enum class CheckSize(val turnstile: String, val height: Dp) {
    FLEXIBLE("flexible", 72.dp),
    COMPACT("compact", 148.dp),
}

/** The widget that fits a place [width] wide. */
internal fun checkSizeFor(width: Dp): CheckSize = if (width >= 300.dp) CheckSize.FLEXIBLE else CheckSize.COMPACT

/** The page that runs the check with [siteKey], its widget of [size]. */
internal fun checkPage(siteKey: String, size: CheckSize): String {
    val key = JsonPrimitive(siteKey).toString()
    val widget = JsonPrimitive(size.turnstile).toString()
    return """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <script>
          function shown() {
            turnstile.render('#check', {
              sitekey: $key,
              size: $widget,
              callback: function (token) { location.href = '$ANSWER_SCHEME://solved#' + encodeURIComponent(token); },
              'error-callback': function () { location.href = '$ANSWER_SCHEME://failed'; }
            });
          }
        </script>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=shown" async defer></script>
        </head><body style="margin:0"><div id="check"></div></body></html>
    """.trimIndent()
}

/** `https://diet.example.org/api/` → `https://diet.example.org/`. */
private fun origin(serverUrl: String): String {
    val url = serverUrl.toHttpUrlOrNull() ?: return "about:blank"
    return url.newBuilder().encodedPath("/").query(null).build().toString()
}

/** Sets [view] up for the check and loads the check's [page] into it. */
@SuppressLint("SetJavaScriptEnabled")
private fun showCheck(
    view: WebView,
    page: String,
    origin: String,
    onToken: (String) -> Unit,
    onFailed: () -> Unit,
) {
    // The check runs Cloudflare's script, which reaches no file and no content of the phone.
    val settings = view.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    // Its answer comes back as a navigation to an address of the app's own scheme, which goes
    // nowhere, so no object of the app is open to the page's scripts. Any other navigation, a link
    // the check might show, is not followed.
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val answer = request.url.takeIf { request.isForMainFrame && it.scheme == ANSWER_SCHEME }
            when (answer?.host) {
                "solved" -> answer?.fragment?.takeIf { it.isNotEmpty() }?.let(onToken) ?: onFailed()
                "failed" -> onFailed()
            }
            return true
        }
    }
    view.loadDataWithBaseURL(origin, page, "text/html", "utf-8", null)
}

/** The scheme of the address the check's answer comes back to: it names no place. */
private const val ANSWER_SCHEME = "dietapp-check"
