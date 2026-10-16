package net.syncswift.ui.components

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.Foundation.*

actual object QrCodeHelper {
    @OptIn(ExperimentalForeignApi::class)
    actual fun generateQrMatrix(content: String): List<BooleanArray> {
        return try {
            val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return emptyList()
            val data = (content as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return emptyList()
            filter.setValue(data, forKey = "inputMessage")
            filter.setValue("M", forKey = "inputCorrectionLevel")

            val outputImage = filter.outputImage ?: return emptyList()
            val extent = outputImage.extent
            val width = extent.useContents { size.width.toInt() }
            val height = extent.useContents { size.height.toInt() }

            if (width <= 0 || height <= 0) return emptyList()

            val ciContext = CIContext.context()
            val cgImage = ciContext.createCGImage(outputImage, fromRect = extent) ?: return emptyList()

            val colorSpace = CGColorSpaceCreateDeviceGray()
            val bytesPerRow = width
            val rawData = ByteArray(width * height)

            val success = rawData.usePinned { pinned ->
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = width.convert(),
                    height = height.convert(),
                    bitsPerComponent = 8.convert(),
                    bytesPerRow = bytesPerRow.convert(),
                    space = colorSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value
                )
                if (context != null) {
                    CGContextDrawImage(context, extent, cgImage)
                    CGContextRelease(context)
                    true
                } else {
                    false
                }
            }
            CGColorSpaceRelease(colorSpace)
            CGImageRelease(cgImage)

            if (!success) return emptyList()

            val result = ArrayList<BooleanArray>(height)
            for (y in 0 until height) {
                val row = BooleanArray(width)
                for (x in 0 until width) {
                    // CoreGraphics drawing origin is bottom-left
                    val pixel = rawData[((height - 1 - y) * width) + x].toInt() and 0xFF
                    row[x] = pixel < 128
                }
                result.add(row)
            }
            result
        } catch (e: Exception) {
            println("[SyncSwift iOS] QR Generation error: ${e.message}")
            emptyList()
        }
    }
}
