package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.syncswift.Copy
import net.syncswift.CrossVectorIcon
import net.syncswift.DevicePlatformIcon
import net.syncswift.TransferVectorIcon
import net.syncswift.ClipboardVectorIcon
import net.syncswift.DocumentVectorIcon
import net.syncswift.FileImageIcon
import net.syncswift.FilePdfIcon
import net.syncswift.FileZipIcon
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.ui.theme.*

/**
 * Full-page screen for choosing a target device to send a file to (NO MODAL).
 */
@Composable
fun FileDeviceSelectScreen(
    fileName: String,
    fileSizeFormatted: String,
    devices: List<DiscoveredDevice>,
    copy: Copy,
    isDeviceConnected: (DiscoveredDevice) -> Boolean,
    onSelectDevice: (DiscoveredDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val loggedInEmail = net.syncswift.SessionManager.getLoggedInEmail()
    var registeredAccountDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    LaunchedEffect(loggedInEmail) {
        if (!loggedInEmail.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                try {
                    registeredAccountDevices = net.syncswift.SupabaseNetwork.fetchAllUserDevices(loggedInEmail)
                } catch (_: Exception) {}
            }
        }
    }
    val targetDevices = remember(devices) {
        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
        devices.filter { dev ->
            !dev.isSelf &&
            (self == null || (dev.host != self.host && cleanDeviceDisplayName(dev.name) != cleanDeviceDisplayName(self.name)))
        }.distinctBy { it.host to cleanDeviceDisplayName(it.name) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copy.selectDeviceToSend,
                    color = Cloud,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )

                // Close Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    CrossVectorIcon(Modifier.size(16.dp), color = Mist)
                }
            }

            Spacer(Modifier.height(24.dp))

            // FILE PREVIEW CARD (Modernized with dynamic file-type iconography)
            val isClipboard = fileName.startsWith("${copy.clipboard}:") ||
                    fileName.startsWith("Pano:") ||
                    fileName.startsWith("Clipboard:") ||
                    fileSizeFormatted == copy.clipboard ||
                    fileSizeFormatted.contains("chars", ignoreCase = true) ||
                    fileSizeFormatted.contains("karakter", ignoreCase = true)

            val ext = if (fileName.contains(".") && !isClipboard) fileName.substringAfterLast(".").lowercase() else ""
            val isImage = !isClipboard && listOf("png", "jpg", "jpeg", "webp", "gif", "heic", "bmp", "svg").contains(ext)
            val isPdf = !isClipboard && ext == "pdf"
            val isZip = !isClipboard && listOf("zip", "rar", "7z", "tar", "gz", "apk").contains(ext)

            val themeBlue = LocalSyncSwiftColors.current.blue
            val (accentColor, badgeLabel) = when {
                isClipboard -> Color(0xFF10B981) to copy.clipboard.uppercase()
                isPdf -> Color(0xFFEF4444) to "PDF"
                isZip -> Color(0xFFF59E0B) to "ZIP"
                isImage -> Color(0xFF6366F1) to (ext.uppercase().ifBlank { "IMG" })
                else -> themeBlue to (ext.uppercase().ifBlank { "FILE" })
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isClipboard -> ClipboardVectorIcon(Modifier.size(24.dp), color = accentColor)
                            isPdf -> FilePdfIcon(Modifier.size(32.dp))
                            isZip -> FileZipIcon(Modifier.size(32.dp))
                            isImage -> FileImageIcon(Modifier.size(32.dp))
                            else -> DocumentVectorIcon(Modifier.size(24.dp), color = accentColor)
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isClipboard) fileName.removePrefix("${copy.clipboard}: ").removePrefix("Pano: ").removePrefix("Clipboard: ").trim() else fileName,
                                color = Cloud,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isClipboard) copy.sendClipboardAction else fileSizeFormatted,
                            color = Mist,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = copy.devicesSectionTitle,
                color = Cloud,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            if (targetDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = copy.noDevicesOnNetwork,
                        color = Mist,
                        fontSize = 14.sp
                    )
                }
            } else {
                val isDark = LocalSyncSwiftColors.current.isDark
                val blue = LocalSyncSwiftColors.current.blue
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(targetDevices) { dev ->
                        val connected = isDeviceConnected(dev)
                        val isDesktop = dev.osType.equals("Desktop", ignoreCase = true) ||
                                dev.osType.equals("Windows", ignoreCase = true) ||
                                dev.osType.equals("macOS", ignoreCase = true) ||
                                dev.name.lowercase().let { it.contains("windows") || it.contains("mac") || it.contains("desktop") || it.contains("pc") }
                        val isMyDevice = registeredAccountDevices.any {
                            it.id == dev.id || (it.host.isNotBlank() && it.host == dev.host && it.name == dev.name)
                        } || (isDesktop && connected)

                        val badgeColor = if (isMyDevice || connected) blue else Mist

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(122.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectDevice(dev) },
                            shape = RoundedCornerShape(18.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, if (connected || isMyDevice) badgeColor.copy(alpha = 0.5f) else InputBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                // Online Green / Offline Gray Status Dot
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (dev.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                                )

                                // Centered circular icon container and device labels
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 4.dp, bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(if (connected || isMyDevice) badgeColor.copy(alpha = 0.15f) else (if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DevicePlatformIcon(
                                            name = dev.name,
                                            osType = dev.osType,
                                            modelCode = dev.modelCode,
                                            color = if (connected || isMyDevice) badgeColor else blue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = cleanDeviceDisplayName(dev.name),
                                        color = Cloud,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isMyDevice) copy.myDeviceLabel else (if (connected) copy.connectedDeviceLabel else dev.osType),
                                        color = if (isMyDevice || connected) badgeColor else Mist,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
