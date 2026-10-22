package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.NotificationBellIcon
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun DesktopTopBar(
    deviceName: String,
    localIp: String,
    connectedCount: Int,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val bg = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val borderCol = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(bg)
            .border(width = 0.5.dp, color = borderCol)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Device status chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = deviceName,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (localIp.isNotBlank()) {
                Text(
                    text = " ($localIp)",
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Connected devices pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isDark) Color(0xFF161F30) else Color(0xFFF8FAFC))
                .border(0.5.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "$connectedCount Cihaz Bağlı",
                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.weight(1f))

        // Notification Bell Icon with Badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                .clickable { onOpenNotifications() },
            contentAlignment = Alignment.Center
        ) {
            NotificationBellIcon(
                modifier = Modifier.size(17.dp),
                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
            )
            if (unreadNotificationCount > 0) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}
