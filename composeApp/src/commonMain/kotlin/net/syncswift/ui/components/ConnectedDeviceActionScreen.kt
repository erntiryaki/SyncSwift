package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Full-page screen for connected device actions (NO MODAL).
 * Allows sending files, syncing clipboard, or disconnecting.
 */
@Composable
fun ConnectedDeviceActionScreen(
    device: DiscoveredDevice,
    copy: Copy,
    onSendFile: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanName = cleanDeviceDisplayName(device.name)
    val isMobile = device.osType.equals("Android", ignoreCase = true) ||
                   device.osType.equals("iOS", ignoreCase = true) ||
                   device.name.contains("Phone", ignoreCase = true) ||
                   device.name.contains("Mobile", ignoreCase = true)

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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cleanName,
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

            // CENTER CONTENT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.12f))
                        .border(BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.4f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMobile) {
                            PhoneVectorIcon(Modifier.size(34.dp), color = Color.White)
                        } else {
                            LaptopVectorIcon(Modifier.size(34.dp), color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = cleanName,
                    color = Cloud,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(8.dp))

                val statusText = if (device.isOnline) copy.connectedStatus else copy.outsideLocalNetwork
                Text(
                    text = "${device.osType} · $statusText",
                    color = Mist,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            // BOTTOM ACTIONS (Lifted up clearly above navbar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Dosya Gönder
                Button(
                    onClick = onSendFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PillButtonBg,
                        contentColor = PillButtonText
                    )
                ) {
                    TransferVectorIcon(Modifier.size(18.dp), color = PillButtonText)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = copy.send,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Bağlantıyı Kes
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InputBg,
                        contentColor = ErrorRed
                    ),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = copy.disconnect,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
