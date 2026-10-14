// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
        title = { Text(stringResource(R.string.auth_check_title)) },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                factory = { context ->
                    WebView(context).apply { showCheck(siteKey, origin(serverUrl), { token(it) }, { failed() }) }
                },
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** `https://diet.example.org/api/` → `https://diet.example.org/`. */
private fun origin(serverUrl: String): String {
    val url = serverUrl.toHttpUrlOrNull() ?: return "about:blank"
    return url.newBuilder().encodedPath("/").query(null).build().toString()
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun WebView.showCheck(siteKey: String, origin: String, onToken: (String) -> Unit, onFailed: () -> Unit) {
    val main = Handler(Looper.getMainLooper())
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // The check stays on its page: a link it might show is not followed.
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
    }
    addJavascriptInterface(
        object {
            @JavascriptInterface
            fun solved(token: String) {
                main.post { onToken(token) }
            }

            @JavascriptInterface
            fun failed() {
                main.post { onFailed() }
            }
        },
        "DietApp",
    )
    val key = JsonPrimitive(siteKey).toString()
    val page = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <script>
          function shown() {
            turnstile.render('#check', {
              sitekey: $key,
              callback: function (token) { DietApp.solved(token); },
              'error-callback': function () { DietApp.failed(); }
            });
          }
        </script>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=shown" async defer></script>
        </head><body style="margin:0"><div id="check"></div></body></html>
    """.trimIndent()
    loadDataWithBaseURL(origin, page, "text/html", "utf-8", null)
}
