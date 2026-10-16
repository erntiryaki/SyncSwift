package net.syncswift.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

actual object QrCodeHelper {
    actual fun generateQrMatrix(content: String): List<BooleanArray> {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val result = ArrayList<BooleanArray>(height)
            for (y in 0 until height) {
                val row = BooleanArray(width)
                for (x in 0 until width) {
                    row[x] = bitMatrix.get(x, y)
                }
                result.add(row)
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}
