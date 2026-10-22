package net.syncswift.ui.account

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.ui.components.QrCodeView
import net.syncswift.ui.components.rememberQrImagePicker
import net.syncswift.ui.components.rememberQrScanner
import net.syncswift.ui.components.SyncSwiftBottomSheet
import net.syncswift.ui.components.ConnectedDeviceActionScreen
import androidx.compose.animation.core.tween
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectedDevicesScreen(
    copy: Copy,
    onBack: () -> Unit,
    onSendFileToDevice: (DiscoveredDevice) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val discoveredDevices by DeviceDiscoveryManager.devices.collectAsState()
    val connectedKeys by ConnectionManager.connectedDeviceKeys.collectAsState()
    val isScanning by DeviceDiscoveryManager.isScanning.collectAsState()
    val selfDevice = remember { DeviceDiscoveryManager.getSelfDevice() }
    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val activePairingCode by ConnectionManager.activePairingCode.collectAsState()
    val codeRemainingSeconds by ConnectionManager.codeRemainingSeconds.collectAsState()
    var manualCodeInput by remember { mutableStateOf("") }
    var isManualConnecting by remember { mutableStateOf(false) }

    var deviceForAction by remember { mutableStateOf<DiscoveredDevice?>(null) }

    var showQrModal by remember { mutableStateOf(false) }
    var showScanModal by remember { mutableStateOf(false) }

    val pendingPairRequest by ConnectionManager.pendingPairRequest.collectAsState()


    var isRefreshing by remember { mutableStateOf(false) }
    var showScanningBanner by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }

    val isDesktopDevice: (DiscoveredDevice) -> Boolean = { dev ->
        val t = dev.osType.lowercase()
        t.contains("windows") || t.contains("mac") || t.contains("desktop") || t.contains("pc")
    }

    var registeredAccountDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    val loggedInEmail = remember { SessionManager.getLoggedInEmail() ?: "" }

    LaunchedEffect(loggedInEmail) {
        if (loggedInEmail.isNotBlank()) {
            try {
                registeredAccountDevices = SupabaseNetwork.fetchAllUserDevices(loggedInEmail)
            } catch (_: Exception) {}
        }
    }

    val isMyAccountDevice: (DiscoveredDevice) -> Boolean = { dev ->
        registeredAccountDevices.any {
            (it.id.isNotBlank() && it.id == dev.id) ||
            (it.host.isNotBlank() && it.host == dev.host && cleanDeviceDisplayName(it.name) == cleanDeviceDisplayName(dev.name)) ||
            cleanDeviceDisplayName(it.name).equals(cleanDeviceDisplayName(dev.name), ignoreCase = true)
        }
    }

    fun triggerScan() {
        if (isRefreshing) return
        isRefreshing = true
        pullOffset = 0f
        scope.launch {
            try {
                DeviceDiscoveryManager.startDiscovery()
                DeviceDiscoveryManager.triggerDiscoveryPing()
                if (loggedInEmail.isNotBlank()) {
                    try {
                        registeredAccountDevices = SupabaseNetwork.fetchAllUserDevices(loggedInEmail)
                    } catch (_: Exception) {}
                }
                delay(2000)
            } finally {
                isRefreshing = false
                pullOffset = 0f
            }
        }
    }

    LaunchedEffect(Unit) {
        DeviceDiscoveryManager.startDiscovery()
        ConnectionManager.ensurePairingCodeActive()
    }

    val selfHost = selfDevice?.host ?: "127.0.0.1"
    val selfPort = selfDevice?.port ?: 52424
    val selfName = selfDevice?.name ?: "SyncSwift Device"
    val selfOs = selfDevice?.osType ?: "Mobile"
    val qrConnectionPayload = "syncswift://remote?code=${activePairingCode.replace(" ", "")}&host=$selfHost&port=$selfPort&name=$selfName&os=$selfOs"

    fun processConnectionUrl(url: String) {
        try {
            val clean = url.trim()

            // 1. Extract 6-digit code if present
            val remoteCode = when {
                clean.contains("code=") -> clean.substringAfter("code=", "").substringBefore("&").replace(" ", "").trim()
                clean.length == 6 && clean.all { it.isDigit() } -> clean
                clean.length == 7 && clean.contains(" ") -> clean.replace(" ", "")
                clean.matches(Regex(""".*\b(\d{3}\s?\d{3})\b.*""")) -> {
                    Regex("""\b(\d{3}\s?\d{3})\b""").find(clean)?.groupValues?.get(1)?.replace(" ", "")
                }
                else -> null
            }

            // 2. Extract direct network host / port / name / os
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
                else -> "SyncSwift Device"
            }
            val os = when {
                clean.contains("os=") -> clean.substringAfter("os=", "").substringBefore("&").trim()
                else -> "Remote Device"
            }

            fun attemptDirectLocalPairing() {
                val targetHost = when {
                    host.isNotBlank() && host != "127.0.0.1" && host != "0.0.0.0" -> host
                    else -> DeviceDiscoveryManager.devices.value.firstOrNull { !it.isSelf }?.host ?: DeviceDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1"
                }
                val dev = DeviceDiscoveryManager.addManualDevice(host = targetHost, port = port, name = name, osType = os)
                showScanModal = false
                showNativeToast(copy.sendingConnectionRequest)
                ConnectionManager.requestPairing(dev) { result ->
                    when (result) {
                        PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                        PairResult.REJECTED -> {
                            ConnectionManager.disconnectDevice(dev, notifyPeer = false)
                            showNativeToast(copy.connectionRejected)
                        }
                        PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                        PairResult.PENDING_APPROVAL -> {}
                    }
                }
            }

            if (remoteCode != null && remoteCode.length == 6 && remoteCode.all { it.isDigit() }) {
                showScanModal = false
                showNativeToast(copy.connecting)
                ConnectionManager.connectViaPairingCode(remoteCode) { result ->
                    when (result) {
                        PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                        PairResult.PENDING_APPROVAL -> {
                            // Dialog is showing waiting for approval with cancel button
                        }
                        PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                            attemptDirectLocalPairing()
                        }
                    }
                }
                return
            }

            attemptDirectLocalPairing()
        } catch (e: Exception) {
            showScanModal = false
            showNativeToast(copy.codeExpiredOrAlreadyUsed)
        }
    }

    val scanQr = rememberQrScanner(
        onSuccess = { scannedText ->
            showScanModal = false
            showQrModal = false
            processConnectionUrl(scannedText)
        },
        onError = { err ->
            if (err != "Cancelled") {
                if (err == "Camera permission denied") {
                    showNativeToast(copy.cameraPermissionDenied)
                } else {
                    showNativeToast(err)
                }
            }
        }
    )

    val pickQrImage = rememberQrImagePicker(
        onSuccess = { scannedText ->
            showScanModal = false
            showQrModal = false
            processConnectionUrl(scannedText)
        },
        onError = { err ->
            if (err == "NoQrFound") {
                showNativeToast(copy.noQrCodeFoundInImage)
            } else if (err != "Cancelled") {
                showNativeToast(err)
            }
        }
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar with Back Button & Title (Header stays pinned, Pull-to-refresh starts underneath)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = copy.connectedDevicesTitle,
                    color = Cloud,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }

            net.syncswift.ui.components.ElegantPullToRefreshLayout(
                isRefreshing = isRefreshing,
                onRefresh = ::triggerScan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                refreshingText = copy.scanningNearbyDevices,
                canScrollBackward = { scrollState.value > 0 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(Modifier.height(10.dp))

            val allConnectedDevices = remember(discoveredDevices, connectedKeys, registeredAccountDevices) {
                val list = mutableListOf<DiscoveredDevice>()
                val otherDevices = discoveredDevices.filter { dev ->
                    !dev.isSelf &&
                    !dev.name.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) &&
                    (selfDevice == null || (dev.host != selfDevice.host && cleanDeviceDisplayName(dev.name) != cleanDeviceDisplayName(selfDevice.name)))
                }.distinctBy {
                    cleanDeviceDisplayName(it.name)
                }

                otherDevices.filter { dev ->
                    val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                        connectedKeys.contains(dev.id) ||
                        connectedKeys.contains(dev.host) ||
                        connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                        ConnectionManager.isDeviceConnected(dev)
                    isConn && !isMyAccountDevice(dev)
                }.forEach { dev ->
                    list.add(dev.copy(name = cleanDeviceDisplayName(dev.name), isOnline = true))
                }

                list.distinctBy { cleanDeviceDisplayName(it.name) }
            }

            // SECTION: Bağlı Cihazlar (Tümü Tek Liste)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { triggerScan() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copy.connectedDevicesTitle,
                    color = Cloud,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val connectedCount = allConnectedDevices.size
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.formatDeviceCount(connectedCount),
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                if (allConnectedDevices.isNotEmpty()) {
                    allConnectedDevices.forEach { dev ->
                        val status = if (dev.isOnline) copy.connectedStatus else copy.offlineSleeping
                        DeviceItemRow(
                            name = cleanDeviceDisplayName(dev.name),
                            details = status,
                            badge = dev.osType,
                            isOnline = dev.isOnline,
                            isConnected = true,
                            isSelf = false,
                            onClick = { deviceForAction = dev }
                        )
                        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                    }
                } else {
                    Text(
                        text = copy.noConnectedDevicesYet,
                        color = Mist,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // BUTONLAR: QR Göster & QR Okut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // BUTON 1: QR Göster
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showQrModal = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Blue
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QrCodeVectorIcon(Modifier.size(18.dp), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = copy.showQrAction,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // BUTON 2: QR Okut
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { scanQr() },
                    shape = RoundedCornerShape(16.dp),
                    color = InputBg,
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QrScanVectorIcon(Modifier.size(18.dp), color = Cloud)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = copy.scanQrAction,
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 6 Haneli Sayı Girme Alanı (Modern, Şık, Sadece Sayı Klavyesi)
            val cleanDigits = remember(manualCodeInput) { manualCodeInput.filter { it.isDigit() }.take(6) }
            val isReadyToConnect = cleanDigits.length == 6

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = InputBg,
                border = BorderStroke(
                    1.2.dp,
                    if (isReadyToConnect) Blue.copy(alpha = 0.8f) else InputBorder.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern Keypad/Code Icon
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isReadyToConnect) Blue.copy(alpha = 0.2f) else CardSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#",
                            color = if (isReadyToConnect) Blue else Mist,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    BasicTextField(
                        value = manualCodeInput,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(6)
                            manualCodeInput = if (digitsOnly.length > 3) {
                                "${digitsOnly.take(3)} ${digitsOnly.drop(3)}"
                            } else {
                                digitsOnly
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Cloud,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (manualCodeInput.isEmpty()) {
                                Text(
                                    text = copy.enter6DigitCode,
                                    color = Mist.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isReadyToConnect && !isManualConnecting) {
                                if (isReadyToConnect) {
                                    isManualConnecting = true
                                    ConnectionManager.connectViaPairingCode(cleanDigits) { res ->
                                        isManualConnecting = false
                                        when (res) {
                                            PairResult.ACCEPTED -> {
                                                manualCodeInput = ""
                                                showNativeToast(copy.connectionAccepted)
                                            }
                                            PairResult.EXPIRED_OR_USED -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                                            PairResult.UNAVAILABLE -> showNativeToast(copy.deviceUnavailable)
                                            else -> {}
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isReadyToConnect) Blue else Blue.copy(alpha = 0.25f)
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            if (isManualConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = copy.connectAction,
                                    color = if (isReadyToConnect) Color.White else Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

        // MODAL 1: QR Okut Bottom Sheet (Option 1: Kamera ile Okut, Option 2: Dosyalardan Seç)
        if (showScanModal) {
            SyncSwiftBottomSheet(
                visible = showScanModal,
                title = copy.scanQrTitle,
                onDismissRequest = { showScanModal = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = copy.scanQrSubtitle,
                        color = Mist,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    // Option A: Kamera ile Okut
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable {
                                showScanModal = false
                                scanQr()
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
                                text = copy.scanWithCamera,
                                color = PillButtonText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Option B: Dosyalardan / Galeriden Seç
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable {
                                showScanModal = false
                                pickQrImage()
                            },
                        shape = RoundedCornerShape(26.dp),
                        color = InputBg,
                        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            UploadIcon(Modifier.size(18.dp), color = Cloud)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = copy.selectFromFiles,
                                color = Cloud,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // MODAL 2: QR Göster Bottom Sheet (QR Kod, 6 Haneli Kod ve Saniye)
        if (showQrModal) {
            SyncSwiftBottomSheet(
                visible = showQrModal,
                onDismissRequest = { showQrModal = false },
                title = copy.remotePairingTitle,
                titleIcon = { QrCodeVectorIcon(Modifier.size(20.dp), color = Blue) },
                confirmText = copy.close,
                confirmColor = Blue,
                confirmTextColor = Color.White,
                onConfirm = { showQrModal = false },
                dismissText = null,
                onDismiss = null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QrCodeView(
                        content = qrConnectionPayload,
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = activePairingCode,
                        color = Cloud,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = copy.codeExpiresIn.replace("%s", codeRemainingSeconds.toString()),
                            color = Mist,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { ConnectionManager.generateOrRotatePairingCode() },
                            shape = CircleShape,
                            color = InputBg,
                            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f))
                        ) {
                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                RefreshIcon(Modifier.size(14.dp), color = Cloud)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

    AnimatedVisibility(
        visible = deviceForAction != null,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val target = deviceForAction
        if (target != null) {
            BackHandler {
                deviceForAction = null
            }
            ConnectedDeviceActionScreen(
                device = target,
                copy = copy,
                onSendFile = {
                    deviceForAction = null
                    onSendFileToDevice(target)
                },
                onDisconnect = {
                    deviceForAction = null
                    ConnectionManager.disconnectDevice(target)
                    showNativeToast(copy.disconnected)
                },
                onDismiss = { deviceForAction = null }
            )
        }
    }
    }
}

@Composable
private fun DeviceItemRow(
    name: String,
    details: String,
    badge: String,
    isOnline: Boolean,
    isConnected: Boolean = false,
    isSelf: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InputBg.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            DevicePlatformIcon(
                name = name,
                osType = badge,
                color = if (isConnected && isOnline) Blue else Mist,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(name, color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = badge,
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) SuccessGreen else Mist.copy(alpha = 0.4f))
                )
                Text(
                    text = details,
                    color = if (isConnected && isOnline) Blue else Mist,
                    fontSize = 12.sp,
                    fontWeight = if (isConnected && isOnline) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        if (!isSelf) {
            ChevronRightIcon(Modifier.size(14.dp), color = Mist.copy(alpha = 0.6f))
        }
    }
}
