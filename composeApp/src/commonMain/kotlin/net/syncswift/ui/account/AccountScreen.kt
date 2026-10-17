package net.syncswift.ui.account

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.ui.components.LanguageControl
import net.syncswift.ui.components.rememberQrScanner
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
    onOpenNotifications: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showProModal by remember { mutableStateOf(false) }
    var showAboutModal by remember { mutableStateOf(false) }
    var showDesktopConnectModal by remember { mutableStateOf(false) }
    var desktopCodeInput by remember { mutableStateOf("") }
    var isDesktopConnecting by remember { mutableStateOf(false) }

    val scanDesktopQr = rememberQrScanner(
        onSuccess = { scannedText ->
            showDesktopConnectModal = false
            val clean = scannedText.trim()
            val extractedCode = when {
                clean.contains("code=") -> clean.substringAfter("code=", "").substringBefore("&").replace(" ", "").trim()
                clean.length == 6 && clean.all { it.isDigit() } -> clean
                clean.length == 7 && clean.contains(" ") -> clean.replace(" ", "")
                clean.matches(Regex(""".*\b(\d{3}\s?\d{3})\b.*""")) -> {
                    Regex("""\b(\d{3}\s?\d{3})\b""").find(clean)?.groupValues?.get(1)?.replace(" ", "")
                }
                else -> null
            }

            val urlWithoutScheme = clean.removePrefix("syncswift://").removePrefix("syncdrop://")
            val host = when {
                clean.contains("host=") -> clean.substringAfter("host=", "").substringBefore("&").trim()
                urlWithoutScheme.contains(":") -> urlWithoutScheme.substringBefore(":").trim()
                clean.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) -> clean
                else -> ""
            }
            val port = when {
                clean.contains("port=") -> clean.substringAfter("port=", "").substringBefore("&").toIntOrNull() ?: 52424
                urlWithoutScheme.contains(":") -> urlWithoutScheme.substringAfter(":").substringBefore("/").toIntOrNull() ?: 52424
                else -> 52424
            }
            val name = when {
                clean.contains("name=") -> clean.substringAfter("name=", "").substringBefore("&").replace("%20", " ").trim()
                else -> "Desktop"
            }
            val os = when {
                clean.contains("os=") -> clean.substringAfter("os=", "").substringBefore("&").trim()
                else -> "Desktop"
            }

            fun attemptDirectLocal() {
                if (host.isNotBlank() && host != "127.0.0.1" && host != "0.0.0.0") {
                    val dev = net.syncswift.discovery.DeviceDiscoveryManager.addManualDevice(host, port, name, os)
                    ConnectionManager.requestPairing(dev) { res ->
                        if (res == PairResult.ACCEPTED) {
                            showNativeToast(copy.connectionAccepted)
                        } else {
                            showNativeToast(copy.codeExpiredOrAlreadyUsed)
                        }
                    }
                } else {
                    showNativeToast(copy.codeExpiredOrAlreadyUsed)
                }
            }

            if (extractedCode != null && extractedCode.length == 6 && extractedCode.all { it.isDigit() }) {
                ConnectionManager.connectViaPairingCode(extractedCode) { res ->
                    when (res) {
                        PairResult.ACCEPTED -> {
                            showNativeToast(copy.connectionAccepted)
                        }
                        PairResult.PENDING_APPROVAL -> {
                            // Dialog is showing with Cancel button
                        }
                        PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                            attemptDirectLocal()
                        }
                    }
                }
            } else {
                attemptDirectLocal()
            }
        },
        onError = { err ->
            if (err != "Cancelled") {
                showNativeToast(err)
            }
        }
    )

    val isLocal = provider == IdentityProvider.Local || userEmail.isBlank()

    val displayName = remember(firstName, lastName, isLocal) {
        if (isLocal) copy.localUser
        else {
            val full = "$firstName $lastName".trim()
            if (full.isNotBlank()) full else ""
        }
    }

    val scrollState = rememberScrollState()

    var devTapCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(16.dp))

        // Top Navigation Bar: Page Title instead of Logo!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.myAccount,
                color = Cloud,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // BOXLESS Profile Header: Sola hizalı isim & e-posta, sağda Düzenle butonu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isLocal) {
                    Text(
                        text = copy.localUser,
                        color = Cloud,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = copy.localUserSubtitle,
                        color = Mist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            color = Cloud,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = userEmail,
                        color = Mist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!isLocal) {
                Spacer(Modifier.width(16.dp))

                // Düzenle Butonu (Minimalist Pill Kutusuz - Beyaz Yazı ve İkon)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PillButtonBg)
                        .clickable(onClick = onOpenEditProfile)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EditVectorIcon(Modifier.size(13.dp), color = Color.White)
                        Text(
                            text = copy.editProfile,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))


        // Divider below header
        HorizontalDivider(
            thickness = 1.dp,
            color = InputBorder.copy(alpha = 0.35f)
        )

        Spacer(Modifier.height(18.dp))

        val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()

        // Pro'ya Geç Banner / Row (Attention-Grabbing yet Flat & Minimalist)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                        net.syncswift.subscription.PaywallReason.UPGRADE_BUTTON
                    )
                },
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.2.dp, if (isPro) SuccessGreen.copy(alpha = 0.55f) else Blue.copy(alpha = 0.55f))
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
                        .background(if (isPro) SuccessGreen.copy(alpha = 0.15f) else Blue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    CrownVectorIcon(Modifier.size(24.dp), color = if (isPro) SuccessGreen else Blue)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPro) "SyncSwift Pro" else copy.upgradeToPro,
                            color = Cloud,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPro) SuccessGreen.copy(alpha = 0.2f) else Blue.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPro) copy.activeStatus else copy.proBadge,
                                color = if (isPro) SuccessGreen else Blue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    Text(
                        text = if (isPro) copy.proActiveDescription else copy.upgradeToProDesc,
                        color = Mist,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                ChevronRightIcon(Modifier.size(16.dp), color = if (isPro) SuccessGreen else Blue)
            }
        }

        Spacer(Modifier.height(14.dp))

        // BOXLESS Menu List with Divider Lines between items
        Column(modifier = Modifier.fillMaxWidth()) {
            // Settings Menu Item
            BoxlessMenuItem(
                title = copy.settingsMenu,
                subtitle = copy.settingsMenuDesc,
                icon = { SettingsIcon(Modifier.size(20.dp), color = Blue) },
                onClick = onOpenSettings
            )
            HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

            // Connected Devices
            val discoveredDevices by net.syncswift.discovery.DeviceDiscoveryManager.devices.collectAsState()
            BoxlessMenuItem(
                title = copy.connectedDevicesMenu,
                subtitle = copy.connectedDevicesMenuDesc,
                icon = { DevicesIcon(Modifier.size(20.dp), color = Mist) },
                onClick = onOpenConnectedDevices
            )
            HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

            // My Devices (Kendi Cihazlarım)
            BoxlessMenuItem(
                title = copy.myDevicesMenu,
                subtitle = copy.myDevicesMenuDesc,
                icon = { DesktopVectorIcon(Modifier.size(20.dp), color = Blue) },
                onClick = onOpenConnectDesktop
            )
            HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

            // Notifications
            BoxlessMenuItem(
                title = copy.notificationsMenu,
                subtitle = copy.notificationsMenuDesc,
                icon = { BellIcon(Modifier.size(20.dp), color = Mist) },
                onClick = onOpenNotifications
            )
            HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

            // Help & Support
            BoxlessMenuItem(
                title = copy.helpSupportMenu,
                subtitle = copy.helpSupportMenuDesc,
                icon = { HelpIcon(Modifier.size(20.dp), color = Mist) },
                onClick = onOpenHelpSupport
            )
            HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

            // About SyncSwift
            BoxlessMenuItem(
                title = copy.aboutMenu,
                subtitle = copy.aboutMenuDesc,
                icon = { InfoIcon(Modifier.size(20.dp), color = Mist) },
                onClick = { showAboutModal = true }
            )
            Spacer(Modifier.height(14.dp))

            if (isLocal) {
                // Giriş Yap Pill Frame
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onSignInRequested),
                    shape = RoundedCornerShape(22.dp),
                    color = Blue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Blue.copy(alpha = 0.40f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Blue.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            LoginIcon(Modifier.size(20.dp), color = Blue)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = copy.signIn,
                                color = Cloud,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = copy.localSignInCta,
                                color = Mist,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ChevronRightIcon(Modifier.size(15.dp), color = Blue.copy(alpha = 0.8f))
                    }
                }
            } else {
                // Çıkış Yap Pill Frame
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { showLogoutDialog = true },
                    shape = RoundedCornerShape(22.dp),
                    color = ErrorRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.38f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ErrorRed.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            LogoutIcon(Modifier.size(20.dp), color = ErrorRed)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = copy.logout,
                                color = ErrorRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = copy.logoutConfirmDesc,
                                color = Mist,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ChevronRightIcon(Modifier.size(15.dp), color = ErrorRed.copy(alpha = 0.8f))
                    }
                }
            }
        }

            Spacer(Modifier.height(24.dp))
        }

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // SyncSwift Pro Modal
    if (showProModal) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showProModal = false },
            title = copy.proModalTitle,
            titleIcon = { CrownVectorIcon(Modifier.size(24.dp), color = Blue) },
            confirmText = copy.comingSoon,
            confirmColor = PillButtonBg,
            confirmTextColor = PillButtonText,
            onConfirm = {
                showProModal = false
                showNativeToast(copy.comingSoon)
            },
            dismissText = copy.close,
            onDismiss = { showProModal = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProBenefitRow(text = copy.proBenefit1)
                ProBenefitRow(text = copy.proBenefit2)
                ProBenefitRow(text = copy.proBenefit3)
                ProBenefitRow(text = copy.proBenefit4)
                ProBenefitRow(text = copy.proBenefit5)
            }
        }
    }

    // Logout Confirmation Modal (Universal Flat Minimalist SyncSwiftModal)
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
                SessionManager.clearSession()
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

    // Modal: Masaüstü Uygulamasına Bağlan (Connect to Desktop App)
    if (showDesktopConnectModal) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showDesktopConnectModal = false },
            title = copy.connectToDesktop,
            titleIcon = { DesktopVectorIcon(Modifier.size(22.dp), color = Blue) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = copy.connectToDesktopDesc,
                    color = Mist,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                // Option 1: QR Kod Okut
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable {
                            showDesktopConnectModal = false
                            scanDesktopQr()
                        },
                    shape = RoundedCornerShape(26.dp),
                    color = PillButtonBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QrScanVectorIcon(Modifier.size(18.dp), color = PillButtonText)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = copy.scanDesktopQr,
                            color = PillButtonText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                Spacer(Modifier.height(18.dp))

                // Option 2: 6 Haneli Masaüstü Kodu Gir
                Text(
                    text = copy.enterDesktopCodePrompt,
                    color = Cloud,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InputBg)
                            .border(1.dp, InputBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = desktopCodeInput,
                            onValueChange = {
                                if (it.length <= 7) desktopCodeInput = it
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Cloud,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (desktopCodeInput.isEmpty()) {
                                    Text(
                                        text = copy.enter6DigitCodeHint,
                                        color = Mist.copy(alpha = 0.5f),
                                        fontSize = 16.sp,
                                        letterSpacing = 2.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = desktopCodeInput.trim().replace(" ", "").length == 6 && !isDesktopConnecting) {
                                isDesktopConnecting = true
                                val clean = desktopCodeInput.trim().replace(" ", "")
                                ConnectionManager.connectViaPairingCode(clean) { res ->
                                    isDesktopConnecting = false
                                    when (res) {
                                        PairResult.ACCEPTED -> {
                                            showDesktopConnectModal = false
                                            desktopCodeInput = ""
                                            showNativeToast(copy.connectionAccepted)
                                        }
                                        PairResult.PENDING_APPROVAL -> {
                                            showDesktopConnectModal = false
                                            desktopCodeInput = ""
                                        }
                                        PairResult.EXPIRED_OR_USED -> {
                                            showNativeToast(copy.codeExpiredOrAlreadyUsed)
                                        }
                                        PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                                            showNativeToast(copy.codeExpiredOrAlreadyUsed)
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (desktopCodeInput.trim().replace(" ", "").length == 6) Blue else InputBg
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDesktopConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = copy.connectAction,
                                    color = if (desktopCodeInput.trim().replace(" ", "").length == 6) Color.White else Mist,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // About SyncSwift Modal (Minimalist & Flat Universal SyncSwiftModal)
    if (showAboutModal) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showAboutModal = false },
            title = "SyncSwift",
            titleIcon = { InfoIcon(Modifier.size(22.dp), color = Blue) },
            confirmText = copy.close,
            confirmColor = PillButtonBg,
            confirmTextColor = PillButtonText,
            onConfirm = { showAboutModal = false },
            dismissText = "",
            onDismiss = { showAboutModal = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Version Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = copy.aboutAppVersion,
                        color = Cloud,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(InputBg)
                            .clickable {
                                devTapCount++
                                if (devTapCount >= 5) {
                                    devTapCount = 0
                                    val currentPro = net.syncswift.subscription.SubscriptionManager.isPro.value
                                    val newPro = !currentPro
                                    net.syncswift.subscription.SubscriptionManager.setProState(newPro)
                                    showNativeToast(if (newPro) "🚀 Geliştirici Modu: Pro Aktif Edildi!" else "Geliştirici Modu: Pro Kapatıldı!")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "v1.0.0",
                            color = Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.25f))

                // Web Address Row: Directly open website
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri("https://syncswift.net")
                            } catch (e: Exception) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("https://syncswift.net"))
                                showNativeToast(copy.clipboardReady)
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Web",
                            color = Mist,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "syncswift.net",
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    ChevronRightIcon(Modifier.size(16.dp), color = Blue)
                }

                HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.25f))

                // Email Address Row: Directly open mail client
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri("mailto:support@syncswift.net")
                            } catch (e: Exception) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("support@syncswift.net"))
                                showNativeToast(copy.emailCopiedToast)
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = copy.emailHint,
                            color = Mist,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "support@syncswift.net",
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    ChevronRightIcon(Modifier.size(16.dp), color = Blue)
                }
            }
        }
    }
}

@Composable
private fun ProBenefitRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Blue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            CheckIcon(Modifier.size(12.dp), color = Blue)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = Cloud,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BoxlessMenuItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    titleColor: Color = Cloud
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InputBg.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
            Text(
                text = subtitle,
                color = Mist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp),
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        ChevronRightIcon(Modifier.size(15.dp), color = Mist.copy(alpha = 0.55f))
    }
}
