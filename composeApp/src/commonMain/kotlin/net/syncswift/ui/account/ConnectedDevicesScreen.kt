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
import net.syncswift.ui.components.SyncSwiftModal
import net.syncswift.ui.components.SendPairRequestScreen
import net.syncswift.ui.components.ConnectedDeviceActionScreen
import androidx.compose.animation.core.tween
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConnectedDevicesScreen(
    copy: Copy,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val discoveredDevices by DeviceDiscoveryManager.devices.collectAsState()
    val connectedKeys by ConnectionManager.connectedDeviceKeys.collectAsState()
    val isScanning by DeviceDiscoveryManager.isScanning.collectAsState()
    val selfDevice = remember { DeviceDiscoveryManager.getSelfDevice() }
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
    }

    val selfHost = selfDevice?.host ?: "127.0.0.1"
    val selfPort = selfDevice?.port ?: 52424
    val selfName = selfDevice?.name ?: "SyncSwift Device"
    val qrConnectionPayload = "syncswift://remote?code=${activePairingCode.replace(" ", "")}&host=$selfHost&port=$selfPort&name=$selfName&os=Android"

    fun processConnectionUrl(url: String) {
        try {
            val clean = url.trim()

            val remoteCode = when {
                clean.startsWith("syncswift://remote") || clean.startsWith("syncdrop://remote") -> {
                    clean.substringAfter("code=", "").substringBefore("&").trim()
                }
                clean.length == 6 && clean.all { it.isDigit() } -> clean
                clean.length == 7 && clean.contains(" ") -> clean.replace(" ", "")
                else -> null
            }

            if (!net.syncswift.subscription.SubscriptionManager.canAddDevice(connectedKeys.size)) {
                showScanModal = false
                net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                    net.syncswift.subscription.PaywallReason.DEVICE_LIMIT
                )
                return
            }

            if (remoteCode != null) {
                showScanModal = false
                showNativeToast(copy.connecting)
                ConnectionManager.connectViaPairingCode(remoteCode) { result ->
                    when (result) {
                        PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                        else -> showNativeToast(copy.invalidPairingCode)
                    }
                }
                return
            }

            val (host, port, name) = when {
                clean.startsWith("syncswift://") || clean.startsWith("syncdrop://") -> {
                    val query = clean.substringAfter("?", "")
                    val params = query.split("&").associate {
                        val parts = it.split("=")
                        if (parts.size == 2) parts[0] to parts[1] else "" to ""
                    }
                    val h = params["host"] ?: ""
                    val p = params["port"]?.toIntOrNull() ?: 52424
                    val n = params["name"]?.replace("%20", " ") ?: "SyncSwift Device"
                    Triple(h, p, n)
                }
                clean.contains(":") -> {
                    val parts = clean.split(":")
                    val h = parts[0].trim()
                    val p = parts[1].trim().toIntOrNull() ?: 52424
                    Triple(h, p, "SyncSwift Device")
                }
                else -> Triple(clean, 52424, "SyncSwift Device")
            }

            if (host.isNotBlank()) {
                val dev = DeviceDiscoveryManager.addManualDevice(host = host, port = port, name = name)
                showScanModal = false
                showNativeToast(copy.sendingConnectionRequest)
                ConnectionManager.requestPairing(dev) { result ->
                    when (result) {
                        PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                        PairResult.REJECTED -> showNativeToast(copy.connectionRejected)
                        PairResult.UNAVAILABLE -> showNativeToast(copy.deviceUnavailable)
                        PairResult.PENDING_APPROVAL -> {}
                    }
                }
            } else {
                showNativeToast(copy.invalidQrCode)
            }
        } catch (e: Exception) {
            showNativeToast(copy.invalidQrCode)
        }
    }

    val scanQr = rememberQrScanner(
        onSuccess = { scannedText ->
            showScanModal = false
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && pullOffset > 0f) {
                    val consumed = available.y.coerceAtLeast(-pullOffset)
                    pullOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0 && scrollState.value == 0 && !isRefreshing) {
                    pullOffset = (pullOffset + available.y * 0.4f).coerceAtMost(250f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset >= 150f && !isRefreshing) {
                    triggerScan()
                }
                pullOffset = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(nestedScrollConnection)
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
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )
            }

            // Modern Pull-to-Refresh: circular indicator with text underneath, no dots, no box
            AnimatedVisibility(
                visible = isRefreshing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Blue,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = copy.scanningNearbyDevices,
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val otherDevices = discoveredDevices.filter { dev ->
                !dev.isSelf &&
                (selfDevice == null || (dev.host != selfDevice.host && cleanDeviceDisplayName(dev.name) != cleanDeviceDisplayName(selfDevice.name)))
            }
            val localConnected = otherDevices.filter { ConnectionManager.isDeviceConnected(it) && !ConnectionManager.isRemoteDevice(it) }
            val remoteConnected = otherDevices.filter { ConnectionManager.isDeviceConnected(it) && ConnectionManager.isRemoteDevice(it) }

            // SECTION 1: Yerel Cihazlar (Aynı Ağ)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { triggerScan() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copy.localConnectedDevices,
                    color = Cloud,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val localCount = localConnected.size + (if (selfDevice != null) 1 else 0)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.formatDeviceCount(localCount),
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                if (selfDevice != null) {
                    DeviceItemRow(
                        name = cleanDeviceDisplayName(selfDevice.name),
                        details = copy.thisDeviceOnline,
                        badge = selfDevice.osType,
                        isOnline = true,
                        isSelf = true
                    )
                    HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                }

                if (localConnected.isNotEmpty()) {
                    localConnected.forEach { dev ->
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
                }
            }

            Spacer(Modifier.height(28.dp))

            // SECTION 2: Uzaktan Bağlı Cihazlar (Farklı Ağ - PRO)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = copy.remoteConnectedDevices,
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Blue.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = copy.proBadge,
                            color = Blue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.formatDeviceCount(remoteConnected.size),
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                if (remoteConnected.isNotEmpty()) {
                    remoteConnected.forEach { dev ->
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
                        text = copy.noRemoteConnectedDevices,
                        color = Mist,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // SECTION 4: Uzaktan Bağlama (PRO Özellik) Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.2.dp, Blue.copy(alpha = 0.45f))
            ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = copy.remotePairingTitle,
                                color = Cloud,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Blue.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = copy.proBadge,
                                    color = Blue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        // Live countdown timer badge without icon, enlarged
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

                    // Rotating Code Showcase Bar
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
                                    if (it.length <= 7) manualCodeInput = it
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
                                    if (!net.syncswift.subscription.SubscriptionManager.canAddDevice(connectedKeys.size)) {
                                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                                            net.syncswift.subscription.PaywallReason.DEVICE_LIMIT
                                        )
                                        return@clickable
                                    }
                                    isManualConnecting = true
                                    val clean = manualCodeInput.trim().replace(" ", "")
                                    ConnectionManager.connectViaPairingCode(clean) { res ->
                                        isManualConnecting = false
                                        if (res == PairResult.ACCEPTED) {
                                            manualCodeInput = ""
                                            showNativeToast(copy.connectionAccepted)
                                        } else {
                                            showNativeToast(copy.invalidPairingCode)
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (manualCodeInput.trim().replace(" ", "").length == 6) Blue else InputBg
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
                                        color = if (manualCodeInput.trim().replace(" ", "").length == 6) Color.White else Mist,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }

        // MODAL 1: QR Göster Modal
        if (showQrModal) {
            SyncSwiftModal(
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
                        text = copy.codeExpiresIn.format(codeRemainingSeconds),
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = selfName,
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // MODAL 2: QR Okut Modal (Option 1: Kamera ile Okut, Option 2: Dosyalardan Seç. NO manual IP, NO Bağlan button)
        if (showScanModal) {
            SyncSwiftModal(
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
                            .clickable { scanQr() },
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
                            .clickable { pickQrImage() },
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
            androidx.activity.compose.BackHandler {
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
            androidx.activity.compose.BackHandler {
                deviceForAction = null
            }
            ConnectedDeviceActionScreen(
                device = target,
                copy = copy,
                onSendFile = {
                    deviceForAction = null
                    onBack()
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
