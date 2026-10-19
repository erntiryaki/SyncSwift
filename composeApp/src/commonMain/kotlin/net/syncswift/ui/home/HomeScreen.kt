package net.syncswift.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.transfer.TransferEngine
import net.syncswift.transfer.TransferItem
import net.syncswift.transfer.TransferStatus
import net.syncswift.ui.components.ConnectedDeviceActionScreen
import net.syncswift.ui.components.SendPairRequestScreen
import net.syncswift.SheetSystemBars
import net.syncswift.ui.components.rememberSheetDragState
import net.syncswift.ui.theme.*

enum class DeviceSectionType {
    NEARBY, CONNECTED, DISCONNECTED
}

data class UiDeviceItem(
    val id: String,
    val name: String,
    val os: String,
    val isOnline: Boolean,
    val section: DeviceSectionType,
    val realDevice: DiscoveredDevice? = null
)

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
    val isDark = LocalSyncSwiftColors.current.isDark
    val scope = rememberCoroutineScope()

    val discoveredDevices by DeviceDiscoveryManager.devices.collectAsState()
    val connectedKeys by ConnectionManager.connectedDeviceKeys.collectAsState()
    val allTransfers by TransferEngine.transfers.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterChip by remember { mutableStateOf(0) } // 0: Tümü, 1: Yakındaki, 2: Bağlı, 3: Bağlı Olmayan
    val searchFocusRequester = remember { FocusRequester() }

    var deviceToPair by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var deviceForAction by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var viewAllSection by remember { mutableStateOf<DeviceSectionType?>(null) }
    var isTransfersExpanded by remember { mutableStateOf(true) }

    val selfDevice = remember(discoveredDevices) { DeviceDiscoveryManager.getSelfDevice() }
    val myName = cleanDeviceDisplayName(selfDevice?.name ?: SessionManager.getMyDeviceName())
    val myRawName = selfDevice?.name ?: SessionManager.getMyDeviceName()
    val myId = SessionManager.getPlatformDeviceId()
    val myPushToken = SessionManager.getMyPushToken()
    val myLocalIp = selfDevice?.host ?: ""

    val isDeviceSelf: (DiscoveredDevice) -> Boolean = { dev ->
        val devClean = cleanDeviceDisplayName(dev.name)
        dev.isSelf ||
                (myId.isNotBlank() && dev.id.equals(myId, ignoreCase = true)) ||
                (myName.isNotBlank() && devClean.equals(myName, ignoreCase = true)) ||
                (myRawName.isNotBlank() && (devClean.equals(cleanDeviceDisplayName(myRawName), ignoreCase = true) || dev.name.equals(myRawName, ignoreCase = true))) ||
                (selfDevice != null && dev.id == selfDevice.id) ||
                (selfDevice != null && dev.id.removePrefix("self-") == selfDevice.id.removePrefix("self-")) ||
                (myLocalIp.isNotBlank() && myLocalIp != "127.0.0.1" && dev.host == myLocalIp) ||
                (selfDevice != null && selfDevice.host.isNotBlank() && selfDevice.host != "127.0.0.1" && dev.host == selfDevice.host) ||
                (myPushToken.isNotBlank() && !dev.pushToken.isNullOrBlank() && dev.pushToken == myPushToken)
    }

    val realOtherDevices = remember(discoveredDevices, connectedKeys) {
        discoveredDevices.filter { !isDeviceSelf(it) }.distinctBy {
            if (it.host.isNotBlank() && !it.host.startsWith("remote-")) it.host
            else (if (it.id.isNotBlank()) it.id else cleanDeviceDisplayName(it.name))
        }
    }

    val connectedDevices = remember(realOtherDevices, connectedKeys) {
        realOtherDevices.filter { dev ->
            val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                    connectedKeys.contains(dev.id) ||
                    connectedKeys.contains(dev.host) ||
                    connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                    ConnectionManager.isDeviceConnected(dev)
            val isRemote = dev.isRemote || ConnectionManager.isRemoteDevice(dev)
            isConn && !isRemote
        }.map { dev ->
            val devModel = if (dev.modelCode.isNotBlank()) dev.modelCode else dev.osType.ifBlank { "SyncSwift" }
            UiDeviceItem(
                id = dev.id.ifBlank { dev.host },
                name = cleanDeviceDisplayName(dev.name),
                os = devModel,
                isOnline = dev.isOnline,
                section = DeviceSectionType.CONNECTED,
                realDevice = dev
            )
        }
    }

    val nearbyDevices = remember(realOtherDevices, connectedKeys) {
        realOtherDevices.filter { dev ->
            val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                    connectedKeys.contains(dev.id) ||
                    connectedKeys.contains(dev.host) ||
                    connectedKeys.contains(cleanDeviceDisplayName(dev.name)) ||
                    ConnectionManager.isDeviceConnected(dev)
            val isRemote = dev.isRemote || ConnectionManager.isRemoteDevice(dev)
            !isConn && !isRemote
        }.map { dev ->
            val devModel = if (dev.modelCode.isNotBlank()) dev.modelCode else dev.osType.ifBlank { "SyncSwift" }
            UiDeviceItem(
                id = dev.id.ifBlank { dev.host },
                name = cleanDeviceDisplayName(dev.name),
                os = devModel,
                isOnline = dev.isOnline,
                section = DeviceSectionType.NEARBY,
                realDevice = dev
            )
        }
    }

    val disconnectedDevices = remember(realOtherDevices, connectedKeys) {
        realOtherDevices.filter { dev ->
            dev.isRemote || ConnectionManager.isRemoteDevice(dev) || dev.host.startsWith("remote-")
        }.map { dev ->
            val devModel = if (dev.modelCode.isNotBlank()) dev.modelCode else dev.osType.ifBlank { "SyncSwift" }
            UiDeviceItem(
                id = dev.id.ifBlank { dev.host },
                name = cleanDeviceDisplayName(dev.name),
                os = devModel,
                isOnline = dev.isOnline,
                section = DeviceSectionType.DISCONNECTED,
                realDevice = dev
            )
        }
    }

    val allDeviceItems = remember(nearbyDevices, connectedDevices, disconnectedDevices) {
        nearbyDevices + connectedDevices + disconnectedDevices
    }

    fun handleDeviceSelection(item: UiDeviceItem) {
        val targetDev = item.realDevice ?: DiscoveredDevice(
            id = item.id,
            name = item.name,
            host = "192.168.1.100",
            port = 52424,
            osType = item.os
        )
        deviceForAction = targetDev
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // TOP BAR
        if (!isSearchActive) {
            // Normal Top Bar (SyncSwift Brand Logo on Left, Search 🔍, Bell 🔔 with blue dot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BrandLogo(
                    modifier = Modifier.height(64.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { isSearchActive = true },
                        contentAlignment = Alignment.Center
                    ) {
                        SearchVectorIcon(Modifier.size(22.dp), color = Cloud)
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onOpenNotifications() },
                        contentAlignment = Alignment.Center
                    ) {
                        BellIcon(Modifier.size(22.dp), color = Cloud)
                        // Blue Badge Dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(LocalSyncSwiftColors.current.blue)
                        )
                    }
                }
            }
        } else {
            // Dynamic Search Top Bar (3rd Screen in Image 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF131B28) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, InputBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchVectorIcon(Modifier.size(18.dp), color = Mist)
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Cloud,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(LocalSyncSwiftColors.current.blue),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = copy.searchDevicesHint,
                                        color = Mist,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                CrossVectorIcon(Modifier.size(12.dp), color = Mist)
                            }
                        }
                    }
                }

                Text(
                    text = copy.otpCancelAction,
                    color = LocalSyncSwiftColors.current.blue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            searchQuery = ""
                            isSearchActive = false
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
        }

        // CONTENT AREA (Normal vs Search)
        if (!isSearchActive) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // SECTION 1: YAKINDAKİ CİHAZLAR (Horizontal Cards)
                SectionHeaderRow(
                    title = copy.nearbyDevicesSection,
                    count = nearbyDevices.size,
                    copy = copy,
                    onViewAllClick = { viewAllSection = DeviceSectionType.NEARBY }
                )

                Spacer(Modifier.height(12.dp))

                if (nearbyDevices.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        nearbyDevices.take(4).forEach { dev ->
                            NearbyDeviceCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { DeviceDiscoveryManager.triggerDiscoveryPing() },
                        shape = RoundedCornerShape(16.dp),
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
                                    .clip(CircleShape)
                                    .background(LocalSyncSwiftColors.current.blue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                NearbyDiscoveryIcon(Modifier.size(20.dp), color = LocalSyncSwiftColors.current.blue)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = copy.scanningNetworkDevices,
                                    color = Cloud,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = copy.openSyncSwiftOnOtherDevice,
                                    color = Mist,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // SECTION 2: YEREL BAĞLI CİHAZLAR
                SectionHeaderRow(
                    title = copy.connectedDevicesSectionTitle,
                    count = connectedDevices.size,
                    copy = copy,
                    onViewAllClick = { viewAllSection = DeviceSectionType.CONNECTED }
                )

                Spacer(Modifier.height(10.dp))

                if (connectedDevices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        connectedDevices.take(4).forEach { dev ->
                            DeviceListRowCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder)
                    ) {
                        Text(
                            text = copy.noConnectedDevicesYet,
                            color = Mist,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // SECTION 3: UZAKTAN BAĞLI CİHAZLAR
                SectionHeaderRow(
                    title = copy.disconnectedDevicesSectionTitle,
                    count = disconnectedDevices.size,
                    copy = copy,
                    onViewAllClick = { viewAllSection = DeviceSectionType.DISCONNECTED }
                )

                Spacer(Modifier.height(10.dp))

                if (disconnectedDevices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        disconnectedDevices.take(4).forEach { dev ->
                            DeviceListRowCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder)
                    ) {
                        Text(
                            text = copy.noDisconnectedDevicesYet,
                            color = Mist,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // SECTION 4: TWO LARGE ACTION BUTTONS (GÖNDER & ORTAK PANO)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Gönder (Solid Blue)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onSendClick),
                        shape = RoundedCornerShape(18.dp),
                        color = LocalSyncSwiftColors.current.blue
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                PaperAirplaneIcon(Modifier.size(18.dp), color = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = copy.send,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = copy.transferFilesSubtitle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Button 2: Ortak Pano (Card Surface)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onClipboardClick),
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(LocalSyncSwiftColors.current.blue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ClipboardVectorIcon(Modifier.size(18.dp), color = LocalSyncSwiftColors.current.blue)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = copy.sendClipboardAction,
                                    color = Cloud,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = copy.clipboardShareAndSync,
                                    color = Mist,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // SECTION 5: TRANSFER GEÇMİŞİ (Real transfers from TransferEngine - Expandable/Collapsible)
                val completedTransfers = remember(allTransfers) {
                    allTransfers.filter { it.status == TransferStatus.COMPLETED }.take(5)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isTransfersExpanded = !isTransfersExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = copy.transfersHistorySection,
                            color = Cloud,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp
                        )
                        ChevronDownIcon(
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (isTransfersExpanded) 0f else -90f),
                            color = Mist
                        )
                    }

                    Text(
                        text = copy.viewAll,
                        color = LocalSyncSwiftColors.current.blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onViewAllTransfers)
                            .padding(4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isTransfersExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(10.dp))

                        if (completedTransfers.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                completedTransfers.forEach { item ->
                                    val icon: @Composable () -> Unit = when {
                                        item.fileName.endsWith(".pdf", ignoreCase = true) || item.mimeType.contains("pdf", ignoreCase = true) -> {
                                            { FilePdfIcon(Modifier.size(38.dp)) }
                                        }
                                        item.fileName.endsWith(".zip", ignoreCase = true) || item.fileName.endsWith(".rar", ignoreCase = true) || item.mimeType.contains("zip", ignoreCase = true) -> {
                                            { FileZipIcon(Modifier.size(38.dp)) }
                                        }
                                        else -> {
                                            { FileImageIcon(Modifier.size(38.dp)) }
                                        }
                                    }
                                    val subtitle = if (item.peerName.isNotBlank()) cleanDeviceDisplayName(item.peerName)
                                    else if (item.isSent) copy.transfersSent else copy.transfersReceived

                                    TransferItemRowCard(
                                        icon = icon,
                                        title = item.fileName,
                                        subtitle = subtitle,
                                        time = if (currentTimeMs() - item.timestamp < 86400000L) copy.timeToday else copy.timeYesterday,
                                        size = item.formattedSize,
                                        onClick = onViewAllTransfers
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                color = CardSurface,
                                border = BorderStroke(1.dp, InputBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = copy.noTransfersYetDesc,
                                        color = Cloud,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = copy.sendFilePromptDesc,
                                        color = Mist,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        } else {
            // DYNAMIC SEARCH VIEW (Phone 3 in Image 2)
            val filteredDevices = remember(searchQuery, allDeviceItems) {
                if (searchQuery.isBlank()) allDeviceItems
                else {
                    allDeviceItems.filter { dev ->
                        dev.name.contains(searchQuery, ignoreCase = true) ||
                                dev.os.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val searchNearby = remember(filteredDevices) {
                filteredDevices.filter { it.section == DeviceSectionType.NEARBY }
            }
            val searchConnected = remember(filteredDevices) {
                filteredDevices.filter { it.section == DeviceSectionType.CONNECTED }
            }
            val searchDisconnected = remember(filteredDevices) {
                filteredDevices.filter { it.section == DeviceSectionType.DISCONNECTED }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // FILTER CHIPS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipPill(
                        label = copy.filterAll,
                        count = filteredDevices.size,
                        selected = selectedFilterChip == 0,
                        onClick = { selectedFilterChip = 0 }
                    )
                    FilterChipPill(
                        label = copy.filterNearby,
                        count = searchNearby.size,
                        selected = selectedFilterChip == 1,
                        onClick = { selectedFilterChip = 1 }
                    )
                    FilterChipPill(
                        label = copy.filterConnected,
                        count = searchConnected.size,
                        selected = selectedFilterChip == 2,
                        onClick = { selectedFilterChip = 2 }
                    )
                    FilterChipPill(
                        label = copy.filterDisconnected,
                        count = searchDisconnected.size,
                        selected = selectedFilterChip == 3,
                        onClick = { selectedFilterChip = 3 }
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 1. Nearby Search Results
                if ((selectedFilterChip == 0 || selectedFilterChip == 1) && searchNearby.isNotEmpty()) {
                    SearchSectionHeader(title = copy.nearbyDevicesSection, count = searchNearby.size)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchNearby.forEach { dev ->
                            DeviceListRowCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // 2. Connected Search Results
                if ((selectedFilterChip == 0 || selectedFilterChip == 2) && searchConnected.isNotEmpty()) {
                    SearchSectionHeader(title = copy.connectedDevicesSectionTitle, count = searchConnected.size)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchConnected.forEach { dev ->
                            DeviceListRowCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // 3. Disconnected Search Results
                if ((selectedFilterChip == 0 || selectedFilterChip == 3) && searchDisconnected.isNotEmpty()) {
                    SearchSectionHeader(title = copy.disconnectedDevicesSectionTitle, count = searchDisconnected.size)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchDisconnected.forEach { dev ->
                            DeviceListRowCard(
                                item = dev,
                                onClick = { handleDeviceSelection(dev) }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                if (filteredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = copy.noMatchingDevicesFound,
                            color = Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Pair request dialogs
    AnimatedVisibility(
        visible = deviceToPair != null,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val target = deviceToPair
        if (target != null) {
            SendPairRequestScreen(
                device = target,
                copy = copy,
                onSendRequest = {
                    deviceToPair = null
                    showNativeToast(copy.sendingConnectionRequest)
                    ConnectionManager.requestPairing(target) { result ->
                        when (result) {
                            PairResult.ACCEPTED -> showNativeToast(copy.connectionAccepted)
                            PairResult.REJECTED -> showNativeToast(copy.connectionRejected)
                            PairResult.EXPIRED_OR_USED -> showNativeToast(copy.codeExpiredOrAlreadyUsed)
                            PairResult.UNAVAILABLE -> showNativeToast(copy.deviceUnavailable)
                            PairResult.PENDING_APPROVAL -> {}
                            else -> {}
                        }
                    }
                },
                onDismiss = { deviceToPair = null }
            )
        }
    }

    // Device Action Bottom Sheet (Matching user screenshot)
    AnimatedVisibility(
        visible = deviceForAction != null,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val target = deviceForAction
        if (target != null) {
            ConnectedDeviceActionScreen(
                device = target,
                copy = copy,
                onSendFile = {
                    deviceForAction = null
                    onDeviceClick(target)
                },
                onSendClipboard = {
                    deviceForAction = null
                    onClipboardClick()
                },
                onDisconnect = {
                    deviceForAction = null
                    ConnectionManager.disconnectDevice(target)
                    showNativeToast(copy.disconnected)
                },
                onDismiss = {
                    deviceForAction = null
                }
            )
        }
    }

    // View All Bottom Sheet Modal (Triggered by 'Tümünü Gör')
    AnimatedVisibility(
        visible = viewAllSection != null,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        val section = viewAllSection
        if (section != null) {
            val (sheetTitle, sheetList) = when (section) {
                DeviceSectionType.NEARBY -> copy.nearbyDevicesSection to nearbyDevices
                DeviceSectionType.CONNECTED -> copy.connectedDevicesSectionTitle to connectedDevices
                DeviceSectionType.DISCONNECTED -> copy.disconnectedDevicesSectionTitle to disconnectedDevices
            }
            DeviceListBottomSheet(
                title = sheetTitle,
                devices = sheetList,
                copy = copy,
                onDeviceClick = { devItem ->
                    viewAllSection = null
                    handleDeviceSelection(devItem)
                },
                onDismiss = { viewAllSection = null }
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    count: Int,
    copy: Copy,
    onViewAllClick: () -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val blue = LocalSyncSwiftColors.current.blue
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Cloud,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    color = blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = copy.viewAll,
            color = blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onViewAllClick)
                .padding(4.dp)
        )
    }
}

@Composable
private fun DeviceListBottomSheet(
    title: String,
    devices: List<UiDeviceItem>,
    copy: Copy,
    onDeviceClick: (UiDeviceItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val handleDismiss: () -> Unit = {
        scope.launch {
            isVisible = false
            kotlinx.coroutines.delay(180)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SheetSystemBars(active = isVisible, isDark = isDark)

        val dragState = rememberSheetDragState(onDismiss = handleDismiss)

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = handleDismiss
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxSheetHeight = maxHeight * 0.92f

                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                            .then(dragState.sheetOffsetModifier)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                enabled = false
                            ) {},
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = if (isDark) Color(0xFF161F30) else Color(0xFFFFFFFF),
                        border = BorderStroke(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0xFFE2E8F0)),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp)
                                .padding(top = 14.dp, bottom = 26.dp)
                        ) {
                            // Drag handle
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(dragState.dragHandleModifier)
                                    .padding(top = 4.dp, bottom = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                                )
                            }

                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(dragState.dragHandleModifier)
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = devices.size.toString(),
                                            color = LocalSyncSwiftColors.current.blue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF222F43) else Color(0xFFF1F5F9))
                                        .clickable(onClick = handleDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CrossVectorIcon(
                                        Modifier.size(12.dp),
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = if (isDark) Color(0x18FFFFFF) else Color(0xFFE2E8F0)
                            )
                            Spacer(Modifier.height(14.dp))

                            if (devices.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    devices.forEach { dev ->
                                        DeviceListRowCard(
                                            item = dev,
                                            onClick = { onDeviceClick(dev) }
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = copy.noMatchingDevicesFound,
                                        color = Mist,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Cloud,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )
        Text(
            text = count.toString(),
            color = Mist,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun NearbyDeviceCard(
    item: UiDeviceItem,
    onClick: () -> Unit
) {
    val blue = LocalSyncSwiftColors.current.blue
    val isDark = LocalSyncSwiftColors.current.isDark
    Surface(
        modifier = Modifier
            .width(114.dp)
            .height(122.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, InputBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Online Green / Offline Gray Status Dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (item.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
            )

            // Centered circular icon container and device labels
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        item.os.contains("iOS", ignoreCase = true) || item.os.contains("Android", ignoreCase = true) -> {
                            MobileDeviceIcon(Modifier.size(24.dp), color = blue)
                        }
                        item.name.contains("iPad", ignoreCase = true) -> {
                            TabletVectorIcon(Modifier.size(24.dp), color = blue)
                        }
                        else -> {
                            LaptopVectorIcon(Modifier.size(26.dp), color = blue)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = item.name,
                    color = Cloud,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.os,
                    color = Mist,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DeviceListRowCard(
    item: UiDeviceItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, InputBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                val blue = LocalSyncSwiftColors.current.blue
                when {
                    item.name.contains("Windows", ignoreCase = true) -> {
                        WindowsLogoIcon(Modifier.size(24.dp), color = blue)
                    }
                    item.os.contains("Android", ignoreCase = true) || item.os.contains("iOS", ignoreCase = true) -> {
                        MobileDeviceIcon(Modifier.size(26.dp), color = blue)
                    }
                    item.name.contains("Ev Bilgisayarı", ignoreCase = true) || item.name.contains("mini", ignoreCase = true) -> {
                        DesktopVectorIcon(Modifier.size(26.dp), color = blue)
                    }
                    else -> {
                        LaptopVectorIcon(Modifier.size(28.dp), color = blue)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Cloud,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.os,
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Status Dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (item.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
            )

            Spacer(Modifier.width(10.dp))

            ChevronRightIcon(Modifier.size(15.dp), color = Mist)
        }
    }
}

@Composable
private fun TransferItemRowCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    time: String,
    size: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, InputBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Cloud,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = time,
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = size,
                    color = Mist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.width(10.dp))

            ChevronRightIcon(Modifier.size(15.dp), color = Mist)
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            Color(0xFF2563EB).copy(alpha = if (isDark) 0.35f else 0.15f)
        } else {
            CardSurface
        },
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF2563EB).copy(alpha = 0.6f) else InputBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = if (selected) Color(0xFF2563EB) else Mist,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = count.toString(),
                color = if (selected) Color(0xFF2563EB) else Mist.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
