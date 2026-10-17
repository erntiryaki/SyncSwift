package net.syncswift

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.ui.theme.SuccessGreen
import net.syncswift.ui.theme.ErrorRed

private val Blue = Color(0xFF6A7F9D)
private val Cloud = Color(0xFFF8FAFC)
private val Mist = Color(0xFF94A3B8)

/** Vector-crafted official SyncSwift 'S' Arrow Logo Icon */
@Composable
fun SyncArrowIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val strokeWidth = size.minDimension * 0.13f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val cx = w / 2f
    val cy = h / 2f
    val r = w * 0.26f

    // Upper curve of 'S'
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r * 1.25f),
        size = Size(r * 2f, r * 2f),
        style = stroke
    )

    // Lower curve of 'S'
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r * 0.75f),
        size = Size(r * 2f, r * 2f),
        style = stroke
    )

    // Top Arrow Head
    val topArrow = Path().apply {
        moveTo(cx + r * 0.6f, cy - r * 1.35f)
        lineTo(cx + r * 1.0f, cy - r * 0.9f)
        lineTo(cx + r * 0.5f, cy - r * 0.7f)
    }
    drawPath(topArrow, color, style = stroke)

    // Bottom Arrow Head
    val bottomArrow = Path().apply {
        moveTo(cx - r * 0.6f, cy + r * 1.35f)
        lineTo(cx - r * 1.0f, cy + r * 0.9f)
        lineTo(cx - r * 0.5f, cy + r * 0.7f)
    }
    drawPath(bottomArrow, color, style = stroke)
}

@Composable
fun DesktopVectorIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val s = Stroke(size.minDimension * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    // Monitor screen frame
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.15f),
        size = Size(w * 0.76f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = s
    )
    // Stand vertical stem
    drawLine(color, Offset(w * 0.5f, h * 0.69f), Offset(w * 0.5f, h * 0.84f), strokeWidth = s.width, cap = s.cap)
    // Stand horizontal base
    drawLine(color, Offset(w * 0.32f, h * 0.84f), Offset(w * 0.68f, h * 0.84f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun UploadIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .085f, cap = StrokeCap.Round)
    val x = size.width / 2; val h = size.height
    drawLine(color, Offset(x, h * .74f), Offset(x, h * .18f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(x, h * .18f), Offset(size.width * .29f, h * .4f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(x, h * .18f), Offset(size.width * .71f, h * .4f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(size.width * .2f, h * .82f), Offset(size.width * .8f, h * .82f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun EmailIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .075f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawRoundRect(color, Offset(w * .1f, h * .2f), Size(w * .8f, h * .6f), CornerRadius(w * .1f), style = s)
    drawPath(Path().apply {
        moveTo(w * .12f, h * .24f)
        lineTo(w * .5f, h * .54f)
        lineTo(w * .88f, h * .24f)
    }, color, style = s)
}

@Composable
fun LockIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .08f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawRoundRect(color, Offset(w * .18f, h * .42f), Size(w * .64f, h * .48f), CornerRadius(w * .1f), style = s)
    drawArc(color, 180f, 180f, false, topLeft = Offset(w * .32f, h * .16f), size = Size(w * .36f, h * .48f), style = s)
    drawCircle(color, radius = w * .06f, center = Offset(w * .5f, h * .62f))
}

@Composable
fun EyeIcon(modifier: Modifier = Modifier, color: Color = Mist, visible: Boolean = false) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .08f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawPath(Path().apply {
        moveTo(w * .1f, h * .5f)
        cubicTo(w * .3f, h * .2f, w * .7f, h * .2f, w * .9f, h * .5f)
        cubicTo(w * .7f, h * .8f, w * .3f, h * .8f, w * .1f, h * .5f)
    }, color, style = s)
    drawCircle(color, radius = w * .12f, center = Offset(w * .5f, h * .5f))
    if (!visible) {
        drawLine(color, Offset(w * .18f, h * .82f), Offset(w * .82f, h * .18f), strokeWidth = s.width, cap = s.cap)
    }
}

@Composable
fun UserIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .055f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawCircle(color, radius = w * .18f, center = Offset(w * .5f, h * .32f), style = s)
    drawArc(color, 180f, 180f, false, topLeft = Offset(w * .18f, h * .52f), size = Size(w * .64f, h * .4f), style = s)
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .08f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawPath(Path().apply {
        moveTo(w * .2f, h * .5f)
        lineTo(w * .42f, h * .72f)
        lineTo(w * .82f, h * .28f)
    }, color, style = s)
}

@Composable
fun FingerprintIcon(modifier: Modifier = Modifier, color: Color = Cloud) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.065f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val cy = size.height / 2f

    drawArc(color, 200f, 140f, false, topLeft = Offset(cx - size.width * 0.18f, cy - size.height * 0.22f), size = Size(size.width * 0.36f, size.height * 0.36f), style = s)
    drawArc(color, 190f, 160f, false, topLeft = Offset(cx - size.width * 0.28f, cy - size.height * 0.32f), size = Size(size.width * 0.56f, size.height * 0.56f), style = s)
    drawArc(color, 180f, 180f, false, topLeft = Offset(cx - size.width * 0.38f, cy - size.height * 0.40f), size = Size(size.width * 0.76f, size.height * 0.72f), style = s)
}

@Composable
fun ClipboardVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val s = Stroke(size.minDimension * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.22f),
        size = Size(w * 0.64f, h * 0.72f),
        cornerRadius = CornerRadius(w * 0.08f, h * 0.08f),
        style = s
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.32f, h * 0.10f),
        size = Size(w * 0.36f, h * 0.18f),
        cornerRadius = CornerRadius(w * 0.05f, h * 0.05f),
        style = s
    )
    drawLine(color, Offset(w * 0.32f, h * 0.48f), Offset(w * 0.68f, h * 0.48f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(w * 0.32f, h * 0.66f), Offset(w * 0.56f, h * 0.66f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun KeyVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val s = Stroke(size.minDimension * 0.07f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.32f, h * 0.42f), style = s)
    drawLine(color, Offset(w * 0.50f, h * 0.42f), Offset(w * 0.84f, h * 0.42f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(w * 0.70f, h * 0.42f), Offset(w * 0.70f, h * 0.64f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(w * 0.82f, h * 0.42f), Offset(w * 0.82f, h * 0.58f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun ShieldVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val s = Stroke(size.minDimension * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.12f)
        lineTo(w * 0.84f, h * 0.25f)
        lineTo(w * 0.84f, h * 0.55f)
        cubicTo(w * 0.84f, h * 0.76f, w * 0.64f, h * 0.88f, w * 0.5f, h * 0.92f)
        cubicTo(w * 0.36f, h * 0.88f, w * 0.16f, h * 0.76f, w * 0.16f, h * 0.55f)
        lineTo(w * 0.16f, h * 0.25f)
        close()
    }, color, style = s)
}

@Composable
fun TransferVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val s = Stroke(size.minDimension * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.78f, h * 0.35f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.6f, h * 0.20f)
        lineTo(w * 0.8f, h * 0.35f)
        lineTo(w * 0.6f, h * 0.50f)
    }, color, style = s)
    drawLine(color, Offset(w * 0.8f, h * 0.65f), Offset(w * 0.22f, h * 0.65f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.4f, h * 0.50f)
        lineTo(w * 0.2f, h * 0.65f)
        lineTo(w * 0.4f, h * 0.80f)
    }, color, style = s)
}

@Composable
fun HomeVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val s = Stroke(size.minDimension * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(w * 0.22f, h * 0.84f)
        lineTo(w * 0.22f, h * 0.44f)
        lineTo(w * 0.50f, h * 0.18f)
        lineTo(w * 0.78f, h * 0.44f)
        lineTo(w * 0.78f, h * 0.84f)
        lineTo(w * 0.60f, h * 0.84f)
        lineTo(w * 0.60f, h * 0.56f)
        lineTo(w * 0.40f, h * 0.56f)
        lineTo(w * 0.40f, h * 0.84f)
        close()
    }
    drawPath(path, color, style = s)
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = s)
    val teeth = 6
    val rOuter = w * 0.42f
    val rInner = w * 0.32f
    for (i in 0 until teeth) {
        val angle = (i * 360f / teeth) * (kotlin.math.PI / 180f)
        val cos = kotlin.math.cos(angle).toFloat()
        val sin = kotlin.math.sin(angle).toFloat()
        drawLine(
            color,
            Offset(w * 0.5f + cos * rInner, h * 0.5f + sin * rInner),
            Offset(w * 0.5f + cos * rOuter, h * 0.5f + sin * rOuter),
            strokeWidth = s.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LogoutIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Bracket / Door
    drawPath(Path().apply {
        moveTo(w * 0.48f, h * 0.22f)
        lineTo(w * 0.22f, h * 0.22f)
        lineTo(w * 0.22f, h * 0.78f)
        lineTo(w * 0.48f, h * 0.78f)
    }, color, style = s)
    // Arrow pointing out
    drawLine(color, Offset(w * 0.38f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.64f, h * 0.32f)
        lineTo(w * 0.82f, h * 0.5f)
        lineTo(w * 0.64f, h * 0.68f)
    }, color, style = s)
}

@Composable
fun LoginIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Bracket / Door
    drawPath(Path().apply {
        moveTo(w * 0.52f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.78f)
        lineTo(w * 0.52f, h * 0.78f)
    }, color, style = s)
    // Arrow pointing into door
    drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.62f, h * 0.5f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.44f, h * 0.32f)
        lineTo(w * 0.62f, h * 0.5f)
        lineTo(w * 0.44f, h * 0.68f)
    }, color, style = s)
}

@Composable
fun TrashIcon(modifier: Modifier = Modifier, color: Color = ErrorRed) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Lid
    drawLine(color, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), strokeWidth = s.width, cap = s.cap)
    // Handle
    drawPath(Path().apply {
        moveTo(w * 0.38f, h * 0.28f)
        lineTo(w * 0.38f, h * 0.16f)
        lineTo(w * 0.62f, h * 0.16f)
        lineTo(w * 0.62f, h * 0.28f)
    }, color, style = s)
    // Bin body
    drawPath(Path().apply {
        moveTo(w * 0.26f, h * 0.28f)
        lineTo(w * 0.30f, h * 0.84f)
        lineTo(w * 0.70f, h * 0.84f)
        lineTo(w * 0.74f, h * 0.28f)
    }, color, style = s)
    // Inner vertical lines
    drawLine(color, Offset(w * 0.43f, h * 0.42f), Offset(w * 0.43f, h * 0.72f), strokeWidth = s.width * 0.8f, cap = s.cap)
    drawLine(color, Offset(w * 0.57f, h * 0.42f), Offset(w * 0.57f, h * 0.72f), strokeWidth = s.width * 0.8f, cap = s.cap)
}

@Composable
fun DevicesIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Laptop screen
    drawRoundRect(color, Offset(w * 0.16f, h * 0.2f), Size(w * 0.68f, h * 0.45f), CornerRadius(w * 0.06f), style = s)
    // Laptop base
    drawLine(color, Offset(w * 0.08f, h * 0.72f), Offset(w * 0.92f, h * 0.72f), strokeWidth = s.width, cap = s.cap)
    drawLine(color, Offset(w * 0.40f, h * 0.72f), Offset(w * 0.60f, h * 0.72f), strokeWidth = s.width * 1.2f, cap = s.cap)
}

@Composable
fun MobileDeviceIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Phone outline
    drawRoundRect(color, Offset(w * 0.24f, h * 0.12f), Size(w * 0.52f, h * 0.76f), CornerRadius(w * 0.08f), style = s)
    // Home indicator
    drawLine(color, Offset(w * 0.44f, h * 0.80f), Offset(w * 0.56f, h * 0.80f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun BellIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.18f)
        cubicTo(w * 0.35f, h * 0.18f, w * 0.28f, h * 0.35f, w * 0.28f, h * 0.56f)
        lineTo(w * 0.18f, h * 0.72f)
        lineTo(w * 0.82f, h * 0.72f)
        lineTo(w * 0.72f, h * 0.56f)
        cubicTo(w * 0.72f, h * 0.35f, w * 0.65f, h * 0.18f, w * 0.5f, h * 0.18f)
    }, color, style = s)
    drawArc(color, 0f, 180f, false, topLeft = Offset(w * 0.40f, h * 0.74f), size = Size(w * 0.20f, h * 0.14f), style = s)
}

@Composable
fun HelpIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = s)
    drawPath(Path().apply {
        moveTo(w * 0.38f, h * 0.38f)
        cubicTo(w * 0.38f, h * 0.28f, w * 0.62f, h * 0.28f, w * 0.62f, h * 0.42f)
        cubicTo(w * 0.62f, h * 0.52f, w * 0.50f, h * 0.52f, w * 0.50f, h * 0.62f)
    }, color, style = s)
    drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.74f))
}

@Composable
fun InfoIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.06f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f), style = s)
    drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.30f))
    drawLine(color, Offset(w * 0.50f, h * 0.44f), Offset(w * 0.50f, h * 0.72f), strokeWidth = s.width, cap = s.cap)
}

@Composable
fun ChevronRightIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.36f, h * 0.22f)
        lineTo(w * 0.64f, h * 0.50f)
        lineTo(w * 0.36f, h * 0.78f)
    }, color, style = s)
}

@Composable
fun ChevronLeftIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.64f, h * 0.22f)
        lineTo(w * 0.36f, h * 0.50f)
        lineTo(w * 0.64f, h * 0.78f)
    }, color, style = s)
}

@Composable
fun ChevronDownIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.22f, h * 0.36f)
        lineTo(w * 0.50f, h * 0.64f)
        lineTo(w * 0.78f, h * 0.36f)
    }, color, style = s)
}

@Composable
fun OutgoingArrowIcon(modifier: Modifier = Modifier, color: Color = SuccessGreen) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.28f, h * 0.72f), Offset(w * 0.72f, h * 0.28f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.44f, h * 0.28f)
        lineTo(w * 0.72f, h * 0.28f)
        lineTo(w * 0.72f, h * 0.56f)
    }, color, style = s)
}

@Composable
fun IncomingArrowIcon(modifier: Modifier = Modifier, color: Color = SuccessGreen) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), strokeWidth = s.width, cap = s.cap)
    drawPath(Path().apply {
        moveTo(w * 0.56f, h * 0.72f)
        lineTo(w * 0.28f, h * 0.72f)
        lineTo(w * 0.28f, h * 0.44f)
    }, color, style = s)
}

@Composable
fun RadarIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val s = Stroke(size.minDimension * 0.065f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f))
    drawArc(color, 210f, 120f, false, topLeft = Offset(w * 0.22f, h * 0.22f), size = Size(w * 0.56f, h * 0.56f), style = s)
    drawArc(color, 210f, 120f, false, topLeft = Offset(w * 0.08f, h * 0.08f), size = Size(w * 0.84f, h * 0.84f), style = s)
}


/** 3D Half-Open Folder illustration with White Oval Icon Container */

@Composable
fun HeroSync3DGraphic(
    modifier: Modifier = Modifier,
    page: Int = 0,
    copy: Copy? = null
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (page) {
            0 -> HeroFolderWithFiles()
            1 -> HeroLiveClipboardVisual(copy)
            else -> HeroSecurityShieldVisual(copy)
        }
    }
}

@Composable
private fun HeroFolderWithFiles() {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Back Folder Backing Plate
        Box(
            modifier = Modifier
                .offset(y = 12.dp)
                .width(170.dp)
                .height(110.dp)
        ) {
            // Folder Tab
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF334155))
            )
            // Folder Back Wall
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 16.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(Color(0xFF1E293B))
            )
        }

        // 2. Document Cards Floating Out
        // PDF Document (Red Accent)
        Surface(
            modifier = Modifier
                .offset(x = (-42).dp, y = (-38).dp)
                .rotate(-14f)
                .width(54.dp)
                .height(68.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF334155)
        ) {
            Column(Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("PDF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF94A3B8)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF64748B)))
            }
        }

        // DOCX Document (Blue Accent)
        Surface(
            modifier = Modifier
                .offset(x = 42.dp, y = (-42).dp)
                .rotate(12f)
                .width(54.dp)
                .height(68.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF334155)
        ) {
            Column(Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2563EB))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("DOCX", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF94A3B8)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF64748B)))
            }
        }

        // XLSX Document (Green Accent)
        Surface(
            modifier = Modifier
                .offset(x = (-16).dp, y = (-26).dp)
                .rotate(-4f)
                .width(52.dp)
                .height(64.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF334155)
        ) {
            Column(Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("XLSX", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF94A3B8)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF64748B)))
            }
        }

        // PNG Document (Orange Accent)
        Surface(
            modifier = Modifier
                .offset(x = 18.dp, y = (-22).dp)
                .rotate(8f)
                .width(52.dp)
                .height(64.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF334155)
        ) {
            Column(Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("PNG", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF94A3B8)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF64748B)))
            }
        }

        // 3. Front Folder Pocket Wall with White Oval Icon Container
        Box(
            modifier = Modifier
                .offset(y = 28.dp)
                .width(174.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Color(0xFF334155))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // White Oval Container for icon_syncswift.png!
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BrandIcon(Modifier.fillMaxSize(), tint = Color.Unspecified)
                }
                Column {
                    Text("SyncSwift", color = Cloud, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Cross Device Sync", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroLiveClipboardVisual(copy: Copy? = null) {
    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft backdrop glow
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Color(0x153B82F6))
        )

        // 1. Two Connected Devices (Phone + Laptop)
        // Left: Smartphone Frame
        Box(
            modifier = Modifier
                .offset(x = (-66).dp, y = 14.dp)
                .width(68.dp)
                .height(116.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Phone notch
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                )
                Spacer(Modifier.height(8.dp))
                // Note card representation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .padding(4.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ClipboardVectorIcon(Modifier.size(10.dp), Blue)
                            Text(copy?.heroClipboardNote ?: "Text", color = Cloud, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth(0.9f).height(2.dp).background(Color(0xFF64748B)))
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(Color(0xFF475569)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // "Kopyalandı" Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E3A5F))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        CheckIcon(Modifier.size(8.dp), Blue)
                        Text(copy?.heroClipboardCopied ?: "Copied", color = Cloud, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Right: Laptop / Monitor Frame
        Box(
            modifier = Modifier
                .offset(x = 58.dp, y = 16.dp)
                .width(114.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF0F172A))
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    }
                    Text("SyncSwift", color = Color(0xFF64748B), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                // Received clipboard content preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1A2638))
                        .padding(5.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CheckIcon(Modifier.size(9.dp), SuccessGreen)
                            Text(copy?.heroClipboardPasted ?: "Pasted", color = Color(0xFFCBD5E1), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth(0.8f).height(2.dp).background(Blue.copy(alpha = 0.6f)))
                    }
                }
            }
        }

        // 2. Central Floating Live Clipboard Card
        Surface(
            modifier = Modifier
                .offset(y = (-36).dp)
                .width(182.dp)
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2A3B),
            border = BorderStroke(1.dp, Color(0xFF334661)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF283A52)),
                    contentAlignment = Alignment.Center
                ) {
                    ClipboardVectorIcon(Modifier.size(18.dp), Blue)
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(copy?.heroClipboardTitle ?: "LIVE CLIPBOARD", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(copy?.heroClipboardSubtitle ?: "Instant Cross-Device", color = Cloud, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

        // 3. Floating Bottom Chip: Otomatik Senkronizasyon
        Surface(
            modifier = Modifier
                .offset(x = 44.dp, y = 58.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E2A3B),
            border = BorderStroke(1.dp, Color(0xFF334661))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransferVectorIcon(Modifier.size(12.dp), Blue)
                Text(copy?.heroClipboardAutoSync ?: "Auto Sync", color = Cloud, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroSecurityShieldVisual(copy: Copy? = null) {
    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer concentric ambient halos
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color(0x0C3B82F6))
        )
        Box(
            modifier = Modifier
                .size(158.dp)
                .clip(CircleShape)
                .background(Color(0x143B82F6))
        )

        // 1. Central Shield Vault Core
        Surface(
            modifier = Modifier
                .size(112.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A2638),
            border = BorderStroke(1.dp, Color(0xFF334661)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShieldVectorIcon(Modifier.size(42.dp), Blue)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(copy?.heroSecurityCore ?: "PROTECTED", color = Color(0xFFCBD5E1), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
        }

        // 2. Floating Top-Left Badge: 256-Bit Şifreleme
        Surface(
            modifier = Modifier
                .offset(x = (-56).dp, y = (-54).dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334661))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LockIcon(Modifier.size(14.dp), Blue)
                Column {
                    Text(copy?.heroSecurityBadge1Title ?: "256-Bit Encryption", color = Cloud, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(copy?.heroSecurityBadge1Sub ?: "End-to-End", color = Color(0xFF94A3B8), fontSize = 8.sp)
                }
            }
        }

        // 3. Floating Top-Right Badge: Sıfır Bilgi
        Surface(
            modifier = Modifier
                .offset(x = 58.dp, y = (-46).dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334661))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyVectorIcon(Modifier.size(14.dp), Blue)
                Column {
                    Text(copy?.heroSecurityBadge2Title ?: "Zero Knowledge", color = Cloud, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(copy?.heroSecurityBadge2Sub ?: "Client-Side Only", color = Color(0xFF94A3B8), fontSize = 8.sp)
                }
            }
        }

        // 4. Floating Bottom Chip: Doğrudan Aktarım
        Surface(
            modifier = Modifier
                .offset(x = (-6).dp, y = 62.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E2A3B),
            border = BorderStroke(1.dp, Color(0xFF334661))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransferVectorIcon(Modifier.size(12.dp), Blue)
                Text(copy?.heroSecurityBadge3 ?: "Direct Peer-to-Peer", color = Cloud, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Vector Globe / Language Icon */
@Composable
fun GlobeVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.075f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    val r = size.minDimension * 0.42f
    val center = Offset(size.width / 2f, size.height / 2f)

    // Outer circle
    drawCircle(color = color, radius = r, center = center, style = stroke)

    // Equator line
    drawLine(
        color = color,
        start = Offset(center.x - r, center.y),
        end = Offset(center.x + r, center.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Vertical meridian ellipse
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.45f, center.y - r),
        size = Size(r * 0.9f, r * 2f),
        style = stroke
    )
}

/** Vector Sun / Light Theme Icon */
@Composable
fun SunVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.08f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    val center = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.22f

    // Center Sun Circle
    drawCircle(color = color, radius = r, center = center, style = stroke)

    // 8 Rays
    val rayInner = r + size.minDimension * 0.08f
    val rayOuter = rayInner + size.minDimension * 0.12f
    val angles = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)

    for (deg in angles) {
        val rad = kotlin.math.PI * deg / 180.0
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + rayInner * cos, center.y + rayInner * sin),
            end = Offset(center.x + rayOuter * cos, center.y + rayOuter * sin),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/** Vector Moon / Dark Theme Icon */
@Composable
fun MoonVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.08f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    val moonPath = Path().apply {
        moveTo(w * 0.65f, h * 0.18f)
        cubicTo(
            w * 0.28f, h * 0.22f,
            w * 0.22f, h * 0.72f,
            w * 0.62f, h * 0.85f
        )
        cubicTo(
            w * 0.46f, h * 0.72f,
            w * 0.48f, h * 0.38f,
            w * 0.65f, h * 0.18f
        )
        close()
    }
    drawPath(moonPath, color = color, style = stroke)
}

/** Vector Edit / Pencil Icon */
@Composable
fun EditVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.08f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    // Pencil Body
    val path = Path().apply {
        moveTo(w * 0.68f, h * 0.16f)
        lineTo(w * 0.84f, h * 0.32f)
        lineTo(w * 0.34f, h * 0.82f)
        lineTo(w * 0.18f, h * 0.84f)
        lineTo(w * 0.20f, h * 0.68f)
        close()
    }
    drawPath(path, color = color, style = stroke)

    // Base Line
    drawLine(
        color = color,
        start = Offset(w * 0.16f, h * 0.90f),
        end = Offset(w * 0.86f, h * 0.90f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/** Vector Sparkle Icon */
@Composable
fun SparkleVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.08f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.12f)
        cubicTo(w * 0.5f, h * 0.38f, w * 0.62f, h * 0.5f, w * 0.88f, h * 0.5f)
        cubicTo(w * 0.62f, h * 0.5f, w * 0.5f, h * 0.62f, w * 0.5f, h * 0.88f)
        cubicTo(w * 0.5f, h * 0.62f, w * 0.38f, h * 0.5f, w * 0.12f, h * 0.5f)
        cubicTo(w * 0.38f, h * 0.5f, w * 0.5f, h * 0.38f, w * 0.5f, h * 0.12f)
        close()
    }
    drawPath(path, color = color, style = stroke)
}

/** Minimalist Pro Crown Vector Icon */
@Composable
fun CrownVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.08f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    // Crown peaks & base
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.74f)
        lineTo(w * 0.18f, h * 0.34f)
        lineTo(w * 0.38f, h * 0.52f)
        lineTo(w * 0.50f, h * 0.22f)
        lineTo(w * 0.62f, h * 0.52f)
        lineTo(w * 0.82f, h * 0.34f)
        lineTo(w * 0.82f, h * 0.74f)
        close()
    }
    drawPath(path, color = color, style = stroke)

    // Base horizontal bar
    drawLine(
        color = color,
        start = Offset(w * 0.22f, h * 0.84f),
        end = Offset(w * 0.78f, h * 0.84f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/** Vector QR Code Icon */
@Composable
fun QrCodeVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val sw = size.minDimension * 0.08f
    val stroke = Stroke(sw, cap = StrokeCap.Square)
    val w = size.width
    val h = size.height

    // Top-Left finder
    drawRect(color = color, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(w * 0.28f, h * 0.28f), style = stroke)
    drawRect(color = color, topLeft = Offset(w * 0.23f, h * 0.23f), size = Size(w * 0.12f, h * 0.12f))

    // Top-Right finder
    drawRect(color = color, topLeft = Offset(w * 0.57f, h * 0.15f), size = Size(w * 0.28f, h * 0.28f), style = stroke)
    drawRect(color = color, topLeft = Offset(w * 0.65f, h * 0.23f), size = Size(w * 0.12f, h * 0.12f))

    // Bottom-Left finder
    drawRect(color = color, topLeft = Offset(w * 0.15f, h * 0.57f), size = Size(w * 0.28f, h * 0.28f), style = stroke)
    drawRect(color = color, topLeft = Offset(w * 0.23f, h * 0.65f), size = Size(w * 0.12f, h * 0.12f))

    // Data dots
    drawRect(color = color, topLeft = Offset(w * 0.58f, h * 0.58f), size = Size(w * 0.10f, h * 0.10f))
    drawRect(color = color, topLeft = Offset(w * 0.75f, h * 0.58f), size = Size(w * 0.10f, h * 0.10f))
    drawRect(color = color, topLeft = Offset(w * 0.58f, h * 0.75f), size = Size(w * 0.10f, h * 0.10f))
    drawRect(color = color, topLeft = Offset(w * 0.75f, h * 0.75f), size = Size(w * 0.10f, h * 0.10f))
}

/** Vector QR Scan Viewfinder Icon */
@Composable
fun QrScanVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val sw = size.minDimension * 0.08f
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    // 4 Viewfinder Corners
    val p = Path().apply {
        // Top-Left
        moveTo(w * 0.15f, h * 0.35f)
        lineTo(w * 0.15f, h * 0.18f)
        lineTo(w * 0.35f, h * 0.18f)

        // Top-Right
        moveTo(w * 0.65f, h * 0.18f)
        lineTo(w * 0.85f, h * 0.18f)
        lineTo(w * 0.85f, h * 0.35f)

        // Bottom-Right
        moveTo(w * 0.85f, h * 0.65f)
        lineTo(w * 0.85f, h * 0.82f)
        lineTo(w * 0.65f, h * 0.82f)

        // Bottom-Left
        moveTo(w * 0.35f, h * 0.82f)
        lineTo(w * 0.15f, h * 0.82f)
        lineTo(w * 0.15f, h * 0.65f)
    }
    drawPath(p, color = color, style = stroke)

    // Center Laser / Scan Line
    drawLine(
        color = color,
        start = Offset(w * 0.22f, h * 0.50f),
        end = Offset(w * 0.78f, h * 0.50f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
}

/** Vector Smartphone Icon for mobile devices (Android / iOS) */
@Composable
fun PhoneVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val sw = size.minDimension * 0.08f
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    // Phone body
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.27f, h * 0.12f),
        size = Size(w * 0.46f, h * 0.76f),
        cornerRadius = CornerRadius(w * 0.08f, h * 0.08f),
        style = stroke
    )

    // Speaker notch
    drawLine(
        color = color,
        start = Offset(w * 0.43f, h * 0.20f),
        end = Offset(w * 0.57f, h * 0.20f),
        strokeWidth = sw * 0.85f,
        cap = StrokeCap.Round
    )

    // Home indicator
    drawLine(
        color = color,
        start = Offset(w * 0.44f, h * 0.80f),
        end = Offset(w * 0.56f, h * 0.80f),
        strokeWidth = sw * 0.85f,
        cap = StrokeCap.Round
    )
}

/** Vector Laptop / Desktop Icon for PC (Windows / macOS) */
@Composable
fun LaptopVectorIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val sw = size.minDimension * 0.08f
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height

    // Screen
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.20f, h * 0.22f),
        size = Size(w * 0.60f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.05f, h * 0.05f),
        style = stroke
    )

    // Base keyboard stand
    val basePath = Path().apply {
        moveTo(w * 0.10f, h * 0.72f)
        lineTo(w * 0.90f, h * 0.72f)
        lineTo(w * 0.86f, h * 0.78f)
        lineTo(w * 0.14f, h * 0.78f)
        close()
    }
    drawPath(basePath, color = color, style = stroke)

    // Notch
    drawLine(
        color = color,
        start = Offset(w * 0.44f, h * 0.72f),
        end = Offset(w * 0.56f, h * 0.72f),
        strokeWidth = sw * 1.2f,
        cap = StrokeCap.Round
    )
}

/** Vector Cross (X) Failure Icon */
@Composable
fun CrossVectorIcon(modifier: Modifier = Modifier, color: Color = ErrorRed) = Canvas(modifier) {
    val sw = size.minDimension * 0.12f
    val w = size.width
    val h = size.height

    drawLine(
        color = color,
        start = Offset(w * 0.28f, h * 0.28f),
        end = Offset(w * 0.72f, h * 0.72f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.72f, h * 0.28f),
        end = Offset(w * 0.28f, h * 0.72f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
}

/** Animated Checkmark (Tik) with drawing effect inside circle */
@Composable
fun AnimatedCheckCircleIcon(
    modifier: Modifier = Modifier,
    circleColor: Color = SuccessGreen,
    checkColor: Color = Color.White
) {
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween<Float>(
                durationMillis = 650,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val p = animProgress.value

        // Background circle (scales slightly with animation)
        drawCircle(
            color = circleColor,
            radius = (size.minDimension / 2f) * (0.85f + 0.15f * p)
        )

        // Animated checkmark stroke
        if (p > 0.2f) {
            val checkP = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
            val sw = size.minDimension * 0.10f

            val start = Offset(w * 0.28f, h * 0.50f)
            val mid = Offset(w * 0.44f, h * 0.66f)
            val end = Offset(w * 0.72f, h * 0.36f)

            if (checkP <= 0.4f) {
                val subP = checkP / 0.4f
                drawLine(
                    color = checkColor,
                    start = start,
                    end = Offset(
                        start.x + (mid.x - start.x) * subP,
                        start.y + (mid.y - start.y) * subP
                    ),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = checkColor,
                    start = start,
                    end = mid,
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                val subP = (checkP - 0.4f) / 0.6f
                drawLine(
                    color = checkColor,
                    start = mid,
                    end = Offset(
                        mid.x + (end.x - mid.x) * subP,
                        mid.y + (end.y - mid.y) * subP
                    ),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** Vector Folder Outline Icon */
@Composable
fun FolderOutlineIcon(modifier: Modifier = Modifier, color: Color = Color.White) = Canvas(modifier) {
    val sw = size.minDimension * 0.08f
    val w = size.width
    val h = size.height

    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.25f)
        lineTo(w * 0.40f, h * 0.25f)
        lineTo(w * 0.50f, h * 0.35f)
        lineTo(w * 0.85f, h * 0.35f)
        lineTo(w * 0.85f, h * 0.78f)
        lineTo(w * 0.15f, h * 0.78f)
        close()
    }
    drawPath(path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** Vector Open / External Link Icon */
@Composable
fun OpenExternalIcon(modifier: Modifier = Modifier, color: Color = Color.White) = Canvas(modifier) {
    val sw = size.minDimension * 0.10f
    val w = size.width
    val h = size.height

    // Square box with open top-right corner
    val boxPath = Path().apply {
        moveTo(w * 0.52f, h * 0.22f)
        lineTo(w * 0.22f, h * 0.22f)
        lineTo(w * 0.22f, h * 0.78f)
        lineTo(w * 0.78f, h * 0.78f)
        lineTo(w * 0.78f, h * 0.48f)
    }
    drawPath(boxPath, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))

    // Arrow pointing top right
    drawLine(
        color = color,
        start = Offset(w * 0.44f, h * 0.56f),
        end = Offset(w * 0.78f, h * 0.22f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
    val arrowHead = Path().apply {
        moveTo(w * 0.58f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.42f)
    }
    drawPath(arrowHead, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

@Composable
fun RefreshIcon(modifier: Modifier = Modifier, color: Color = Cloud) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.085f
    val r = size.minDimension * 0.34f
    val cx = w * 0.5f
    val cy = h * 0.5f

    // Arc from 50 to 330 degrees (leaving a gap at top-right for arrow)
    drawArc(
        color = color,
        startAngle = 50f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )

    // Arrow pointing into the circle at the end of the arc (top-right)
    val headSize = size.minDimension * 0.16f
    val arrowTipX = cx + r * 0.65f
    val arrowTipY = cy - r * 0.75f
    val arrow = Path().apply {
        moveTo(arrowTipX - headSize, arrowTipY - headSize * 0.1f)
        lineTo(arrowTipX + headSize * 0.2f, arrowTipY)
        lineTo(arrowTipX - headSize * 0.1f, arrowTipY + headSize)
    }
    drawPath(arrow, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun NotificationBellIcon(modifier: Modifier = Modifier, color: Color = Cloud) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.08f
    val s = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Bell dome & flare
    val bellPath = Path().apply {
        moveTo(w * 0.5f, h * 0.18f)
        cubicTo(w * 0.35f, h * 0.18f, w * 0.28f, h * 0.35f, w * 0.28f, h * 0.55f)
        cubicTo(w * 0.28f, h * 0.68f, w * 0.20f, h * 0.74f, w * 0.20f, h * 0.76f)
        lineTo(w * 0.80f, h * 0.76f)
        cubicTo(w * 0.80f, h * 0.74f, w * 0.72f, h * 0.68f, w * 0.72f, h * 0.55f)
        cubicTo(w * 0.72f, h * 0.35f, w * 0.65f, h * 0.18f, w * 0.50f, h * 0.18f)
    }
    drawPath(bellPath, color = color, style = s)

    // Bell top small loop
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.44f, h * 0.10f),
        size = Size(w * 0.12f, h * 0.10f),
        style = s
    )

    // Clapper at bottom
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.42f, h * 0.76f),
        size = Size(w * 0.16f, h * 0.12f),
        style = s
    )
}

@Composable
fun WarningTriangleIcon(modifier: Modifier = Modifier, color: Color = ErrorRed) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.085f
    val s = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val tri = Path().apply {
        moveTo(w * 0.5f, h * 0.16f)
        lineTo(w * 0.86f, h * 0.82f)
        lineTo(w * 0.14f, h * 0.82f)
        close()
    }
    drawPath(tri, color = color, style = s)
    // Exclamation point stem
    drawLine(color, Offset(w * 0.5f, h * 0.40f), Offset(w * 0.5f, h * 0.60f), strokeWidth = sw, cap = StrokeCap.Round)
    // Dot
    drawCircle(color, radius = sw * 0.7f, center = Offset(w * 0.5f, h * 0.72f))
}

@Composable
fun StarVectorIcon(modifier: Modifier = Modifier, color: Color = Blue, filled: Boolean = false) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.08f
    val path = Path().apply {
        moveTo(w * 0.50f, h * 0.12f)
        lineTo(w * 0.62f, h * 0.36f)
        lineTo(w * 0.88f, h * 0.40f)
        lineTo(w * 0.69f, h * 0.59f)
        lineTo(w * 0.74f, h * 0.86f)
        lineTo(w * 0.50f, h * 0.73f)
        lineTo(w * 0.26f, h * 0.86f)
        lineTo(w * 0.31f, h * 0.59f)
        lineTo(w * 0.12f, h * 0.40f)
        lineTo(w * 0.38f, h * 0.36f)
        close()
    }
    if (filled) {
        drawPath(path, color = color)
    } else {
        drawPath(path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SearchVectorIcon(modifier: Modifier = Modifier, color: Color = Mist) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.09f
    val s = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Glass circle
    drawCircle(
        color = color,
        radius = w * 0.32f,
        center = Offset(w * 0.42f, h * 0.42f),
        style = s
    )
    // Handle
    drawLine(
        color = color,
        start = Offset(w * 0.65f, h * 0.65f),
        end = Offset(w * 0.86f, h * 0.86f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
}


