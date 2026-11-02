// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.EAN8Writer
import com.google.zxing.oned.UPCAWriter
import com.google.zxing.oned.UPCEWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A barcode read off a picture of its brightness, as the camera gives it. */
class BarcodeReaderTest {
    private fun picture(bars: BitMatrix): ByteArray = ByteArray(bars.width * bars.height) { at ->
        if (bars[at % bars.width, at / bars.width]) 0 else -1
    }

    /** The picture read as the camera reads it, frame after frame. */
    private fun read(bars: BitMatrix, times: Int = 2): String? {
        val reader = BarcodeReader()
        return (1..times).map { reader.read(picture(bars), bars.width, bars.height) }.last()
    }

    @Test
    fun `reads every kind a product carries, as Open Food Facts keeps it`() {
        assertEquals("5900512320359", read(EAN13Writer().encode("5900512320359", BarcodeFormat.EAN_13, 400, 160)))
        assertEquals("20077242", read(EAN8Writer().encode("20077242", BarcodeFormat.EAN_8, 300, 160)))
        assertEquals("0036000291452", read(UPCAWriter().encode("036000291452", BarcodeFormat.UPC_A, 400, 160)))
    }

    @Test
    fun `writes a short UPC out in full`() {
        // 01234565 stands for 012345000065.
        assertEquals("0012345000065", read(UPCEWriter().encode("01234565", BarcodeFormat.UPC_E, 400, 160)))
    }

    @Test
    fun `counts a code read twice running, not once`() {
        val bars = EAN13Writer().encode("5900512320359", BarcodeFormat.EAN_13, 400, 160)
        val reader = BarcodeReader()
        assertNull(reader.read(picture(bars), bars.width, bars.height))
        assertEquals("5900512320359", reader.read(picture(bars), bars.width, bars.height))
    }

    @Test
    fun `a frame of something else in between starts again`() {
        val bars = EAN13Writer().encode("5900512320359", BarcodeFormat.EAN_13, 400, 160)
        val blank = ByteArray(bars.width * bars.height) { -1 }
        val reader = BarcodeReader()
        reader.read(picture(bars), bars.width, bars.height)
        assertNull(reader.read(blank, bars.width, bars.height))
        assertNull(reader.read(picture(bars), bars.width, bars.height))
    }

    @Test
    fun `reads a code held across the picture`() {
        val bars = EAN13Writer().encode("5900512320359", BarcodeFormat.EAN_13, 400, 160)
        val across = ByteArray(bars.width * bars.height)
        for (y in 0 until bars.height) {
            for (x in 0 until bars.width) across[x * bars.height + bars.height - 1 - y] = if (bars[x, y]) 0 else -1
        }
        val reader = BarcodeReader()
        reader.read(across, bars.height, bars.width)
        assertEquals("5900512320359", reader.read(across, bars.height, bars.width))
    }

    @Test
    fun `goes by the row a camera's picture is laid out in`() {
        val bars = EAN13Writer().encode("5900512320359", BarcodeFormat.EAN_13, 400, 160)
        val stride = bars.width + 16
        val wider = ByteArray(stride * bars.height) { -1 }
        for (y in 0 until bars.height) {
            for (x in 0 until bars.width) wider[y * stride + x] = if (bars[x, y]) 0 else -1
        }
        val reader = BarcodeReader()
        reader.read(wider, bars.width, bars.height, stride)
        assertEquals("5900512320359", reader.read(wider, bars.width, bars.height, stride))
    }

    @Test
    fun `finds nothing in a picture of nothing`() {
        val reader = BarcodeReader()
        val noise = ByteArray(400 * 160) { at -> if ((at * 2654435761L).toInt() and 64 == 0) 0 else -1 }
        repeat(2) { assertNull(reader.read(noise, 400, 160)) }
    }
}
