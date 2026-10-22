package net.syncswift.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.theme.LocalSyncSwiftColors

/**
 * Premium Desktop Sidebar Navigation replacing BottomNavBar on larger screens.
 */
@Composable
fun DesktopSidebar(
    selectedTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    onOpenConnectDesktop: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val bg = if (isDark) Color(0xFF0B101B) else Color(0xFFF8FAFC)
    val borderCol = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val brandBlue = LocalSyncSwiftColors.current.blue

    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(bg)
            .border(width = 1.dp, color = borderCol)
            .padding(vertical = 18.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // App Logo & Brand Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandIcon(modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "SyncSwift",
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = "Desktop Edition",
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Navigation Items
        SidebarNavItem(
            selected = selectedTab == MainNavTab.Home,
            label = copy.homeTab,
            icon = { col -> HomeVectorIcon(Modifier.size(20.dp), color = col) },
            onClick = { onTabSelected(MainNavTab.Home) },
            isDark = isDark
        )

        SidebarNavItem(
            selected = selectedTab == MainNavTab.Transfers,
            label = copy.transfersTab,
            icon = { col -> ClockHistoryVectorIcon(Modifier.size(20.dp), color = col) },
            onClick = { onTabSelected(MainNavTab.Transfers) },
            isDark = isDark
        )

        SidebarNavItem(
            selected = selectedTab == MainNavTab.ClipboardHistory,
            label = copy.clipboardHistoryTab,
            icon = { col -> ClipboardVectorIcon(Modifier.size(19.dp), color = col) },
            onClick = { onTabSelected(MainNavTab.ClipboardHistory) },
            isDark = isDark
        )

        SidebarNavItem(
            selected = selectedTab == MainNavTab.Workspaces,
            label = copy.workspacesTab,
            icon = { col -> WorkspacesVectorIcon(Modifier.size(20.dp), color = col) },
            onClick = { onTabSelected(MainNavTab.Workspaces) },
            isDark = isDark
        )

        SidebarNavItem(
            selected = selectedTab == MainNavTab.Account,
            label = copy.accountTab,
            icon = { col -> UserIcon(Modifier.size(19.dp), color = col) },
            onClick = { onTabSelected(MainNavTab.Account) },
            isDark = isDark
        )

        Spacer(Modifier.weight(1f))

        // Connect Mobile / QR Code Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                .clickable { onOpenConnectDesktop() }
                .padding(vertical = 11.dp, horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Cihaz Eşleştir",
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "QR veya 6 Haneli Kod",
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // System Settings & Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF161F30) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    fontSize = 13.sp,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = copy.settingsTitle,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SidebarNavItem(
    selected: Boolean,
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val activeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val activeColor = LocalSyncSwiftColors.current.blue
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val itemColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) activeBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon(itemColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = if (selected) (if (isDark) Color.White else Color(0xFF0F172A)) else itemColor,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
