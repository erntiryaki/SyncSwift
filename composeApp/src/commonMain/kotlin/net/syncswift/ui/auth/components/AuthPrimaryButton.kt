package net.syncswift.ui.auth.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ArrowForwardIcon(modifier: Modifier = Modifier, color: Color = Color.White) = Canvas(modifier) {
    val s = Stroke(width = size.minDimension * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    // Line from left to right
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.5f), androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.5f), s.width, StrokeCap.Round)
    // Arrow head
    drawPath(Path().apply {
        moveTo(w * 0.60f, h * 0.25f)
        lineTo(w * 0.85f, h * 0.5f)
        lineTo(w * 0.60f, h * 0.75f)
    }, color, style = s)
}

@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (enabled && !isLoading) Color(0xFF2563EB)
                else Color(0xFF2563EB).copy(alpha = 0.45f)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
                ArrowForwardIcon(Modifier.size(16.dp), color = Color.White)
            }
        }
    }
}
