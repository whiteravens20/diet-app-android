// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.RecipeSource
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.DOT
import net.whiteravens.dietapp.ui.screens.shopping.shareText
import java.io.ByteArrayOutputStream

/** A line of a recipe as it is sent: an ingredient, its amount in words and what the recipe says of it. */
data class SharedLine(val name: String, val amount: String, val note: String?)

/**
 * A recipe as it is sent to somebody who is to cook it, as the web sends it: what it is called, what
 * it takes in the amounts it was opened with, and how it is made. No figures, and nothing of whoever
 * sends it: no profile, target or diet. Its words come translated, so a text and a document say the
 * same.
 */
data class SharedRecipe(
    val title: String,
    /** What the amounts are for: "Ingredients for 2 servings". */
    val ingredientsHeading: String,
    val ingredients: List<SharedLine>,
    val stepsHeading: String,
    val steps: List<String>,
    /** Said under it: where it is from and its licence, and that a model had a part in it. */
    val notes: List<String>,
)

/**
 * The recipe as a text for a message, line for line as the web writes it. Its spaces are plain, as a
 * message's are: the no-break spaces of the app hold its own lines, not those of a message.
 */
fun recipeText(recipe: SharedRecipe): String = buildList {
    add(recipe.title)
    add("")
    add("${recipe.ingredientsHeading}:")
    recipe.ingredients.forEach { line -> add("- ${line.name}: ${line.amount}${line.note?.let { " — $it" }.orEmpty()}") }
    add("")
    add("${recipe.stepsHeading}:")
    recipe.steps.forEachIndexed { index, step -> add("${index + 1}. $step") }
    if (recipe.notes.isNotEmpty()) {
        add("")
        addAll(recipe.notes)
    }
}.joinToString("\n").replace(PLAIN, " ")

private val PLAIN = Regex("[\u00A0\u202F]")

/** [text] as the text of a page: its marks of markup written out, so that a title or a step is only ever read. */
private fun escaped(text: String): String = buildString {
    text.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}

/**
 * The recipe as a page to print, laid out as the web's document: the application's [mark] (a PNG in
 * base64, where there is one) and [appName], the title, the ingredients with their amounts, the steps
 * by number, and the notes under them. It names nothing outside itself: the page runs no script and
 * loads nothing.
 */
fun recipePage(recipe: SharedRecipe, appName: String, mark: String?): String = buildString {
    append("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
    append(escaped(recipe.title))
    append("</title><style>")
    append(PAGE_STYLE)
    append("</style></head><body><header>")
    mark?.let { append("<img src=\"data:image/png;base64,").append(it).append("\" alt=\"\">") }
    append("<span>").append(escaped(appName)).append("</span></header>")
    append("<h1>").append(escaped(recipe.title)).append("</h1>")
    append("<h2>").append(escaped(recipe.ingredientsHeading)).append("</h2><table>")
    recipe.ingredients.forEach { line ->
        append("<tr><td>").append(escaped(line.name))
        line.note?.let { append("<small>").append(escaped(it)).append("</small>") }
        append("</td><td class=\"amount\">").append(escaped(line.amount)).append("</td></tr>")
    }
    append("</table><h2>").append(escaped(recipe.stepsHeading)).append("</h2><ol>")
    recipe.steps.forEach { append("<li>").append(escaped(it)).append("</li>") }
    append("</ol>")
    recipe.notes.forEach { append("<p class=\"note\">").append(escaped(it)).append("</p>") }
    append("</body></html>")
}

/** The web's document, in a page's terms: A4, the same sizes of type and the same lines. */
private const val PAGE_STYLE = """
@page { size: A4; margin: 13mm 15mm 17mm; }
body { font-family: sans-serif; font-size: 10.5pt; line-height: 1.4; color: #000; margin: 0; }
header { display: flex; align-items: center; gap: 6pt; padding-bottom: 6pt; margin-bottom: 16pt; border-bottom: 1pt solid #000; font-size: 11pt; font-weight: 700; }
header img { width: 22pt; height: 22pt; }
h1 { font-size: 20pt; line-height: 1.2; margin: 0 0 16pt; }
h2 { font-size: 8.5pt; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6pt; margin: 0 0 4pt; padding-bottom: 2pt; border-bottom: 0.5pt solid #999; }
table { width: 100%; border-collapse: collapse; margin-bottom: 16pt; }
td { padding: 3pt 0; border-bottom: 0.5pt solid #ddd; vertical-align: top; }
td small { display: block; font-size: 8.5pt; color: #555; }
td.amount { text-align: right; color: #222; padding-left: 12pt; white-space: nowrap; }
tr, li { break-inside: avoid; }
ol { margin: 0 0 16pt; padding-left: 20pt; }
li { margin-bottom: 6pt; }
li::marker { font-weight: 700; }
p.note { font-size: 8pt; color: #555; margin: 2pt 0 0; }
"""

/** Pages being made for the printing: one let go of before it is handed over is collected, and nothing prints. */
private val beingPrinted = mutableSetOf<WebView>()

/**
 * Hands [recipe] to the system's printing, whose window saves it as a PDF, prints it or passes it on,
 * under the recipe's title. The page is made on the phone from what the app holds: the view that lays
 * it out runs no script, may fetch nothing and opens no address.
 */
fun printRecipe(context: Context, recipe: SharedRecipe, appName: String) {
    val page = WebView(context)
    page.settings.apply {
        javaScriptEnabled = false
        blockNetworkLoads = true
        allowFileAccess = false
        allowContentAccess = false
    }
    page.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun onPageFinished(view: WebView, url: String?) {
            // Laid out once: a second call for the same page has nothing more to print.
            if (!beingPrinted.remove(view)) return
            val printing = context.getSystemService(PrintManager::class.java)
            printing.print(
                recipe.title,
                view.createPrintDocumentAdapter(recipe.title),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
            )
        }
    }
    beingPrinted += page
    page.loadDataWithBaseURL(null, recipePage(recipe, appName, markOf(context)), "text/html", "utf-8", null)
}

/** The application's icon, in the shape the phone gives its icons, as a PNG in base64: the mark at the head of the page. */
private fun markOf(context: Context): String? = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.let { icon ->
    val png = ByteArrayOutputStream()
    icon.toBitmap(MARK_PIXELS, MARK_PIXELS).compress(Bitmap.CompressFormat.PNG, 100, png)
    Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP)
}

private const val MARK_PIXELS = 96

/** How many [servings] the amounts are for, in the words a recipe's servings take: "2 servings", "1.5 servings". */
@Composable
private fun servingsFor(servings: Double): String =
    if (servings % 1.0 == 0.0) {
        pluralStringResource(R.plurals.plans_portion_recipe, servings.toInt(), formatNumber(servings))
    } else {
        // A count with a fraction takes the form the languages give fractions.
        stringResource(R.string.plans_offer_servings_fraction, formatNumber(servings, fractionDigits = 1))
    }

/**
 * A recipe as it is sent, from what it is opened with: [lines] in the amounts shown, which are for
 * [servings]; where it is from ([source]) and whether a model had a part in it ([byModel]).
 */
@Composable
fun sharedRecipe(
    title: String,
    servings: Double,
    lines: List<SharedLine>,
    steps: List<String>,
    source: RecipeSource?,
    byModel: Boolean,
): SharedRecipe {
    val from = source?.let { webAddress(it.url) }?.let { stringResource(R.string.recipe_source, it) }
    val licence = source?.let { stringResource(R.string.recipe_source_licence, it.license) }
    val modelNote = if (byModel) stringResource(R.string.recipe_share_by_model) else null
    return SharedRecipe(
        title = title,
        ingredientsHeading = stringResource(R.string.recipe_share_ingredients_for, servingsFor(servings)),
        ingredients = lines,
        stepsHeading = stringResource(R.string.recipe_share_method),
        steps = steps,
        notes = listOfNotNull(licence?.let { listOfNotNull(from, it).joinToString(DOT) }, modelNote),
    )
}

/**
 * The ways to send [recipe] to somebody who is to cook it, as the web offers them: as a text, through
 * the system's sharing, and as a PDF, through its printing. Nothing goes to the instance: what is
 * sent goes where the user sends it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeShareRow(recipe: SharedRecipe, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val chooser = stringResource(R.string.recipe_share_chooser)
    val appName = stringResource(R.string.app_name)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.recipe_share_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The two ways stay together: on a narrow screen they go under the words as one.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { shareText(context, recipe.title, recipeText(recipe), chooser) }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.recipe_share_text), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { printRecipe(context, recipe, appName) }) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.recipe_share_pdf), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
