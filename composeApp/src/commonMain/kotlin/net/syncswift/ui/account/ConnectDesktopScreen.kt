package net.syncswift.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.subscription.PaywallReason
import net.syncswift.subscription.SubscriptionManager
import net.syncswift.ui.components.SyncSwiftBottomSheet
import net.syncswift.ui.components.rememberQrScanner
import net.syncswift.ui.theme.*

/**
 * "Kendi Cihazlarım" (My Devices) Screen.
 * - Displays Current Device ("Bu Cihaz" badge, passive / non-removable).
 * - Lists Linked Desktop & Other Devices with removal action and remote logout.
 *   (Free users can remove desktop devices; Pro users can remove both desktop and mobile devices).
 * - Compact "Masaüstüne Bağlan" section at the bottom (Free limit: 1 desktop device).
 */
@Composable
fun ConnectDesktopScreen(
    copy: Copy,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isPro by SubscriptionManager.isPro.collectAsState()
    val userEmail = remember { SessionManager.getLoggedInEmail() ?: "" }
    val currentDeviceId = remember { SessionManager.getPlatformDeviceId() }
    val currentDeviceName = remember { SessionManager.getMyDeviceName() }
    val currentDeviceOs = remember { SessionManager.getMyPlatformName() }

    var desktopCodeInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    var registeredDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    var isLoadingDevices by remember { mutableStateOf(false) }

    var deviceToConfirmRemove by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var isRemovingDevice by remember { mutableStateOf(false) }

    // Identify linked desktop count for free plan check
    val isDesktopDevice: (DiscoveredDevice) -> Boolean = { dev ->
        val t = dev.osType.lowercase()
        t.contains("windows") || t.contains("mac") || t.contains("desktop") || t.contains("pc")
    }

    val isMobileDevice: (DiscoveredDevice) -> Boolean = { dev ->
        val t = dev.osType.lowercase()
        t.contains("android") || t.contains("ios") || t.contains("iphone") || t.contains("ipad") || t.contains("mobile") || !isDesktopDevice(dev)
    }

    fun refreshDevices() {
        if (userEmail.isNotBlank()) {
            scope.launch {
                isLoadingDevices = true
                try {
                    val remoteDevs = SupabaseNetwork.fetchAllUserDevices(userEmail)
                    registeredDevices = remoteDevs

                    // Masaüstünden çıkış yapılmışsa ve Supabase kaydı silinmişse yerel bellekten de kaldır
                    val currentDiscovered = net.syncswift.discovery.DeviceDiscoveryManager.devices.value
                    currentDiscovered.filter { isDesktopDevice(it) && remoteDevs.none { r -> r.id == it.id } }.forEach {
                        net.syncswift.discovery.DeviceDiscoveryManager.removeManualDevice(it.host)
                        net.syncswift.discovery.DeviceDiscoveryManager.removeManualDevice(it.id)
                    }
                } catch (_: Exception) {
                } finally {
                    isLoadingDevices = false
                }
            }
        }
    }

    var isRefreshingMyDevices by remember { mutableStateOf(false) }
    fun triggerMyDevicesRefresh() {
        if (isRefreshingMyDevices) return
        isRefreshingMyDevices = true
        scope.launch {
            try {
                net.syncswift.discovery.DeviceDiscoveryManager.triggerDiscoveryPing()
                net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
                refreshDevices()
                kotlinx.coroutines.delay(1800)
            } finally {
                isRefreshingMyDevices = false
            }
        }
    }

    // Ekran açıkken her 3 saniyede bir cihaz listesini güncelle (masaüstü çıkış yaptığında anında mobilde silinsin)
    LaunchedEffect(userEmail) {
        while (true) {
            refreshDevices()
            kotlinx.coroutines.delay(3000)
        }
    }

    val selfDevice = remember { net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice() }
    val myCleanName = remember { cleanDeviceDisplayName(selfDevice?.name ?: currentDeviceName) }
    val myPushToken = remember { SessionManager.getMyPushToken() }
    val isCurrentSelfDevice: (DiscoveredDevice) -> Boolean = { dev ->
        val devClean = cleanDeviceDisplayName(dev.name)
        dev.isSelf ||
        (currentDeviceId.isNotBlank() && dev.id.equals(currentDeviceId, ignoreCase = true)) ||
        (selfDevice != null && dev.id == selfDevice.id) ||
        (selfDevice != null && dev.id.removePrefix("self-") == selfDevice.id.removePrefix("self-")) ||
        (selfDevice != null && selfDevice.host.isNotBlank() && selfDevice.host != "127.0.0.1" && dev.host == selfDevice.host) ||
        (myPushToken.isNotBlank() && !dev.pushToken.isNullOrBlank() && dev.pushToken == myPushToken) ||
        (myCleanName.isNotBlank() && devClean.equals(myCleanName, ignoreCase = true)) ||
        (currentDeviceName.isNotBlank() && (devClean.equals(cleanDeviceDisplayName(currentDeviceName), ignoreCase = true) || dev.name.equals(currentDeviceName, ignoreCase = true)))
    }

    // Kullanıcının kayıtlı ve eşleşmiş cihazları (kendi telefonu kesinlikle hariç tutulur)
    val discoveredDevices by net.syncswift.discovery.DeviceDiscoveryManager.devices.collectAsState()
    val otherLinkedDevices = remember(registeredDevices, discoveredDevices, currentDeviceId, selfDevice) {
        val list = mutableListOf<DiscoveredDevice>()
        // 1. Supabase hesabına kayıtlı cihazlar (mevcut telefon hariç)
        registeredDevices.filter { !isCurrentSelfDevice(it) }.forEach { list.add(it) }

        // 2. Yerel / eşleşmiş masaüstü cihazları (mevcut telefon hariç, sadece desktop olanlar)
        discoveredDevices.filter { dev ->
            !isCurrentSelfDevice(dev) && isDesktopDevice(dev) &&
            list.none { it.id == dev.id || (it.host.isNotBlank() && it.host == dev.host && it.name == dev.name) }
        }.forEach { list.add(it) }

        list.distinctBy { it.id.ifBlank { "${it.host}:${it.port}" } }
    }

    val linkedMobileDevices = remember(otherLinkedDevices) {
        otherLinkedDevices.filter { isMobileDevice(it) }
    }

    // Masaüstü cihazları (mevcut telefon hariç)
    val linkedDesktopDevices = remember(otherLinkedDevices) {
        otherLinkedDevices.filter { isDesktopDevice(it) }
    }

    val linkedDesktopCount = remember(linkedDesktopDevices) {
        linkedDesktopDevices.size
    }

    val scanQr = rememberQrScanner(
        onSuccess = { scannedText ->
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
                urlWithoutScheme.contains(":") -> urlWithoutScheme.substringBefore(":").substringBefore("/").toIntOrNull() ?: 52424
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

            fun attemptDirectLocalPairing() {
                if (host.isNotBlank() && host != "127.0.0.1" && host != "0.0.0.0") {
                    val dev = net.syncswift.discovery.DeviceDiscoveryManager.addManualDevice(host, port, name, os)
                    ConnectionManager.requestPairing(dev) { pairRes ->
                        isConnecting = false
                        if (pairRes == PairResult.ACCEPTED) {
                            showNativeToast(copy.connectionAccepted)
                            refreshDevices()
                            onDismiss()
                        } else {
                            showNativeToast(copy.codeExpiredOrAlreadyUsed)
                        }
                    }
                } else {
                    isConnecting = false
                    showNativeToast(copy.codeExpiredOrAlreadyUsed)
                }
            }

            if (extractedCode != null && extractedCode.length == 6 && extractedCode.all { it.isDigit() }) {
                isConnecting = true
                ConnectionManager.connectViaPairingCode(extractedCode) { res ->
                    when (res) {
                        PairResult.ACCEPTED -> {
                            isConnecting = false
                            showNativeToast(copy.connectionAccepted)
                            refreshDevices()
                            onDismiss()
                        }
                        PairResult.PENDING_APPROVAL -> {
                            isConnecting = false
                            refreshDevices()
                            onDismiss()
                        }
                        PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                            attemptDirectLocalPairing()
                        }
                    }
                }
            } else {
                isConnecting = true
                attemptDirectLocalPairing()
            }
        },
        onError = { err ->
            if (err != "Cancelled") {
                showNativeToast(err)
            }
        }
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
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
                }

                Spacer(Modifier.width(14.dp))

                Text(
                    text = copy.myDevicesTitle,
                    color = Cloud,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }

            // SCROLLABLE CONTENT
            val scrollState = rememberScrollState()
            net.syncswift.ui.components.ElegantPullToRefreshLayout(
                isRefreshing = isRefreshingMyDevices,
                onRefresh = ::triggerMyDevicesRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                refreshingText = copy.scanningNearbyDevices,
                canScrollBackward = { scrollState.value > 0 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                // ==========================================
                // SECTION 1: MOBİL CİHAZLARIM (TOP)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = copy.myMobileDevices,
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Text(
                        text = copy.formatDeviceCount(1 + linkedMobileDevices.size),
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Şuanki cihaz: KUTUSUZ şekilde (boxless)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Blue.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MobileDeviceIcon(Modifier.size(20.dp), color = Blue)
                    }

                    Spacer(Modifier.width(13.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myCleanName,
                            color = Cloud,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$currentDeviceOs · ${copy.connectedStatus}",
                                color = Mist,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Passive "Bu Cihaz" Badge (Cannot be removed)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Blue.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = copy.currentDeviceBadge,
                            color = Blue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Diğer mobil cihazlar (varsa)
                if (linkedMobileDevices.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = InputBorder.copy(alpha = 0.22f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    linkedMobileDevices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Night),
                                contentAlignment = Alignment.Center
                            ) {
                                MobileDeviceIcon(Modifier.size(20.dp), color = Mist)
                            }

                            Spacer(Modifier.width(13.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cleanDeviceDisplayName(dev.name),
                                    color = Cloud,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                val statusText = if (dev.isOnline) copy.connectedStatus else copy.offlineSleeping
                                Text(
                                    text = "${dev.osType} · $statusText",
                                    color = Mist,
                                    fontSize = 11.5.sp
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ErrorRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.32f)),
                                modifier = Modifier.clickable {
                                    deviceToConfirmRemove = dev
                                }
                            ) {
                                Text(
                                    text = copy.removeAction,
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ==========================================
                // SECTION 2: MASAÜSTÜ CİHAZLARIM (BOTTOM)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = copy.myDesktopDevices,
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    if (linkedDesktopDevices.isNotEmpty()) {
                        Text(
                            text = copy.formatDeviceCount(linkedDesktopDevices.size),
                            color = Mist,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (linkedDesktopDevices.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = copy.noLinkedDesktop,
                            color = Mist,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            linkedDesktopDevices.forEachIndexed { index, dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Blue.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DesktopVectorIcon(Modifier.size(19.dp), color = Blue)
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cleanDeviceDisplayName(dev.name),
                                            color = Cloud,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        val statusText = if (dev.isOnline) copy.connectedStatus else copy.offlineSleeping
                                        Text(
                                            text = "${dev.osType} · $statusText",
                                            color = Mist,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ErrorRed.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.32f)),
                                        modifier = Modifier.clickable {
                                            deviceToConfirmRemove = dev
                                        }
                                    ) {
                                        Text(
                                            text = copy.removeAction,
                                            color = ErrorRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                if (index < linkedDesktopDevices.size - 1) {
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = InputBorder.copy(alpha = 0.22f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))

                // SECTION 3: MASAÜSTÜNE BAĞLAN (COMPACT SECTION)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Blue.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                DesktopVectorIcon(Modifier.size(18.dp), color = Blue)
                            }

                            Column {
                                Text(
                                    text = copy.connectDesktopSectionTitle,
                                    color = Cloud,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = copy.connectDesktopSectionDesc,
                                    color = Mist,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // QR CODE SCAN BUTTON
                        Button(
                            onClick = {
                                if (!isPro && linkedDesktopCount >= 1) {
                                    SubscriptionManager.triggerPaywall(PaywallReason.DEVICE_LIMIT)
                                } else {
                                    scanQr()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PillButtonBg,
                                contentColor = PillButtonText
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                QrScanVectorIcon(Modifier.size(16.dp), color = PillButtonText)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = copy.scanDesktopQr,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // OR DIVIDER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                thickness = 1.dp,
                                color = InputBorder.copy(alpha = 0.28f)
                            )
                            Text(
                                text = "  ${copy.or}  ",
                                color = Mist.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                thickness = 1.dp,
                                color = InputBorder.copy(alpha = 0.28f)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // 6-DIGIT CODE INPUT ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(InputBg)
                                    .border(1.dp, InputBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = desktopCodeInput,
                                    onValueChange = {
                                        val digitsOnly = it.filter { ch -> ch.isDigit() || ch == ' ' }
                                        if (digitsOnly.length <= 7) desktopCodeInput = digitsOnly
                                    },
                                    textStyle = TextStyle(
                                        color = Cloud,
                                        fontSize = 16.sp,
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
                                                fontSize = 14.sp,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }

                            val cleanDigits = desktopCodeInput.trim().replace(" ", "")
                            val isValidCode = cleanDigits.length == 6 && cleanDigits.all { it.isDigit() }

                            Button(
                                onClick = {
                                    if (!isPro && linkedDesktopCount >= 1) {
                                        SubscriptionManager.triggerPaywall(PaywallReason.DEVICE_LIMIT)
                                        return@Button
                                    }
                                    if (isValidCode && !isConnecting) {
                                        isConnecting = true
                                        ConnectionManager.connectViaPairingCode(cleanDigits) { res ->
                                            isConnecting = false
                                            when (res) {
                                                PairResult.ACCEPTED -> {
                                                    desktopCodeInput = ""
                                                    showNativeToast(copy.connectionAccepted)
                                                    refreshDevices()
                                                    onDismiss()
                                                }
                                                PairResult.PENDING_APPROVAL -> {
                                                    desktopCodeInput = ""
                                                    refreshDevices()
                                                    onDismiss()
                                                }
                                                PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                                                    showNativeToast(copy.codeExpiredOrAlreadyUsed)
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = isValidCode && !isConnecting,
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue,
                                    contentColor = Color.White,
                                    disabledContainerColor = InputBg,
                                    disabledContentColor = Mist.copy(alpha = 0.5f)
                                )
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = copy.connectAction,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Compact Steps
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = InputBg.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StepRow(number = "1", text = copy.desktopStep1)
                                StepRow(number = "2", text = copy.desktopStep2)
                                StepRow(number = "3", text = copy.desktopStep3)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
        }

        // Remove Device Confirmation Bottom Sheet
        if (deviceToConfirmRemove != null) {
            val targetDev = deviceToConfirmRemove!!
            SyncSwiftBottomSheet(
                visible = true,
                onDismissRequest = {
                    if (!isRemovingDevice) deviceToConfirmRemove = null
                },
                title = copy.removeDeviceConfirmTitle,
                titleIcon = { WarningTriangleIcon(Modifier.size(20.dp), color = ErrorRed) },
                confirmText = copy.removeAction,
                confirmColor = ErrorRed,
                confirmTextColor = Color.White,
                confirmLoading = isRemovingDevice,
                onConfirm = {
                    scope.launch {
                        isRemovingDevice = true
                        try {
                            val devId = targetDev.id.ifBlank { "manual-${targetDev.host}" }
                            // 1. Send direct local TCP logout packet to target device if reachable
                            ConnectionManager.notifyRemoteLogout(targetDev)
                            // 2. Remove from Supabase (sends FCM push, deletes row, clears user_id)
                            if (userEmail.isNotBlank()) {
                                SupabaseNetwork.removeUserDevice(userEmail, devId)
                                SupabaseNetwork.clearDeviceUser(devId)
                            }
                            // 3. Disconnect local connection & purge manual device
                            ConnectionManager.disconnectDevice(targetDev, notifyPeer = false)
                            net.syncswift.discovery.DeviceDiscoveryManager.removeManualDevice(targetDev.host)
                            net.syncswift.discovery.DeviceDiscoveryManager.removeManualDevice(devId)
                            showNativeToast(copy.deviceRemovedSuccess)
                            refreshDevices()
                        } finally {
                            isRemovingDevice = false
                            deviceToConfirmRemove = null
                        }
                    }
                },
                dismissText = copy.cancelAction,
                onDismiss = {
                    if (!isRemovingDevice) deviceToConfirmRemove = null
                }
            ) {
                Text(
                    text = copy.removeDeviceConfirmDesc.replace("%s", cleanDeviceDisplayName(targetDev.name)),
                    color = Mist,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Blue.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Blue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Mist,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
