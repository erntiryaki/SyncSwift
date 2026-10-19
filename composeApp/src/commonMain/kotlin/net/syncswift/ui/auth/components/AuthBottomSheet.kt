package net.syncswift.ui.auth.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.SheetSystemBars
import net.syncswift.ui.components.rememberSheetDragState
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun AuthBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String = "",
    titleIcon: (@Composable () -> Unit)? = null,
    isDark: Boolean = LocalSyncSwiftColors.current.isDark,
    content: @Composable ColumnScope.() -> Unit
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Handles system tray: dark while sheet is open, properly restored to black icons on light theme when closed!
    SheetSystemBars(active = visible, isDark = isDark)

    val dragState = rememberSheetDragState(visible = visible, onDismiss = onDismiss)

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
            val maxSheetHeight = maxHeight * 0.92f

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(200)) { it }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .then(dragState.sheetOffsetModifier)
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
                            .padding(horizontal = 24.dp)
                            .padding(top = 14.dp, bottom = 24.dp)
                    ) {
                        // Drag handle (with downward drag gesture support)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragState.dragHandleModifier)
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
                        if (title.isNotEmpty() || titleIcon != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(dragState.dragHandleModifier)
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
                                        if (title.isNotEmpty()) {
                                            Spacer(Modifier.width(10.dp))
                                        }
                                    }
                                    if (title.isNotEmpty()) {
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
                                        .clickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    net.syncswift.CrossVectorIcon(Modifier.size(12.dp), color = AuthColors.textSecondary(isDark))
                                }
                            }
                        }

                        // Body Content: Scrollable so action buttons NEVER get cut off or pushed offscreen!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            content()
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
