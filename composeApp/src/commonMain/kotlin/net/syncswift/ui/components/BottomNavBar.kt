package net.syncswift.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import net.syncswift.ClipboardVectorIcon
import net.syncswift.Copy
import net.syncswift.HomeVectorIcon
import net.syncswift.TransferVectorIcon
import net.syncswift.UserIcon
import net.syncswift.ui.theme.*

enum class MainNavTab { Home, Transfers, ClipboardHistory, Account }

@Composable
fun BottomNavBar(
    selectedTab: MainNavTab,
    onTabSelected: (MainNavTab) -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Consume touch events so they don't fall through to screens beneath navbar */ }
            .background(CardSurface)
    ) {
        // Subtle top border divider
        HorizontalDivider(
            thickness = 1.dp,
            color = InputBorder.copy(alpha = 0.35f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 11.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home (en başta)
            NavBarPillItem(
                selected = selectedTab == MainNavTab.Home,
                label = copy.homeTab,
                icon = { color -> HomeVectorIcon(Modifier.size(22.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Home) }
            )

            // 2. Transfers
            NavBarPillItem(
                selected = selectedTab == MainNavTab.Transfers,
                label = copy.transfersTab,
                icon = { color -> TransferVectorIcon(Modifier.size(21.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Transfers) }
            )

            // 3. Clipboard History
            NavBarPillItem(
                selected = selectedTab == MainNavTab.ClipboardHistory,
                label = copy.clipboardHistoryTab,
                icon = { color -> ClipboardVectorIcon(Modifier.size(20.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.ClipboardHistory) }
            )

            // 4. Account
            NavBarPillItem(
                selected = selectedTab == MainNavTab.Account,
                label = copy.accountTab,
                icon = { color -> UserIcon(Modifier.size(21.dp), color = color) },
                onClick = { onTabSelected(MainNavTab.Account) }
            )
        }
    }
}

@Composable
private fun NavBarPillItem(
    selected: Boolean,
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemColor by animateColorAsState(
        targetValue = if (selected) PillButtonText else Mist,
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                if (selected) PillButtonBg else Color.Transparent,
                shape = CircleShape
            )
            .then(
                if (selected) Modifier.border(0.5.dp, PillButtonBorder.copy(alpha = 0.5f), CircleShape) else Modifier
            )
            .padding(
                horizontal = if (selected) 16.dp else 12.dp,
                vertical = 9.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon(itemColor)

            if (selected) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = label,
                    color = PillButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1
                )
            }
        }
    }
}

