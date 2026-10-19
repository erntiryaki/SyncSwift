package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.CrownVectorIcon
import net.syncswift.subscription.SubscriptionManager.ProSubscriptionDates
import net.syncswift.ui.theme.Cloud
import net.syncswift.ui.theme.Mist

@Composable
fun ProActiveSheetContent(
    dates: ProSubscriptionDates,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üstte büyük yuvarlak içinde taç
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF59E0B).copy(alpha = 0.24f),
                            Color(0xFFF59E0B).copy(alpha = 0.08f)
                        )
                    )
                )
                .border(2.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CrownVectorIcon(
                modifier = Modifier.size(40.dp),
                color = Color(0xFFFBBF24)
            )
        }

        Spacer(Modifier.height(18.dp))

        // Altında Zaten Pro Abonesiniz
        Text(
            text = copy.alreadyProTitle.removeSuffix(".").ifBlank { "Zaten Pro Abonesiniz" },
            color = Cloud,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        // Altında başlangıç tarihi
        if (dates.startDateFormatted.isNotBlank()) {
            Text(
                text = copy.proStartDateLabel.replace("%s", dates.startDateFormatted),
                color = Mist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
        }

        // Altında bitiş tarihi (x gün kaldı)
        Text(
            text = copy.proRenewalDateLabel
                .replace("%s", dates.renewalDateFormatted)
                .replace("%d", dates.daysLeft.coerceAtLeast(0).toString()),
            color = Mist,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
