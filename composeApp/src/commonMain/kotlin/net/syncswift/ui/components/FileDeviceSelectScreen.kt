package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.CrossVectorIcon
import net.syncswift.LaptopVectorIcon
import net.syncswift.PhoneVectorIcon
import net.syncswift.TransferVectorIcon
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

            // FILE PREVIEW CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Blue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        TransferVectorIcon(Modifier.size(22.dp), color = Blue)
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            color = Cloud,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = fileSizeFormatted,
                            color = Mist,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = copy.availableDevicesSection,
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(targetDevices) { dev ->
                        val connected = isDeviceConnected(dev)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectDevice(dev) },
                            shape = RoundedCornerShape(14.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, if (connected) SuccessGreen.copy(alpha = 0.4f) else InputBorder.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (connected) SuccessGreen.copy(alpha = 0.15f) else InputBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isMobile = dev.osType.equals("Android", ignoreCase = true) ||
                                            dev.osType.equals("iOS", ignoreCase = true) ||
                                            dev.name.lowercase().let { n ->
                                                n.contains("iphone") || n.contains("android") || n.contains("galaxy") ||
                                                n.contains("pixel") || n.contains("redmi") || n.contains("xiaomi") ||
                                                n.contains("huawei") || n.contains("samsung") || n.contains("oppo") ||
                                                n.contains("vivo") || n.contains("phone") || n.contains("ipad") ||
                                                n.contains("tablet")
                                            }
                                    if (isMobile) {
                                        PhoneVectorIcon(Modifier.size(20.dp), color = if (connected) SuccessGreen else Mist)
                                    } else {
                                        LaptopVectorIcon(Modifier.size(20.dp), color = if (connected) SuccessGreen else Mist)
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cleanDeviceDisplayName(dev.name),
                                        color = Cloud,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val devStatus = if (dev.isOnline) copy.deviceOnline else copy.outsideLocalNetwork
                                    Text(
                                        text = if (connected) copy.connectedStatus else "${dev.osType} · $devStatus",
                                        color = if (connected) SuccessGreen else Mist,
                                        fontSize = 12.sp
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
