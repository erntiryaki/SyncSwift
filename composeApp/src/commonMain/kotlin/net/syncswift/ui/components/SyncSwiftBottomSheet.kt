package net.syncswift.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.syncswift.CrossVectorIcon
import net.syncswift.SheetSystemBars
import net.syncswift.ui.auth.components.AuthColors
import net.syncswift.ui.theme.*

/**
 * Universal, flat, elegant SyncSwift Bottom Sheet.
 * 100% identical to AuthBottomSheet in visual appearance, backdrop darkness, opening/closing animations,
 * status bar styling, and layout structure.
 * Pure Compose overlay - zero native Dialog windows, zero top-right animations, zero gesture glitches.
 */
@Composable
fun SyncSwiftBottomSheet(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    title: String? = null,
    titleIcon: (@Composable () -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmColor: Color = PillButtonBg,
    confirmTextColor: Color = Cloud,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    isDark: Boolean = LocalSyncSwiftColors.current.isDark,
    content: @Composable ColumnScope.() -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        isVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val handleDismiss: () -> Unit = {
        scope.launch {
            isVisible = false
            delay(180)
            onDismissRequest()
        }
    }

    // Handles system tray: dark while sheet is open, properly restored to black icons on light theme when closed!
    SheetSystemBars(active = isVisible, isDark = isDark)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = handleDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val maxSheetHeight = maxHeight * 0.92f

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(200)) { it }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = false
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
                            .padding(horizontal = 24.dp)
                            .padding(top = 14.dp, bottom = 24.dp)
                    ) {
                        // Visual Handle Indicator (Clean pill indicator, no swipe gestures)
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

                        // Header Row (Icon + Title + Close Button)
                        if (!title.isNullOrEmpty() || titleIcon != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (titleIcon != null) {
                                        titleIcon()
                                        if (!title.isNullOrEmpty()) {
                                            Spacer(Modifier.width(10.dp))
                                        }
                                    }
                                    if (!title.isNullOrEmpty()) {
                                        Text(
                                            text = title,
                                            color = AuthColors.textPrimary(isDark),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.3).sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF222F43) else Color(0xFFF1F5F9))
                                        .clickable { handleDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    CrossVectorIcon(Modifier.size(12.dp), color = AuthColors.textSecondary(isDark))
                                }
                            }
                        }

                        // Body Content (Scrollable so tall modals NEVER get cut off!)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            content()
                        }

                        // Actions Footer (Confirm / Dismiss - ALWAYS visible and never pushed below layout!)
                        if (confirmText != null || dismissText != null || onConfirm != null || onDismiss != null) {
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (dismissText != null) {
                                    TextButton(
                                        onClick = {
                                            if (onDismiss != null) onDismiss() else handleDismiss()
                                        },
                                        enabled = !confirmLoading
                                    ) {
                                        Text(
                                            text = dismissText,
                                            color = AuthColors.textSecondary(isDark),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                if (confirmText != null && onConfirm != null) {
                                    Button(
                                        onClick = onConfirm,
                                        enabled = confirmEnabled && !confirmLoading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = confirmColor,
                                            contentColor = confirmTextColor
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        if (confirmLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = confirmTextColor,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = confirmText,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
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
