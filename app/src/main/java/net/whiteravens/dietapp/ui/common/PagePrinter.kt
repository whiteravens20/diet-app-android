// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Hands a page to the platform's print service once this is shown, which prints it or keeps it as
 * a PDF called [name]. The page is laid out by a web view that runs no script and reaches nothing
 * outside the page it is given; [onDone] is told once the print service has it.
 */
@Composable
fun PagePrinter(name: String, html: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        context.activity()?.let { printPage(it, name, html) }
        done()
    }
}

/** A text as markup shows it, every sign that markup reads written as the sign itself. */
fun escaped(text: String): String = buildString(text.length) {
    for (sign in text) {
        when (sign) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(sign)
        }
    }
}

/** Lays [html] out as a page and gives it to the print service under [name], which the PDF it may keep is called by. */
private suspend fun printPage(activity: Activity, name: String, html: String) {
    val page = WebView(activity)
    page.settings.apply {
        javaScriptEnabled = false
        blockNetworkLoads = true
        allowFileAccess = false
        allowContentAccess = false
    }
    suspendCancellableCoroutine { loaded ->
        page.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (loaded.isActive) loaded.resume(Unit)
            }
        }
        page.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    // The print service lays the page out once the job is made: the page is held until then.
    activity.getSystemService(PrintManager::class.java).print(name, page.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
}

/** The activity a screen is shown in: only an activity can start a print job. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
