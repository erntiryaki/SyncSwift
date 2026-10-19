package net.syncswift.ui.auth.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import net.syncswift.SheetSystemBars

@Composable
private fun CheckMarkIcon(modifier: Modifier = Modifier, color: Color) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.16f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(w * 0.22f, h * 0.52f)
        lineTo(w * 0.44f, h * 0.74f)
        lineTo(w * 0.80f, h * 0.28f)
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
    // Handles system tray: dark while sheet is open, properly restored to black icons on light theme when closed!
    SheetSystemBars(active = visible, isDark = isDark)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            val maxSheetHeight = maxHeight * 0.86f

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(200)) { it }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = if (isDark) Color(0xFF161F30) else Color(0xFFFFFFFF),
                    border = BorderStroke(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0xFFE2E8F0)),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        // Visual pill indicator
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
                                net.syncswift.CrossVectorIcon(Modifier.size(12.dp), color = AuthColors.textSecondary(isDark))
                            }
                        }

                        // Language List: Scrollable so tall lists never get cut off!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
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

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
