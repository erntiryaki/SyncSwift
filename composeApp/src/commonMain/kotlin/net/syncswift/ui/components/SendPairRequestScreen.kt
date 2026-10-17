package net.syncswift.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.CrossVectorIcon
import net.syncswift.LaptopVectorIcon
import net.syncswift.PhoneVectorIcon
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.ui.theme.*

/**
 * Full-page screen for sending a connection request (NO MODAL).
 */
@Composable
fun SendPairRequestScreen(
    device: DiscoveredDevice,
    copy: Copy,
    onSendRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanName = cleanDeviceDisplayName(device.name)
    val isMobile = remember(device) {
        val os = device.osType
        val name = device.name.lowercase()
        os.equals("Android", ignoreCase = true) || os.equals("iOS", ignoreCase = true) ||
        name.contains("iphone") || name.contains("android") || name.contains("galaxy") ||
        name.contains("pixel") || name.contains("redmi") || name.contains("xiaomi") ||
        name.contains("huawei") || name.contains("samsung") || name.contains("oppo") ||
        name.contains("vivo") || name.contains("phone") || name.contains("ipad") ||
        name.contains("tablet")
    }
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

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
                    text = copy.connectionRequestTitle,
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
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Device Pulse Container
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Blue.copy(alpha = 0.12f))
                        .border(BorderStroke(1.5.dp, Blue.copy(alpha = 0.4f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMobile) {
                            PhoneVectorIcon(Modifier.size(36.dp), color = Color.White)
                        } else {
                            LaptopVectorIcon(Modifier.size(36.dp), color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = cleanName,
                    color = Cloud,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = copy.sendConnectionRequestPrompt.replace("%s", cleanName),
                    color = Mist,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(18.dp))

                // Network Status Badge
                val isRemote = net.syncswift.connection.ConnectionManager.isRemoteDevice(device)
                val statusText = if (device.isOnline) {
                    copy.connectedStatus
                } else if (isRemote) {
                    copy.outsideLocalNetwork
                } else {
                    copy.offlineSleeping
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputBg)
                        .border(BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${device.osType} · $statusText",
                        color = Cloud,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // BOTTOM ACTIONS: İstek Gönder (Send Request) & Vazgeç (Cancel)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSendRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PillButtonBg,
                        contentColor = PillButtonText
                    )
                ) {
                    Text(
                        text = copy.sendConnectionRequestAction,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InputBg,
                        contentColor = Mist
                    ),
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = copy.otpCancelAction,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
