package net.syncswift.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.ui.theme.*

/**
 * Full-page, minimalist incoming pairing screen (NO MODAL).
 * Displays incoming connection request with Accept & Decline actions.
 */
@Composable
fun IncomingPairScreen(
    request: IncomingPairRequest,
    copy: Copy,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isMobile = remember(request) {
        val os = request.senderOsType
        val name = request.senderName.lowercase()
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

                // Close / Decline Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable { onDecline() },
                    contentAlignment = Alignment.Center
                ) {
                    CrossVectorIcon(Modifier.size(16.dp), color = Mist)
                }
            }

            // CENTER CONTENT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
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
                    text = request.senderName,
                    color = Cloud,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "${request.senderName} ${copy.wantsToConnect}",
                    color = Mist,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(18.dp))

                // OS & Connection Info Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputBg)
                        .border(BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = request.senderOsType,
                        color = Cloud,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // BOTTOM ACTIONS: Onayla (Accept) & Reddet (Decline)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAccept,
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
                        text = copy.accept,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDecline,
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
                        text = copy.decline,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
