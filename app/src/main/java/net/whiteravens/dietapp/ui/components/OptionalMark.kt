// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R

/**
 * The word that says an outlined field may stay empty, in the top edge of its outline at the right,
 * as the web's frame has it. The field is drawn in a layer of its own, where the word's box clears
 * what lies under it, so the outline stops at the word and the surface behind the field shows
 * through, whatever that surface is: a screen, a card or a dialog. TalkBack reads the word as the
 * field's state.
 */
fun Modifier.optionalMark(): Modifier = composed {
    val word = stringResource(R.string.common_optional)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    semantics { stateDescription = word }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val text = measurer.measure(word, style)
            val gap = 4.dp.toPx()
            val width = text.size.width + 2 * gap
            val height = text.size.height.toFloat()
            val left = size.width - 12.dp.toPx() - width
            // An outlined field keeps 8 dp above its outline for the label it floats there: the word
            // stands on that edge, never above the field, which the layer would cut off.
            val top = (OUTLINE_TOP.toPx() - height / 2).coerceAtLeast(0f)
            drawRect(Color.Black, topLeft = Offset(left, top), size = Size(width, height), blendMode = BlendMode.Clear)
            drawText(text, topLeft = Offset(left + gap, top))
        }
}

/** How far below the top of an outlined field its outline runs. */
private val OUTLINE_TOP = 8.dp
