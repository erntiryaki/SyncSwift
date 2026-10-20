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
import net.syncswift.connection.OutgoingStatusManager
import net.syncswift.connection.PairResult
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.ui.components.QrCodeView
import net.syncswift.ui.components.rememberQrImagePicker
import net.syncswift.ui.components.rememberQrScanner
import net.syncswift.ui.components.SyncSwiftBottomSheet
import net.syncswift.ui.components.SendPairRequestScreen
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

    var deviceToPair by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var deviceForAction by remember { mutableStateOf<DiscoveredDevice?>(null) }

    var showQrModal by remember { mutableStateOf(false) }
    var showScanModal by remember { mutableStateOf(false) }

    val pendingPairRequest by ConnectionManager.pendingPairRequest.collectAsState()
    val pendingTransferRequest by ConnectionManager.pendingTransferRequest.collectAsState()
    val outgoingStatus by OutgoingStatusManager.state.collectAsState()

    LaunchedEffect(pendingPairRequest) {
        if (pendingPairRequest != null) {
            showQrModal = false
            showScanModal = false
            deviceToPair = null
            deviceForAction = null
        }
    }

    LaunchedEffect(pendingTransferRequest) {
        if (pendingTransferRequest != null) {
            showQrModal = false
            showScanModal = false
            deviceToPair = null
            deviceForAction = null
        }
    }

    LaunchedEffect(outgoingStatus) {
        if (outgoingStatus != null) {
            showQrModal = false
            showScanModal = false
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var showScanningBanner by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }

    fun triggerScan() {
        if (isRefreshing) return
        isRefreshing = true
        pullOffset = 0f
        scope.launch {
            try {
                DeviceDiscoveryManager.startDiscovery()
                delay(2500)
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
                if (host.isNotBlank() && host != "127.0.0.1" && host != "0.0.0.0") {
                    val dev = DeviceDiscoveryManager.addManualDevice(host = host, port = port, name = name, osType = os)
                    showScanModal = false
                    showNativeToast(copy.sendingConnectionRequest)
                    ConnectionManager.requestPairing(dev) { result ->
                        when (result) {
                            PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                            PairResult.REJECTED -> showNativeToast(copy.connectionRejected)
                            PairResult.EXPIRED_OR_USED -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                            PairResult.UNAVAILABLE -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                            PairResult.PENDING_APPROVAL -> {}
                        }
                    }
                } else {
                    showScanModal = false
                    showNativeToast(copy.codeExpiredOrAlreadyUsed)
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
                    Spacer(Modifier.height(16.dp))

                    // Top Bar with Back Button & Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))

            val otherDevices = discoveredDevices.filter { dev ->
                !dev.isSelf &&
                (selfDevice == null || (dev.host != selfDevice.host && cleanDeviceDisplayName(dev.name) != cleanDeviceDisplayName(selfDevice.name)))
            }.distinctBy {
                if (it.host.isNotBlank() && !it.host.startsWith("remote-")) it.host else cleanDeviceDisplayName(it.name)
            }
            val allConnectedDevices = otherDevices.filter { dev ->
                connectedKeys.contains("${dev.host}:${dev.port}") ||
                connectedKeys.contains(dev.id) ||
                connectedKeys.contains(dev.host) ||
                connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                ConnectionManager.isDeviceConnected(dev)
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

            Spacer(Modifier.height(32.dp))

            // SECTION 4: Uzaktan Bağlama Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.2.dp, InputBorder.copy(alpha = 0.35f))
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = copy.remotePairingTitle,
                                color = Cloud,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${codeRemainingSeconds}s",
                                color = Blue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = copy.remotePairingSubtitle,
                            color = Mist,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        // Rotating Code Showcase Bar with Refresh Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(InputBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = copy.enter6DigitCode,
                                    color = Mist,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = activePairingCode,
                                    color = Cloud,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 3.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${codeRemainingSeconds}s",
                                    color = Mist,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { ConnectionManager.generateOrRotatePairingCode() },
                                    shape = CircleShape,
                                    color = InputBg,
                                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RefreshIcon(Modifier.size(16.dp), color = Cloud)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 2 Action Buttons: QR Göster & QR Okut
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(23.dp))
                                    .clickable { showQrModal = true },
                                shape = RoundedCornerShape(23.dp),
                                color = PillButtonBg
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    QrCodeVectorIcon(Modifier.size(16.dp), color = PillButtonText)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = copy.showQrAction,
                                        color = PillButtonText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(23.dp))
                                    .clickable { showScanModal = true },
                                shape = RoundedCornerShape(23.dp),
                                color = InputBg
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    QrScanVectorIcon(Modifier.size(16.dp), color = Blue)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = copy.scanQrAction,
                                        color = Cloud,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                        Spacer(Modifier.height(14.dp))

                        // 6-digit code direct input area
                        Text(
                            text = copy.enter6DigitCode,
                            color = Cloud,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

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
                                    value = manualCodeInput,
                                    onValueChange = {
                                        if (isPro && it.length <= 7) manualCodeInput = it
                                    },
                                    enabled = isPro,
                                    textStyle = TextStyle(
                                        color = Cloud,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (manualCodeInput.isEmpty()) {
                                            Text(
                                                text = copy.enter6DigitCodeHint,
                                                color = Mist.copy(alpha = 0.5f),
                                                fontSize = 15.sp,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = manualCodeInput.trim().replace(" ", "").length == 6 && !isManualConnecting) {
                                        isManualConnecting = true
                                        val clean = manualCodeInput.trim().replace(" ", "")
                                        ConnectionManager.connectViaPairingCode(clean) { res ->
                                            isManualConnecting = false
                                            when (res) {
                                                PairResult.ACCEPTED -> {
                                                    manualCodeInput = ""
                                                    showNativeToast(copy.connectionAccepted)
                                                }
                                                PairResult.PENDING_APPROVAL -> {
                                                    manualCodeInput = ""
                                                }
                                                PairResult.EXPIRED_OR_USED, PairResult.UNAVAILABLE, PairResult.REJECTED -> {
                                                    showNativeToast(copy.codeExpiredOrAlreadyUsed)
                                                }
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPro && manualCodeInput.trim().replace(" ", "").length == 6) Blue else InputBg
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isManualConnecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            text = copy.connectAction,
                                            color = if (isPro && manualCodeInput.trim().replace(" ", "").length == 6) Color.White else Mist.copy(alpha = 0.6f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

        // MODAL 1: QR Göster Bottom Sheet
        if (showQrModal) {
            SyncSwiftBottomSheet(
                visible = showQrModal,
                title = copy.showQrTitle,
                onDismissRequest = { showQrModal = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = copy.showQrSubtitle,
                        color = Mist,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    // Crisp high-contrast QR Code View
                    QrCodeView(
                        content = qrConnectionPayload,
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = activePairingCode,
                        color = Cloud,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = copy.codeExpiresIn.replace("%s", codeRemainingSeconds.toString()),
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(14.dp))

                    // QR / Code Refresh Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { ConnectionManager.generateOrRotatePairingCode() },
                        shape = RoundedCornerShape(20.dp),
                        color = InputBg,
                        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RefreshIcon(Modifier.size(16.dp), color = Cloud)
                            Text(
                                text = copy.refreshCode,
                                color = Cloud,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = selfName,
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // MODAL 2: QR Okut Bottom Sheet (Option 1: Kamera ile Okut, Option 2: Dosyalardan Seç. NO manual IP, NO Bağlan button)
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
    }

    AnimatedVisibility(
        visible = deviceToPair != null,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val target = deviceToPair
        if (target != null) {
            BackHandler {
                deviceToPair = null
            }
            SendPairRequestScreen(
                device = target,
                copy = copy,
                onSendRequest = {
                    if (!net.syncswift.subscription.SubscriptionManager.canAddDevice(connectedKeys.size)) {
                        deviceToPair = null
                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                            net.syncswift.subscription.PaywallReason.DEVICE_LIMIT
                        )
                        return@SendPairRequestScreen
                    }
                    deviceToPair = null
                    showNativeToast(copy.sendingConnectionRequest)
                    ConnectionManager.requestPairing(target) { result ->
                        when (result) {
                            PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                            PairResult.REJECTED -> showNativeToast(copy.connectionRejected)
                            PairResult.EXPIRED_OR_USED -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                            PairResult.UNAVAILABLE -> showNativeToast(copy.deviceUnavailable)
                            PairResult.PENDING_APPROVAL -> {}
                        }
                    }
                },
                onDismiss = { deviceToPair = null }
            )
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
        val isMobile = badge.equals("Android", ignoreCase = true) || badge.equals("iOS", ignoreCase = true)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InputBg.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            if (isMobile) {
                PhoneVectorIcon(Modifier.size(20.dp), color = if (isConnected && isOnline) Blue else Mist)
            } else {
                LaptopVectorIcon(Modifier.size(20.dp), color = if (isConnected && isOnline) Blue else Mist)
            }
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
