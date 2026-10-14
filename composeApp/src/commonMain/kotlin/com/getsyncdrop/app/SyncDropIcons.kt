package com.getsyncdrop.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF38BDF8)
private val Cloud = Color(0xFFF8FAFC)
private val Mist = Color(0xFF94A3B8)

/** Vector-crafted official SyncDrop 'S' Arrow Logo Icon */
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
    val s = Stroke(size.minDimension * .08f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawCircle(color, radius = w * .18f, center = Offset(w * .5f, h * .32f), style = s)
    drawArc(color, 180f, 180f, false, topLeft = Offset(w * .18f, h * .52f), size = Size(w * .64f, h * .4f), style = s)
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, color: Color = Blue) = Canvas(modifier) {
    val s = Stroke(size.minDimension * .12f, cap = StrokeCap.Round)
    val w = size.width; val h = size.height
    drawPath(Path().apply {
        moveTo(w * .2f, h * .5f)
        lineTo(w * .42f, h * .72f)
        lineTo(w * .82f, h * .28f)
    }, color, style = s)
}

@Composable
fun FingerprintIcon(modifier: Modifier = Modifier, color: Color = Cloud) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.085f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val cy = size.height / 2f

    drawArc(color, 200f, 140f, false, topLeft = Offset(cx - size.width * 0.18f, cy - size.height * 0.22f), size = Size(size.width * 0.36f, size.height * 0.36f), style = s)
    drawArc(color, 190f, 160f, false, topLeft = Offset(cx - size.width * 0.28f, cy - size.height * 0.32f), size = Size(size.width * 0.56f, size.height * 0.56f), style = s)
    drawArc(color, 180f, 180f, false, topLeft = Offset(cx - size.width * 0.38f, cy - size.height * 0.40f), size = Size(size.width * 0.76f, size.height * 0.72f), style = s)
}

/** 3D Half-Open Folder illustration with White Oval Icon Container */
@Composable
fun HeroSync3DGraphic(
    modifier: Modifier = Modifier,
    page: Int = 0
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (page) {
            0 -> HeroFolderWithFiles()
            1 -> HeroEncryptionVault()
            else -> HeroTransferSpeed()
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
                // White Oval Container for icon_syncdrop.png!
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
                    Text("SyncDrop", color = Cloud, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Cross Device Sync", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroEncryptionVault() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF334155)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LockIcon(Modifier.size(48.dp), Blue)
                Spacer(Modifier.height(8.dp))
                Text("E2E ENCRYPTED", color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun HeroTransferSpeed() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF334155)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                UploadIcon(Modifier.size(46.dp), Blue)
                Spacer(Modifier.height(8.dp))
                Text("INSTANT DROP", color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}
