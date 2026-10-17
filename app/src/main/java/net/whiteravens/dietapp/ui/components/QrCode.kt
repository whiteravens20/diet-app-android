// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor

/**
 * [content] as a QR code, drawn here on the phone: what it holds goes nowhere else. It is dark on
 * white whatever the theme, with a white border around it, as a camera reads one; [description]
 * says what it is to a screen reader.
 */
@Composable
fun QrCode(content: String, description: String, modifier: Modifier = Modifier, side: Dp = 176.dp) {
    val modules = remember(content) { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Box(modifier.background(Color.White).padding(12.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.size(side)) {
            // Whole pixels to a module, so that no seam of light shows between two dark ones.
            val cell = floor(size.minDimension / modules.width).coerceAtLeast(1f)
            val start = (size.minDimension - cell * modules.width) / 2
            for (y in 0 until modules.height) {
                for (x in 0 until modules.width) {
                    if (modules[x, y]) drawRect(Color.Black, topLeft = Offset(start + x * cell, start + y * cell), size = Size(cell, cell))
                }
            }
        }
    }
}
