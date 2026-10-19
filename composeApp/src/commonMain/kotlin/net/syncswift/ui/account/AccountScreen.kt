package net.syncswift.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.ui.theme.*

@Composable
fun AccountScreen(
    copy: Copy,
    userEmail: String,
    firstName: String,
    lastName: String,
    provider: IdentityProvider,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelpSupport: () -> Unit,
    onLogout: () -> Unit,
    onSignInRequested: () -> Unit = {},
    onOpenConnectedDevices: () -> Unit = {},
    onOpenConnectDesktop: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onToggleTheme: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showProModal by remember { mutableStateOf(false) }
    var showAboutModal by remember { mutableStateOf(false) }

    val isDark = LocalSyncSwiftColors.current.isDark
    val softBlue = LocalSyncSwiftColors.current.blue
    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
    val isLocal = provider == IdentityProvider.Local || userEmail.isBlank()

    val displayName = remember(firstName, lastName, isLocal) {
        if (isLocal) copy.localUser
        else {
            val full = "$firstName $lastName".trim()
            if (full.isNotBlank()) full else userEmail.substringBefore("@")
        }
    }
    val effectiveEmail = remember(userEmail, isLocal) {
        if (isLocal) copy.localUserSubtitle
        else userEmail
    }

    val iconColor = if (isDark) softBlue else Color(0xFF1E293B)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(14.dp))

            // 1. Top Header Row: Page Title & Settings Gear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = copy.myAccount,
                    color = Cloud,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLocal, onClick = onOpenEditProfile)
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    SettingsIcon(Modifier.size(24.dp), color = Cloud)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Real User Profile Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !isLocal, onClick = onOpenEditProfile),
                shape = RoundedCornerShape(18.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, InputBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = if (isDark) listOf(
                                    Color(0xFF131B28),
                                    Color(0xFF182844).copy(alpha = 0.65f)
                                ) else listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFF1F5F9).copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // Translucent layered organic waves covering the bottom-right of header card
                    ProfileCardLayeredWaves(
                        isDark = isDark,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(18.dp))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayName,
                                color = Cloud,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )

                            // Pro or Free Badge Pill
                            if (isPro) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(softBlue)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CrownVectorIcon(Modifier.size(13.dp), color = Color(0xFFFBBF24))
                                        Text(
                                            text = copy.proBadge,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CrownVectorIcon(Modifier.size(13.dp), color = Color(0xFFFBBF24))
                                        Text(
                                            text = copy.freePlan,
                                            color = if (isDark) Color.White else Color(0xFF334155),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = effectiveEmail,
                            color = Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = copy.accountSubtitle,
                                color = Mist,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isLocal) {
                                Spacer(Modifier.width(8.dp))
                                ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3. Pro Tier Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                            net.syncswift.subscription.PaywallReason.UPGRADE_BUTTON
                        )
                    },
                shape = RoundedCornerShape(18.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, InputBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isDark) Color(0xFF78350F).copy(alpha = 0.35f)
                                else Color(0xFFFEF3C7)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CrownVectorIcon(Modifier.size(22.dp), color = Color(0xFFF59E0B))
                    }

                    Spacer(Modifier.width(14.dp))

                    val dates = remember(isPro) {
                        net.syncswift.subscription.SubscriptionManager.getProSubscriptionDates()
                    }
                    val proSubtitle = if (isPro) {
                        if (dates.renewalDateFormatted.isNotBlank() && dates.daysLeft >= 0) {
                            copy.proRenewalDateLabel
                                .replace("%s", dates.renewalDateFormatted)
                                .replace("%d", dates.daysLeft.toString())
                        } else {
                            copy.proActiveDescription
                        }
                    } else {
                        copy.upgradeToProDesc
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isPro) "SyncSwift Pro" else copy.upgradeToPro,
                            color = Cloud,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = proSubtitle,
                            color = Mist,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. ORIGINAL REAL MENUS (Settings removed since it is in the header)
            // 1. Connected Devices
            AccountMenuItemRow(
                icon = { DevicesIcon(Modifier.size(22.dp), color = iconColor) },
                title = copy.connectedDevicesMenu,
                subtitle = copy.connectedDevicesMenuDesc,
                onClick = onOpenConnectedDevices
            )

            // 3. Connect Desktop (My Devices)
            AccountMenuItemRow(
                icon = { DesktopVectorIcon(Modifier.size(22.dp), color = iconColor) },
                title = copy.myDevicesMenu,
                subtitle = copy.myDevicesMenuDesc,
                onClick = onOpenConnectDesktop
            )

            // 4. Notifications
            AccountMenuItemRow(
                icon = { BellIcon(Modifier.size(22.dp), color = iconColor) },
                title = copy.notificationsMenu,
                subtitle = copy.notificationsMenuDesc,
                onClick = onOpenNotifications
            )

            // 5. Help & Support
            AccountMenuItemRow(
                icon = { HelpIcon(Modifier.size(22.dp), color = iconColor) },
                title = copy.helpSupportMenu,
                subtitle = copy.helpSupportMenuDesc,
                onClick = onOpenHelpSupport
            )

            // 6. About SyncSwift
            AccountMenuItemRow(
                icon = { InfoIcon(Modifier.size(22.dp), color = iconColor) },
                title = copy.aboutMenu,
                subtitle = copy.aboutMenuDesc,
                onClick = { showAboutModal = true }
            )

            Spacer(Modifier.height(16.dp))

            // 5. Bottom Action: Sign In for Local User OR Sign Out for Logged-In User
            if (isLocal) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onSignInRequested),
                    shape = RoundedCornerShape(16.dp),
                    color = softBlue.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, softBlue.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoginIcon(Modifier.size(22.dp), color = softBlue)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = copy.signIn,
                                color = softBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = copy.localSignInCta,
                                color = Mist,
                                fontSize = 12.sp
                            )
                        }
                        ChevronRightIcon(Modifier.size(16.dp), color = softBlue)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showLogoutDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF261214) else Color(0xFFFEF2F2),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFECACA)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LogoutIcon(Modifier.size(22.dp), color = Color(0xFFEF4444))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = copy.logout,
                            color = Color(0xFFEF4444),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            modifier = Modifier.weight(1f)
                        )
                        ChevronRightIcon(Modifier.size(16.dp), color = Color(0xFFEF4444))
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }

        if (!isPro) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Logout Confirmation Modal
    if (showLogoutDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showLogoutDialog = false },
            title = copy.logoutConfirmTitle,
            titleIcon = { LogoutIcon(Modifier.size(20.dp), color = ErrorRed) },
            confirmText = copy.logout,
            confirmColor = ErrorRed,
            confirmTextColor = Color.White,
            onConfirm = {
                showLogoutDialog = false
                val devId = SessionManager.getPlatformDeviceId()
                scope.launch {
                    net.syncswift.SupabaseNetwork.clearDeviceUser(devId)
                }
                SessionManager.clearSession()
                net.syncswift.subscription.SubscriptionManager.setProState(false)
                showNativeToast(copy.logoutSuccess)
                onLogout()
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showLogoutDialog = false }
        ) {
            Text(
                text = copy.logoutConfirmDesc,
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }

    // About SyncSwift Modal
    if (showAboutModal) {
        AboutModalSheet(
            copy = copy,
            onDismiss = { showAboutModal = false }
        )
    }
}

@Composable
private fun AccountMenuItemRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cloud,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Mist,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(Modifier.width(8.dp))

        ChevronRightIcon(Modifier.size(16.dp), color = Mist)
    }
}

@Composable
private fun AboutModalSheet(
    copy: Copy,
    onDismiss: () -> Unit
) {
    var devClicks by remember { mutableStateOf(0) }

    net.syncswift.ui.components.SyncSwiftModal(
        onDismissRequest = onDismiss,
        title = copy.aboutTitle,
        titleIcon = { InfoIcon(Modifier.size(22.dp), color = Blue) },
        confirmText = copy.close,
        confirmColor = PillButtonBg,
        confirmTextColor = PillButtonText,
        onConfirm = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        devClicks++
                        if (devClicks >= 5) {
                            devClicks = 0
                            val current = SessionManager.getSavedProStatus()
                            SessionManager.saveProStatus(!current)
                            net.syncswift.subscription.SubscriptionManager.setProState(!current)
                            showNativeToast(if (!current) copy.devModeProActivated else copy.devModeProDeactivated)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = copy.aboutVersionLabel,
                    color = Cloud,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1.0.0",
                    color = Mist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = InputBorder)

            Text(
                text = copy.aboutMissionDesc,
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ProfileCardLayeredWaves(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Organic translucent flowing waves emerging from the bottom-right
        // In dark mode: layered deep blue / navy waves
        // In light mode: layered soft blue / sky waves
        val waveColor1 = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.28f) else Color(0xFF60A5FA).copy(alpha = 0.16f)
        val waveColor2 = if (isDark) Color(0xFF2563EB).copy(alpha = 0.20f) else Color(0xFF3B82F6).copy(alpha = 0.12f)
        val waveColor3 = if (isDark) Color(0xFF4D90FE).copy(alpha = 0.15f) else Color(0xFF2563EB).copy(alpha = 0.08f)

        // Wave Layer 1 (Back / Deep)
        val path1 = Path().apply {
            moveTo(w * 0.15f, h)
            cubicTo(
                w * 0.38f, h * 0.52f,
                w * 0.65f, h * 0.88f,
                w, h * 0.15f
            )
            lineTo(w, h)
            close()
        }
        drawPath(path1, waveColor1)

        // Wave Layer 2 (Middle)
        val path2 = Path().apply {
            moveTo(w * 0.32f, h)
            cubicTo(
                w * 0.52f, h * 0.68f,
                w * 0.74f, h * 0.38f,
                w, h * 0.42f
            )
            lineTo(w, h)
            close()
        }
        drawPath(path2, waveColor2)

        // Wave Layer 3 (Foreground crest)
        val path3 = Path().apply {
            moveTo(w * 0.48f, h)
            cubicTo(
                w * 0.65f, h * 0.80f,
                w * 0.82f, h * 0.62f,
                w, h * 0.68f
            )
            lineTo(w, h)
            close()
        }
        drawPath(path3, waveColor3)
    }
}

