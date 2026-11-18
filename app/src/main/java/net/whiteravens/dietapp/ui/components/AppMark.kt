// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import net.whiteravens.dietapp.R

/**
 * The application's mark, its icon in the shape the phone gives its icons: where the product introduces itself (the
 * first start, signing in, the locked screen), as the launcher shows it. A picture without words: the name stands
 * beside it.
 */
@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val mark = remember(pixels) {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(pixels, pixels)?.asImageBitmap()
    }
    if (mark != null) Image(mark, contentDescription = null, modifier = modifier.size(size))
}
