package net.syncswift.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.syncswift.ui.theme.*

/**
 * Universal, ultra-minimalist, flat, elegant SyncSwift Bottom Sheet Modal.
 * Renders anchored to bottom, supports swipe-down to dismiss, never cuts off content or action buttons.
 * Guarantees proper status bar coloring and restoration on dismiss!
 */
@Composable
fun SyncSwiftModal(
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // Handles status bar: dark while modal is open, restored to theme (black icons on light theme) on close!
        SheetSystemBars(active = true, isDark = isDark)

        val dragState = rememberSheetDragState(onDismiss = onDismissRequest)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val maxSheetHeight = maxHeight * 0.86f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .then(dragState.sheetOffsetModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {}, // Prevent backdrop clicks through sheet
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    // Sleek centered drag handle (with downward drag gesture support)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragState.dragHandleModifier)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Mist.copy(alpha = 0.45f))
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Header (Icon + Title)
                    if (title != null || titleIcon != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragState.dragHandleModifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (titleIcon != null) {
                                titleIcon()
                                Spacer(Modifier.width(12.dp))
                            }
                            if (title != null) {
                                Text(
                                    text = title,
                                    color = Cloud,
                                    fontSize = 18.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
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
                    if (onConfirm != null || onDismiss != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissText != null && onDismiss != null) {
                                TextButton(
                                    onClick = onDismiss,
                                    enabled = !confirmLoading
                                ) {
                                    Text(
                                        text = dismissText,
                                        color = Mist,
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

                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
