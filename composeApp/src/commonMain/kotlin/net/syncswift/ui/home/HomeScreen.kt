package net.syncswift.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.transfer.TransferEngine
import net.syncswift.transfer.TransferItem
import net.syncswift.transfer.TransferStatus
import net.syncswift.ui.components.SyncSwiftModal
import net.syncswift.ui.components.SendPairRequestScreen
import net.syncswift.ui.components.ConnectedDeviceActionScreen
import androidx.compose.animation.core.tween
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    copy: Copy,
    onSendClick: () -> Unit = {},
    onClipboardClick: () -> Unit = {},
    onDeviceClick: (DiscoveredDevice) -> Unit = {},
    onViewAllTransfers: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val discoveredDevices by DeviceDiscoveryManager.devices.collectAsState()
    val connectedKeys by ConnectionManager.connectedDeviceKeys.collectAsState()
    val isScanning by DeviceDiscoveryManager.isScanning.collectAsState()
    val selfDevice = remember { DeviceDiscoveryManager.getSelfDevice() }
    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
    val allTransfers by TransferEngine.transfers.collectAsState()
    val recentTransfers = remember(allTransfers) {
        allTransfers.filter { it.status == TransferStatus.COMPLETED }
    }
    val scope = rememberCoroutineScope()

    var deviceToPair by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var deviceForAction by remember { mutableStateOf<DiscoveredDevice?>(null) }

    val pendingPairRequest by ConnectionManager.pendingPairRequest.collectAsState()
    LaunchedEffect(pendingPairRequest) {
        if (pendingPairRequest != null) {
            deviceToPair = null
            deviceForAction = null
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
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(16.dp))

        // Top Bar: Clean Minimalist Page Header with Notification Bell
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = copy.homeTab,
                color = Cloud,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            )

            val notificationsList by net.syncswift.notification.NotificationHistoryManager.notifications.collectAsState()
            val unreadCount = remember(notificationsList) { notificationsList.count { !it.isRead } }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(InputBg.copy(alpha = 0.7f))
                    .clickable { onOpenNotifications() },
                contentAlignment = Alignment.Center
            ) {
                NotificationBellIcon(Modifier.size(19.dp), color = Cloud)
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Blue)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Current Device Info (Boxless, across the width)
        val selfDev = remember { DeviceDiscoveryManager.getSelfDevice() }
        val selfDeviceName = selfDev?.name ?: "SyncSwift Device"
        val osText = selfDev?.osType ?: (if (currentPlatform() == PlatformFlavor.Android) "Android" else "iOS")
        val deviceSubtitle = "$osText (${copy.yourDevice})"
        val isMobileDevice = osText.equals("Android", ignoreCase = true) || osText.equals("iOS", ignoreCase = true)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBg.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (isMobileDevice) {
                    PhoneVectorIcon(Modifier.size(19.dp), color = Blue)
                } else {
                    LaptopVectorIcon(Modifier.size(19.dp), color = Blue)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = selfDeviceName,
                    color = Cloud,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        text = deviceSubtitle,
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

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

        Spacer(Modifier.height(20.dp))

        val otherDevices = discoveredDevices.filter { dev ->
            !dev.isSelf &&
            (selfDevice == null || (dev.host != selfDevice.host && cleanDeviceDisplayName(dev.name) != cleanDeviceDisplayName(selfDevice.name)))
        }
        // Home screen shows ONLY online local devices (no remote devices, no offline ghosts)
        val connectedList = otherDevices.filter { dev ->
            val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                         connectedKeys.contains(dev.id) ||
                         connectedKeys.contains(dev.host) ||
                         connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                         ConnectionManager.isDeviceConnected(dev)
            isConn && !ConnectionManager.isRemoteDevice(dev) && dev.isOnline
        }
        val availableList = otherDevices.filter { dev ->
            val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                         connectedKeys.contains(dev.id) ||
                         connectedKeys.contains(dev.host) ||
                         connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                         ConnectionManager.isDeviceConnected(dev)
            !isConn && !ConnectionManager.isRemoteDevice(dev) && dev.isOnline
        }

        // SECTION 1: BAĞLI CİHAZLAR (Connected Devices)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { triggerScan() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.connectedDevicesSection,
                color = Cloud,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            val connectedCount = connectedList.size
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
            if (connectedList.isNotEmpty()) {
                connectedList.forEach { device ->
                    val statusText = if (device.isOnline) copy.connectedStatus else copy.offlineSleeping
                    BoxlessDeviceRow(
                        name = cleanDeviceDisplayName(device.name),
                        details = statusText,
                        badge = device.osType,
                        isOnline = device.isOnline,
                        isConnected = true,
                        onClick = {
                            deviceForAction = device
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                }
            } else {
                Text(
                    text = copy.noDevicesOnNetwork,
                    color = Mist,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // SECTION 2: AĞDAKİ DİĞER CİHAZLAR (Available Devices)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { triggerScan() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.availableDevicesSection,
                color = Cloud,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = copy.formatDeviceCount(availableList.size),
                color = Mist,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            if (availableList.isNotEmpty()) {
                availableList.forEach { device ->
                    val statusText = if (device.isOnline) copy.deviceOnline else copy.outsideLocalNetwork
                    BoxlessDeviceRow(
                        name = cleanDeviceDisplayName(device.name),
                        details = statusText,
                        badge = device.osType,
                        isOnline = device.isOnline,
                        isConnected = false,
                        onClick = {
                            deviceToPair = device
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
                }
            } else {
                Text(
                    text = copy.noDevicesOnNetwork,
                    color = Mist,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // SECTION 2: ACTION BUTTONS (GÖNDER & ORTAK PANO)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gönder (Send Files Action)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .clickable(onClick = onSendClick),
                shape = RoundedCornerShape(26.dp),
                color = PillButtonBg
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    UploadIcon(Modifier.size(18.dp), color = PillButtonText)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = copy.send,
                        color = PillButtonText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Ortak Pano (Clipboard Action)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(onClick = onClipboardClick),
                    shape = RoundedCornerShape(26.dp),
                    color = if (isPro) InputBg else InputBg.copy(alpha = 0.6f),
                    border = if (!isPro) BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ClipboardVectorIcon(Modifier.size(18.dp), color = if (isPro) Blue else Mist)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = copy.clipboard,
                            color = if (isPro) Cloud else Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isPro) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-8).dp, y = (-5).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Blue)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = copy.proBadge,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(34.dp))

        // SECTION 3: SON AKTARIMLAR (Recent Transfers)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.recentTransfers,
                color = Cloud,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            if (recentTransfers.isNotEmpty()) {
                Text(
                    text = copy.viewAll,
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onViewAllTransfers)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Recent Transfer Items or Clean Empty State
        if (recentTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = copy.noTransfersYet,
                    color = Mist.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                recentTransfers.take(4).forEach { item ->
                    BoxlessTransferRow(
                        title = item.fileName,
                        info = "${if (item.isSent) copy.transfersSent else copy.transfersReceived} · ${item.formattedSize}",
                        isSent = item.isSent,
                        status = item.status
                    )
                    HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
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
                    val connectedCount = discoveredDevices.count { ConnectionManager.isDeviceConnected(it) }
                    if (!net.syncswift.subscription.SubscriptionManager.canAddDevice(connectedCount)) {
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
                            PairResult.PENDING_APPROVAL -> { /* OutgoingStatusModal displays approval waiting */ }
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
                    onDeviceClick(target)
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
private fun BoxlessDeviceRow(
    name: String,
    details: String,
    badge: String,
    isOnline: Boolean,
    isConnected: Boolean = false,
    isSelf: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isMobile = badge.equals("Android", ignoreCase = true) || badge.equals("iOS", ignoreCase = true)
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
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InputBg.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            if (isMobile) {
                PhoneVectorIcon(Modifier.size(19.dp), color = if (isConnected && isOnline) Blue else Mist)
            } else {
                LaptopVectorIcon(Modifier.size(19.dp), color = if (isConnected && isOnline) Blue else Mist)
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

@Composable
private fun BoxlessTransferRow(
    title: String,
    info: String,
    isSent: Boolean,
    status: TransferStatus = TransferStatus.COMPLETED
) {
    val isFailed = status == TransferStatus.FAILED || status == TransferStatus.CANCELLED
    val statusColor = if (isFailed) ErrorRed else SuccessGreen

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (isSent) {
                OutgoingArrowIcon(Modifier.size(18.dp), color = statusColor)
            } else {
                IncomingArrowIcon(Modifier.size(18.dp), color = statusColor)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = info,
                color = if (isFailed) ErrorRed else Mist,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        when (status) {
            TransferStatus.IN_PROGRESS -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Blue,
                    strokeWidth = 2.dp
                )
            }
            TransferStatus.COMPLETED -> {
                AnimatedCheckCircleIcon(Modifier.size(20.dp))
            }
            TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CrossVectorIcon(Modifier.size(10.dp), color = ErrorRed)
                }
            }
            else -> {}
        }
    }
}
