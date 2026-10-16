package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.syncswift.ui.theme.*

/**
 * Universal, ultra-minimalist, flat, elegant SyncSwift Modal Dialog.
 * Zero neon glow, zero heavy shadows, perfectly adapted to Dark & Light themes.
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header (Icon + Title)
                if (title != null || titleIcon != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Body Content
                content()

                // Actions Footer (Confirm / Dismiss)
                if (onConfirm != null || onDismiss != null) {
                    Spacer(Modifier.height(20.dp))
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
                                    fontWeight = FontWeight.Medium
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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
