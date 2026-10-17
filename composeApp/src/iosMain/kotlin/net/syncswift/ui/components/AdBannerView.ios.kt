package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.SyncSwiftIcons
import net.syncswift.subscription.PaywallReason
import net.syncswift.subscription.SubscriptionManager
import net.syncswift.ui.theme.*

@Composable
actual fun AdBannerView(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurface)
                .border(1.dp, InputBorder.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickable {
                    SubscriptionManager.requestPaywall(PaywallReason.UPGRADE_BUTTON)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon pill
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Electric.copy(alpha = 0.22f),
                                Sky.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                SyncSwiftIcons.Lightning(
                    modifier = Modifier.size(20.dp),
                    tint = Electric
                )
            }

            Spacer(Modifier.width(12.dp))

            // Text column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SyncSwift Pro",
                        color = Cloud,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Navy.copy(alpha = 0.6f))
                            .border(0.8.dp, InputBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "AD",
                            color = Mist,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Sınırsız transfer & reklamsız deneyim",
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action button pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Electric)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keşfet",
                    color = Midnight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
