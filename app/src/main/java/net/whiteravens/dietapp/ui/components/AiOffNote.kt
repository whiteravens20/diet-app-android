// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import net.whiteravens.dietapp.R

/**
 * Said beside what asks a model, on an account that has no model to ask: that those actions are
 * off, and where AI is turned on, as a link that opens the account's settings, as the web's note
 * links its own.
 */
@Composable
fun AiOffNote(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    val link = stringResource(R.string.ai_off_turn_on)
    val whole = stringResource(R.string.ai_off_note, link)
    val linked = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(whole)
        val start = whole.indexOf(link)
        if (start >= 0) {
            addLink(
                LinkAnnotation.Clickable(
                    tag = "account",
                    styles = TextLinkStyles(SpanStyle(color = linked, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
                ) { onTurnOn() },
                start,
                start + link.length,
            )
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The same said where no link can stand: a field's hint, or what a button that is off says of itself. */
@Composable
fun aiOffText(): String = stringResource(R.string.ai_off_note, stringResource(R.string.ai_off_turn_on))
