package net.syncswift.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.syncswift.*
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.ui.theme.*

/**
 * Bottom Sheet for Device Actions matching the screenshot:
 * 1. Main Action Sheet (Dosya Gönder, Ortak Pano, Bağlantıyı Kes, Cihazı Düzenle, Cihazı Kaldır)
 * 2. Disconnect Confirmation Sheet (Broken Link icon, "Bağlantıyı Kes", "İptal")
 */
@Composable
fun ConnectedDeviceActionScreen(
    device: DiscoveredDevice,
    copy: Copy,
    isConnected: Boolean = net.syncswift.connection.ConnectionManager.isDeviceConnected(device),
    onSendFile: () -> Unit,
    onSendClipboard: () -> Unit = {},
    onSendConnectionRequest: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }

    val cleanName = cleanDeviceDisplayName(device.name)
    val isDark = LocalSyncSwiftColors.current.isDark
    val isMobile = device.osType.equals("Android", ignoreCase = true) ||
            device.osType.equals("iOS", ignoreCase = true) ||
            device.name.contains("Phone", ignoreCase = true) ||
            device.name.contains("Mobile", ignoreCase = true)

    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }

    DisposableEffect(isVisible) {
        if (isVisible) {
            AdBannerManager.incrementModalCount()
            onDispose {
                AdBannerManager.decrementModalCount()
            }
        } else {
            onDispose {}
        }
    }

    val handleDismiss: () -> Unit = {
        scope.launch {
            isVisible = false
            kotlinx.coroutines.delay(180)
            onDismiss()
        }
    }

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
                    .clickable(onClick = handleDismiss),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxSheetHeight = maxHeight * 0.92f

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                            .clickable(enabled = false) {}, // Prevent click through
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
                                .padding(top = 14.dp, bottom = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Pill indicator
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

                            Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = showDisconnectConfirmation,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(180))
                    }
                ) { isConfirmingDisconnect ->
                    if (!isConfirmingDisconnect) {
                        // SCREEN 1: DEVICE ACTION MENU
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Device Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Device Icon
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DevicePlatformIcon(
                                            name = cleanName,
                                            osType = device.osType,
                                            modelCode = device.modelCode,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = cleanName,
                                            color = Cloud,
                                            fontSize = 17.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.3).sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (device.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                                            )
                                            val statusLabel = when {
                                                isConnected -> copy.statusConnected
                                                device.isOnline -> copy.nearbyDevicesSection
                                                else -> copy.statusOffline
                                            }
                                            Text(
                                                text = "$statusLabel • ${device.osType.ifBlank { "SyncSwift" }}",
                                                color = Mist,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                // Close Button (X in circle)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CrossVectorIcon(Modifier.size(12.dp), color = Mist)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            HorizontalDivider(thickness = 0.6.dp, color = InputBorder)

                            Spacer(Modifier.height(8.dp))

                            if (!isConnected) {
                                // Yakındaki Cihaz (Henüz bağlanmamış): Bağlantı İsteği Gönder & Dosya Gönder
                                DeviceActionItemRow(
                                    icon = { LinkChainIcon(Modifier.size(20.dp), color = LocalSyncSwiftColors.current.blue) },
                                    title = copy.sendConnectionRequestAction,
                                    subtitle = copy.sendConnectionRequestActionDesc,
                                    isDestructive = false,
                                    onClick = onSendConnectionRequest
                                )

                                DeviceActionItemRow(
                                    icon = { PaperAirplaneIcon(Modifier.size(20.dp), color = LocalSyncSwiftColors.current.blue) },
                                    title = copy.sendFileAction,
                                    subtitle = copy.sendFileActionDesc,
                                    isDestructive = false,
                                    onClick = onSendFile
                                )
                            } else {
                                // Bağlanmış Cihaz: Dosya Gönder, Pano Gönder, Bağlantıyı Kes, Cihazı Düzenle
                                DeviceActionItemRow(
                                    icon = { PaperAirplaneIcon(Modifier.size(20.dp), color = LocalSyncSwiftColors.current.blue) },
                                    title = copy.sendFileAction,
                                    subtitle = copy.sendFileActionDesc,
                                    isDestructive = false,
                                    onClick = onSendFile
                                )

                                DeviceActionItemRow(
                                    icon = { ClipboardVectorIcon(Modifier.size(20.dp), color = LocalSyncSwiftColors.current.blue) },
                                    title = copy.sendClipboardActionTitle,
                                    subtitle = copy.sendClipboardActionDesc,
                                    isDestructive = false,
                                    onClick = onSendClipboard
                                )

                                DeviceActionItemRow(
                                    icon = { LinkChainIcon(Modifier.size(20.dp), color = Cloud) },
                                    title = copy.disconnectDeviceAction,
                                    subtitle = copy.disconnectDeviceActionDesc,
                                    isDestructive = false,
                                    onClick = onDisconnect
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                        }
                    } else {
                        // SCREEN 2: DISCONNECT CONFIRMATION (Phone 2 in Screenshot)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Row with Close Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .clickable { showDisconnectConfirmation = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    CrossVectorIcon(Modifier.size(12.dp), color = Mist)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Broken Link Red Glow Circular Container
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                BrokenLinkIcon(Modifier.size(36.dp), color = Color(0xFFEF4444))
                            }

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = copy.disconnectDeviceAction,
                                color = Cloud,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = copy.disconnectConfirmQuestion.replace("%s", cleanName),
                                color = Mist,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(Modifier.height(24.dp))

                            // Primary Red Button: Bağlantıyı Kes
                            Button(
                                onClick = onDisconnect,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = copy.disconnectDeviceAction,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Secondary Button: İptal
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showDisconnectConfirmation = false },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = copy.otpCancelAction,
                                        color = Cloud,
                                        fontSize = 15.sp,
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
}
}

@Composable
private fun DeviceActionItemRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isDestructive) Color(0xFFEF4444) else Cloud,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Mist,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(Modifier.width(8.dp))

        ChevronRightIcon(
            Modifier.size(16.dp),
            color = if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.8f) else Mist
        )
    }
}
