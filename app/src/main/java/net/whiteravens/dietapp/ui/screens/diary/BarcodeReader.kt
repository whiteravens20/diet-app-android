// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.UPCEReader
import net.whiteravens.dietapp.domain.productCodeOf

/**
 * Reads a barcode of a product off a picture of the camera, here on the phone: the picture goes
 * nowhere. Only the kinds a product carries are looked for. The same code read twice running is what
 * counts, so that a frame caught in passing cannot give a wrong one.
 */
class BarcodeReader {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private var lastRead: String? = null

    /**
     * The code in a picture of [width] by [height] of its brightness alone, a row of [rowStride] bytes
     * after a row, where it has just been read the second time running; null otherwise. A picture is
     * read as it lies and turned by a quarter: the bars of a code run either way.
     */
    fun read(brightness: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
        val code = readOnce(brightness, rowStride, width, height) ?: readOnce(turned(brightness, width, height, rowStride), height, height, width)
        val confirmed = code != null && code == lastRead
        lastRead = code
        return code.takeIf { confirmed }
    }

    /** The two ways of telling a bar from what is around it see different pictures well, so one gets the other's second chance. */
    private fun readOnce(bytes: ByteArray, dataWidth: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(bytes, dataWidth, height, 0, 0, width, height, false)
        return decoded(BinaryBitmap(HybridBinarizer(source))) ?: decoded(BinaryBitmap(GlobalHistogramBinarizer(source)))
    }

    private fun decoded(picture: BinaryBitmap): String? = try {
        val result = reader.decodeWithState(picture)
        productCodeOf(if (result.barcodeFormat == BarcodeFormat.UPC_E) UPCEReader.convertUPCEtoUPCA(result.text) else result.text)
    } catch (e: NotFoundException) {
        null
    } finally {
        reader.reset()
    }

    private fun turned(bytes: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) out[x * height + height - 1 - y] = bytes[y * rowStride + x]
        }
        return out
    }
}
