package net.syncswift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

expect object QrCodeHelper {
    fun generateQrMatrix(content: String): List<BooleanArray>
}

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    val matrix = remember(content) { QrCodeHelper.generateQrMatrix(content) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(lightColor)
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (matrix.isEmpty()) return@Canvas
            val rows = matrix.size
            val cols = matrix[0].size
            val cellWidth = size.width / cols
            val cellHeight = size.height / rows

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (matrix[y][x]) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
