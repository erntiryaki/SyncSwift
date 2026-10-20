package net.syncswift.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.ClipboardVectorIcon
import net.syncswift.ClockHistoryVectorIcon
import net.syncswift.Copy
import net.syncswift.HomeVectorIcon
import net.syncswift.UserIcon
import net.syncswift.WorkspacesVectorIcon
import net.syncswift.ui.theme.LocalSyncSwiftColors

enum class MainNavTab { Home, ClipboardHistory, Workspaces, Account, Transfers }

@Composable
fun BottomNavBar(
    selectedTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val navBg = if (isDark) Color(0xFF0E1420) else Color(0xFFFFFFFF)
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
            .background(navBg)
    ) {
        // Top border divider
        HorizontalDivider(
            thickness = 0.75.dp,
            color = dividerColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Ana Sayfa (Home)
            NavBarItem(
                selected = selectedTab == MainNavTab.Home,
                label = copy.homeTab,
                icon = { color -> HomeVectorIcon(Modifier.size(22.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Home) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )

            // 2. Pano Geçmişi (Clipboard)
            NavBarItem(
                selected = selectedTab == MainNavTab.ClipboardHistory,
                label = copy.clipboardHistoryTab,
                icon = { color -> ClipboardVectorIcon(Modifier.size(21.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.ClipboardHistory) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )

            // 3. Çalışma Alanları (Workspaces - Şifreli Ortak Kasa)
            NavBarItem(
                selected = selectedTab == MainNavTab.Workspaces,
                label = copy.workspacesTab,
                icon = { color -> WorkspacesVectorIcon(Modifier.size(21.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Workspaces) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )

            // 4. Hesabım (Account)
            NavBarItem(
                selected = selectedTab == MainNavTab.Account,
                label = copy.accountTab,
                icon = { color -> UserIcon(Modifier.size(21.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Account) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavBarItem(
    selected: Boolean,
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = LocalSyncSwiftColors.current.blue
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val itemColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180)
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            icon(itemColor)
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = label,
            color = itemColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
