package net.syncswift.ui.auth.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import net.syncswift.AppLanguage

@Composable
fun CloseXIcon(modifier: Modifier = Modifier, color: Color) = Canvas(modifier) {
    val s = Stroke(width = size.minDimension * 0.16f, cap = StrokeCap.Round)
    val pad = size.minDimension * 0.28f
    drawLine(color, androidx.compose.ui.geometry.Offset(pad, pad), androidx.compose.ui.geometry.Offset(size.width - pad, size.height - pad), s.width, StrokeCap.Round)
    drawLine(color, androidx.compose.ui.geometry.Offset(size.width - pad, pad), androidx.compose.ui.geometry.Offset(pad, size.height - pad), s.width, StrokeCap.Round)
}

@Composable
fun CheckMarkIcon(modifier: Modifier = Modifier, color: Color) = Canvas(modifier) {
    val s = Stroke(width = size.minDimension * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(w * 0.28f, h * 0.52f)
        lineTo(w * 0.44f, h * 0.68f)
        lineTo(w * 0.74f, h * 0.34f)
    }, color, style = s)
}

@Composable
fun LanguageBottomSheet(
    visible: Boolean,
    current: AppLanguage,
    isDark: Boolean,
    title: String,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        net.syncswift.UpdateSystemBars(isDark = true)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // Prevent click-through
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(if (isDark) Color(0xFF161F30) else Color(0xFFFFFFFF))
                    .border(
                        BorderStroke(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0xFFE2E8F0)),
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                    )
                }

                // Header Row: Title & Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = AuthColors.textPrimary(isDark),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF222F43) else Color(0xFFF1F5F9))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        CloseXIcon(Modifier.size(12.dp), color = AuthColors.textSecondary(isDark))
                    }
                }

                // Language List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppLanguage.entries.forEachIndexed { index, option ->
                        val isSelected = option == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelect(option)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular code badge (e.g. TR, EN, DE) styled like the neutral back button
                            val badgeBg = if (isSelected) {
                                if (isDark) Color(0xFF222F43) else Color(0xFFE2E8F0)
                            } else {
                                if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            }
                            val badgeBorder = if (isSelected) {
                                if (isDark) Color(0x40FFFFFF) else Color(0xFFCBD5E1)
                            } else {
                                if (isDark) Color(0x1AFFFFFF) else Color(0xFFE2E8F0)
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(badgeBg)
                                    .border(BorderStroke(1.dp, badgeBorder), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.code.uppercase(),
                                    color = if (isSelected) AuthColors.textPrimary(isDark) else AuthColors.textSecondary(isDark),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Text(
                                text = option.nativeName,
                                color = if (isSelected) AuthColors.textPrimary(isDark) else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)),
                                fontSize = 15.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            // Checkmark styled neutral like back button (not saturated colored)
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF222F43) else Color(0xFFE2E8F0))
                                        .border(
                                            BorderStroke(1.dp, if (isDark) Color(0x40FFFFFF) else Color(0xFFCBD5E1)),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CheckMarkIcon(Modifier.size(13.dp), color = AuthColors.textPrimary(isDark))
                                }
                            }
                        }

                        if (index < AppLanguage.entries.size - 1) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = if (isDark) Color(0x14FFFFFF) else Color(0xFFF1F5F9)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
