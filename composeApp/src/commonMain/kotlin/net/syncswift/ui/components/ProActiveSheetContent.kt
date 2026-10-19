package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.subscription.SubscriptionManager.ProSubscriptionDates
import net.syncswift.ui.theme.*

@Composable
fun ProActiveSheetContent(
    dates: ProSubscriptionDates,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Radiant Pro Header Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF131B28),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF3B2306).copy(alpha = 0.65f),
                                Color(0xFF131B28)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gold Crown Icon in glowing box
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF78350F).copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CrownVectorIcon(Modifier.size(26.dp), color = Color(0xFFFBBF24))
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SyncSwift Pro",
                                color = Cloud,
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp
                            )
                            // "AKTİF" Pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SuccessGreen.copy(alpha = 0.20f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = copy.proBadge,
                                    color = SuccessGreen,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = copy.proActiveDescription,
                            color = Mist,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 2. Dates & Subscription Status Details
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Renewal / Expiry date & remaining days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = copy.proRenewalDateLabel
                                .replace("%s", dates.renewalDateFormatted)
                                .replace("%d", dates.daysLeft.coerceAtLeast(0).toString()),
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp
                        )
                        if (dates.startDateFormatted.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = copy.proStartDateLabel.replace("%s", dates.startDateFormatted),
                                color = Mist,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (dates.daysLeft >= 0) {
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Blue.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${dates.daysLeft}d",
                                color = Blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Pro Feature Privileges Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProFeatureMiniCard(
                icon = { DevicesIcon(Modifier.size(16.dp), color = Blue) },
                title = copy.connectedDevicesMenu,
                modifier = Modifier.weight(1f)
            )
            ProFeatureMiniCard(
                icon = { UploadIcon(Modifier.size(16.dp), color = SuccessGreen) },
                title = copy.transferFilesSubtitle,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProFeatureMiniCard(
                icon = { ClipboardVectorIcon(Modifier.size(16.dp), color = Color(0xFF8B5CF6)) },
                title = copy.clipboard,
                modifier = Modifier.weight(1f)
            )
            ProFeatureMiniCard(
                icon = { CrownVectorIcon(Modifier.size(16.dp), color = Color(0xFFFBBF24)) },
                title = copy.proBadge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProFeatureMiniCard(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = title,
                color = Cloud,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
