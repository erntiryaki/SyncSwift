package net.syncswift.connection

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.PlatformConfig
import net.syncswift.SessionManager
import net.syncswift.SupabaseNetwork
import net.syncswift.currentTimeMs
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.darwin.*
import platform.posix.*
import kotlin.coroutines.resume
import kotlin.random.Random

import net.syncswift.currentAppCopy
import net.syncswift.showNativeToast

private fun htons(s: UShort): UShort = (((s.toInt() and 0xFF) shl 8) or ((s.toInt() ushr 8) and 0xFF)).toUShort()

private fun parseIpv4(ip: String): UInt? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    val b0 = parts[0].toUIntOrNull() ?: return null
    val b1 = parts[1].toUIntOrNull() ?: return null
    val b2 = parts[2].toUIntOrNull() ?: return null
    val b3 = parts[3].toUIntOrNull() ?: return null
    if (b0 > 255u || b1 > 255u || b2 > 255u || b3 > 255u) return null
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun buildPairRequestPacket(name: String, host: String, port: Int, os: String): ByteArray {
    val nameBytes = name.encodeToByteArray()
    val hostBytes = host.encodeToByteArray()
    val osBytes = os.encodeToByteArray()
    val size = 2 + (2 + nameBytes.size) + (2 + hostBytes.size) + 4 + (2 + osBytes.size)
    val buf = ByteArray(size)
    var idx = 0
    buf[idx++] = 1 // PROTOCOL_VERSION
    buf[idx++] = 3 // TYPE_PAIR_REQUEST
    // writeUTF(name)
    buf[idx++] = ((nameBytes.size shr 8) and 0xFF).toByte()
    buf[idx++] = (nameBytes.size and 0xFF).toByte()
    nameBytes.copyInto(buf, idx); idx += nameBytes.size
    // writeUTF(host)
    buf[idx++] = ((hostBytes.size shr 8) and 0xFF).toByte()
    buf[idx++] = (hostBytes.size and 0xFF).toByte()
    hostBytes.copyInto(buf, idx); idx += hostBytes.size
    // writeInt(port)
    buf[idx++] = ((port shr 24) and 0xFF).toByte()
    buf[idx++] = ((port shr 16) and 0xFF).toByte()
    buf[idx++] = ((port shr 8) and 0xFF).toByte()
    buf[idx++] = (port and 0xFF).toByte()
    // writeUTF(os)
    buf[idx++] = ((osBytes.size shr 8) and 0xFF).toByte()
    buf[idx++] = (osBytes.size and 0xFF).toByte()
    osBytes.copyInto(buf, idx); idx += osBytes.size
    return buf
}

private fun escapeJson(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> {
                if (ch.code < 32) {
                    val hex = ch.code.toString(16).padStart(4, '0')
                    sb.append("\\u$hex")
                } else {
                    sb.append(ch)
                }
            }
        }
    }
    return sb.toString()
}

private suspend fun sendPairRequestViaFcm(target: DiscoveredDevice, selfName: String, selfHost: String, selfPort: Int, selfOs: String): Boolean {
    var token = target.pushToken
    if (token.isNullOrBlank() && target.host.isNotBlank()) {
        token = ConnectionManager.queryPushTokenFromSupabase(target.host, target.name)
    }
    if (token.isNullOrBlank()) return false

    val targetCopy = net.syncswift.copyForLanguageCode(target.language)
    val self = DeviceDiscoveryManager.getSelfDevice()
    val selfId = platform.UIKit.UIDevice.currentDevice.identifierForVendor?.UUIDString ?: (self?.id ?: "ios-device")
    val myPushToken = net.syncswift.getSavedIosPushToken() ?: ""
    val isRemoteTarget = target.isRemote || target.host.startsWith("remote-") ||
        DeviceDiscoveryManager.devices.value.none { it.host == target.host && it.isOnline && !it.isRemote }

    return ConnectionManager.sendFcmNotification(
        token = token,
        title = targetCopy.notificationPairRequestTitle,
        body = targetCopy.notificationPairRequestBody.replace("%s", selfName),
        data = mapOf(
            "type" to "pair_request",
            "senderId" to selfId,
            "senderName" to selfName,
            "senderHost" to selfHost,
            "senderPort" to "$selfPort",
            "senderOs" to selfOs,
            "senderPushToken" to myPushToken,
            "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
            "isRemote" to "$isRemoteTarget",
            "timestamp" to "${currentTimeMs()}"
        )
    )
}

actual object ConnectionManager {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("[SyncSwift iOS ConnectionManager] Coroutine error caught: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    private val _connectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val connectedDeviceKeys: StateFlow<Set<String>> = _connectedDeviceKeys.asStateFlow()

    private val _remoteConnectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val remoteConnectedDeviceKeys: StateFlow<Set<String>> = _remoteConnectedDeviceKeys.asStateFlow()

    private val _pendingPairRequest = MutableStateFlow<IncomingPairRequest?>(null)
    actual val pendingPairRequest: StateFlow<IncomingPairRequest?> = _pendingPairRequest.asStateFlow()

    fun dispatchIncomingPairRequest(request: IncomingPairRequest) {
        dispatch_async(dispatch_get_main_queue()) {
            _pendingPairRequest.value = request
        }
    }

    private val _pendingTransferRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    actual val pendingTransferRequest: StateFlow<IncomingTransferRequest?> = _pendingTransferRequest.asStateFlow()

    fun dispatchIncomingTransferRequest(request: IncomingTransferRequest) {
        dispatch_async(dispatch_get_main_queue()) {
            _pendingTransferRequest.value = request
        }
    }

    private val _activePairingCode = MutableStateFlow("834 291")
    actual val activePairingCode: StateFlow<String> = _activePairingCode.asStateFlow()

    private val _codeRemainingSeconds = MutableStateFlow(60)
    actual val codeRemainingSeconds: StateFlow<Int> = _codeRemainingSeconds.asStateFlow()

    private var codeRotationJob: Job? = null

    init {
        val saved = SessionManager.getSavedConnectedDeviceKeys()
        val savedRemote = SessionManager.getSavedRemoteConnectedDeviceKeys()
        if (saved.isNotEmpty()) _connectedDeviceKeys.value = saved
        if (savedRemote.isNotEmpty()) _remoteConnectedDeviceKeys.value = savedRemote
    }

    private fun deviceKey(host: String, port: Int): String = "$host:$port"

    actual fun isDeviceConnected(device: DiscoveredDevice): Boolean {
        val key = deviceKey(device.host, device.port)
        val clean = cleanDeviceDisplayName(device.name)
        val set = _connectedDeviceKeys.value + _remoteConnectedDeviceKeys.value
        return set.contains(key) ||
               set.contains(device.id) ||
               (device.host.isNotBlank() && set.contains(device.host)) ||
               (clean.isNotBlank() && set.contains(clean)) ||
               (device.name.isNotBlank() && set.contains(device.name))
    }

    actual fun isRemoteDevice(device: DiscoveredDevice): Boolean {
        if (device.isRemote || device.id.startsWith("remote-") || device.host.startsWith("remote-")) {
            return true
        }
        val host = device.host.trim()
        val key = deviceKey(host, device.port)
        val clean = cleanDeviceDisplayName(device.name).trim()
        val remoteKeys = _remoteConnectedDeviceKeys.value
        if (remoteKeys.contains(key) ||
            remoteKeys.contains(device.id) ||
            (host.isNotBlank() && remoteKeys.contains(host)) ||
            (clean.isNotBlank() && remoteKeys.contains(clean)) ||
            (device.name.isNotBlank() && remoteKeys.contains(device.name))) {
            return true
        }

        val isLocalIp = host.isNotBlank() &&
                (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.") || host.startsWith("127."))
        if (isLocalIp) {
            return false
        }
        return false
    }

    private val cancelledRequestIds = mutableSetOf<String>()

    actual fun isRequestCancelled(requestId: String): Boolean {
        if (requestId.isBlank()) return false
        return cancelledRequestIds.contains(requestId)
    }

    actual fun markRequestCancelled(requestId: String) {
        if (requestId.isNotBlank()) {
            cancelledRequestIds.add(requestId)
        }
    }

    actual fun markDeviceConnected(deviceKey: String, isRemote: Boolean) {
        if (isRequestCancelled(deviceKey)) {
            println("[SyncSwift iOS] Skipping markDeviceConnected for cancelled device: $deviceKey")
            return
        }
        val clean = cleanDeviceDisplayName(deviceKey)
        val keysToAdd = setOf(deviceKey, clean).filter { it.isNotBlank() }
        val newConnected = _connectedDeviceKeys.value + keysToAdd
        _connectedDeviceKeys.value = newConnected
        SessionManager.saveConnectedDeviceKeys(newConnected)
        if (isRemote) {
            val newRemote = _remoteConnectedDeviceKeys.value + keysToAdd
            _remoteConnectedDeviceKeys.value = newRemote
            SessionManager.saveRemoteConnectedDeviceKeys(newRemote)
        }
    }

    actual fun markDeviceDisconnected(deviceKey: String) {
        val clean = cleanDeviceDisplayName(deviceKey)
        val keysToRemove = setOf(deviceKey, clean).filter { it.isNotBlank() }
        val newConnected = _connectedDeviceKeys.value - keysToRemove
        _connectedDeviceKeys.value = newConnected
        SessionManager.saveConnectedDeviceKeys(newConnected)
        val newRemote = _remoteConnectedDeviceKeys.value - keysToRemove
        _remoteConnectedDeviceKeys.value = newRemote
        SessionManager.saveRemoteConnectedDeviceKeys(newRemote)
    }

    suspend fun queryPushTokenFromSupabase(host: String, name: String): String? = suspendCancellableCoroutine { continuation ->
        try {
            val cleanName = cleanDeviceDisplayName(name)
            val supabaseUrl = PlatformConfig.supabaseUrl
            val anonKey = PlatformConfig.supabaseAnonKey
            val encodedName = (cleanName as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: cleanName
            val queryParam = if (host.isNotBlank() && !host.startsWith("remote-")) {
                val encodedHost = (host as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: host
                "or=(local_ip.eq.$encodedHost,device_name.eq.$encodedName)"
            } else {
                "device_name=eq.$encodedName"
            }
            val url = NSURL.URLWithString("$supabaseUrl/rest/v1/devices?select=push_token&$queryParam&limit=1")
            if (url == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val req = NSMutableURLRequest.requestWithURL(url).apply {
                setHTTPMethod("GET")
                setTimeoutInterval(6.0)
                setValue(anonKey, forHTTPHeaderField = "apikey")
                setValue("Bearer $anonKey", forHTTPHeaderField = "Authorization")
            }

            val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, err ->
                try {
                    if (err == null && data != null) {
                        val body = NSString.create(data, NSUTF8StringEncoding)?.toString() ?: ""
                        val match = Regex(""""push_token"\s*:\s*"([^"]+)"""").find(body)
                        val resultToken = match?.groupValues?.get(1)?.ifBlank { null }
                        if (continuation.isActive) continuation.resume(resultToken)
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                    }
                } catch (_: Throwable) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    suspend fun sendFcmNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        if (token.isBlank()) return false
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = NSURL.URLWithString("https://api.syncswift.net/api/fcm/send")
                if (url == null) {
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
                val req = NSMutableURLRequest.requestWithURL(url).apply {
                    setHTTPMethod("POST")
                    setTimeoutInterval(8.0)
                    setValue("application/json", forHTTPHeaderField = "Content-Type")
                }

                val dataPairs = data.entries.joinToString(",") { (k, v) ->
                    "\"${escapeJson(k)}\": \"${escapeJson(v)}\""
                }
                val jsonBody = """{"targetToken":"${escapeJson(token)}","token":"${escapeJson(token)}","title":"${escapeJson(title)}","body":"${escapeJson(body)}","data":{$dataPairs}}"""
                val bodyData = (jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                req.setHTTPBody(bodyData)

                val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, err ->
                    try {
                        val success = if (err == null && resp != null) {
                            val http = resp as? NSHTTPURLResponse
                            (http?.statusCode?.toInt() ?: 0) in 200..299
                        } else false
                        if (continuation.isActive) continuation.resume(success)
                    } catch (_: Throwable) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                continuation.invokeOnCancellation { task.cancel() }
                task.resume()
            } catch (_: Throwable) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    fun syncDeviceWithSupabase(pushToken: String) {
        if (pushToken.isBlank()) return
        scope.launch(Dispatchers.Default) {
            try {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val deviceId = SessionManager.getPlatformDeviceId()
                val cleanName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                val localIp = self?.host ?: ""
                val email = SessionManager.getLoggedInEmail() ?: ""
                val userId = SessionManager.getLoggedInUserId() ?: if (email.isNotBlank()) SupabaseNetwork.getUserId(email) else null

                val success = SupabaseNetwork.registerDeviceInSupabase(
                    email = email,
                    deviceId = deviceId,
                    deviceName = cleanName,
                    deviceType = "iOS",
                    pushToken = pushToken,
                    localIp = localIp,
                    port = 52424,
                    userId = userId
                )
                println("[SyncSwift iOS] Registered device in Supabase: $success")
            } catch (e: Exception) {
                println("[SyncSwift iOS] Failed to sync iOS device with Supabase: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit) {
        val copy = currentAppCopy()
        val self = DeviceDiscoveryManager.getSelfDevice()
        val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
        val selfHost = self?.host ?: ""
        val selfPort = self?.port ?: 52424
        val selfOs = "iOS"

        var activeSocket: Int = -1
        var isCancelled = false
        var pairJob: Job? = null

        val cancelPairing: () -> Unit = {
            isCancelled = true
            pairJob?.cancel()
            OutgoingStatusManager.dismiss()
            if (activeSocket >= 0) {
                val cancelByte = byteArrayOf(10)
                cancelByte.usePinned { pinned ->
                    send(activeSocket, pinned.addressOf(0), 1.convert(), 0)
                }
                close(activeSocket)
                activeSocket = -1
            }
            markRequestCancelled(target.id)
            markRequestCancelled(target.host)
            markRequestCancelled(deviceKey(target.host, target.port))
            markDeviceDisconnected(deviceKey(target.host, target.port))
            markDeviceDisconnected(target.id)
            DeviceDiscoveryManager.removeManualDevice(target.host)

            scope.launch(Dispatchers.Default) {
                val token = if (target.pushToken?.isNotBlank() == true) target.pushToken else queryPushTokenFromSupabase(target.host, target.name)
                if (!token.isNullOrBlank()) {
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                    val selfHost = self?.host ?: ""
                    val selfPort = self?.port ?: 52424
                    val selfId = self?.id ?: UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""
                    sendFcmNotification(
                        token = token,
                        title = "",
                        body = "",
                        data = mapOf(
                            "type" to "pair_cancelled",
                            "senderName" to selfName,
                            "senderHost" to selfHost,
                            "senderPort" to "$selfPort",
                            "senderId" to selfId
                        )
                    )
                }
            }
        }

        OutgoingStatusManager.show(
            type = OutgoingStatusType.CONNECTION,
            deviceName = target.name,
            step = OutgoingStep.WAITING_APPROVAL,
            message = copy.waitingForRecipientApproval,
            onCancel = cancelPairing
        )

        pairJob = scope.launch(Dispatchers.Default) {
            val isLocalDiscovered = target.isOnline && !target.isRemote && !target.host.startsWith("remote-") &&
                    !isRemoteDevice(target) && DeviceDiscoveryManager.devices.value.any { it.host == target.host && it.isOnline && !it.isRemote }

            // 1. Try direct local TCP socket pairing only if discovered on local LAN
            if (isLocalDiscovered && target.host.isNotBlank() && target.host != "127.0.0.1") {
                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock >= 0) {
                    activeSocket = sock
                    val connected = memScoped {
                        val timeout = alloc<timeval>()
                        timeout.tv_sec = 2
                        timeout.tv_usec = 0
                        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                        val nosigpipe = alloc<IntVar>()
                        nosigpipe.value = 1
                        setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                        val serverAddr = alloc<sockaddr_in>()
                        serverAddr.sin_family = AF_INET.convert()
                        serverAddr.sin_port = htons(target.port.toUShort())
                        val parsedIp = parseIpv4(target.host)
                        if (parsedIp != null) {
                            serverAddr.sin_addr.s_addr = parsedIp.convert()
                            if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                                val packet = buildPairRequestPacket(selfName, selfHost, selfPort, selfOs)
                                val sent = packet.usePinned { pinned ->
                                    send(sock, pinned.addressOf(0), packet.size.convert(), 0)
                                }
                                if (sent > 0) {
                                    val resp = ByteArray(1)
                                    val r = resp.usePinned { recv(sock, it.addressOf(0), 1.convert(), 0) }
                                    close(sock)
                                    activeSocket = -1
                                    if (r > 0 && (resp[0] == 6.toByte() || resp[0] == 7.toByte())) {
                                        val key = deviceKey(target.host, target.port)
                                        val isRemote = target.isRemote || isRemoteDevice(target)
                                        markDeviceConnected(key, isRemote = isRemote)
                                        markDeviceConnected(target.id, isRemote = isRemote)
                                        markDeviceConnected(target.host, isRemote = isRemote)
                                        markDeviceConnected(target.name, isRemote = isRemote)
                                        markDeviceConnected(cleanDeviceDisplayName(target.name), isRemote = isRemote)
                                        DeviceDiscoveryManager.addOrUpdateConnectedDevice(target)
                                        OutgoingStatusManager.completeSuccess(copy.connectionAccepted)
                                        withContext(Dispatchers.Main) { onResult(PairResult.ACCEPTED) }
                                        return@memScoped true
                                    } else if (r > 0 && resp[0] == 8.toByte()) {
                                        OutgoingStatusManager.fail(copy.connectionRequestRejected)
                                        withContext(Dispatchers.Main) { onResult(PairResult.REJECTED) }
                                        return@memScoped true
                                    }
                                }
                            }
                        }
                        close(sock)
                        activeSocket = -1
                        false
                    }
                    if (connected) return@launch
                }
            }

            if (isCancelled || !isActive) return@launch

            // 2. Cross-network FCM Relay fallback or direct
            val fcmSent = sendPairRequestViaFcm(target, selfName, selfHost, selfPort, selfOs)
            if (isCancelled || !isActive) return@launch

            if (fcmSent) {
                OutgoingStatusManager.show(
                    type = OutgoingStatusType.CONNECTION,
                    deviceName = target.name,
                    step = OutgoingStep.WAITING_APPROVAL,
                    message = copy.transferNotificationSentWaiting,
                    onCancel = cancelPairing
                )
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.transferNotificationSentWaiting)
                    onResult(PairResult.PENDING_APPROVAL)
                }
                return@launch
            }

            // 3. Fallback: Neither direct TCP nor FCM worked
            OutgoingStatusManager.fail(copy.deviceDidNotRespond)
            withContext(Dispatchers.Main) {
                onResult(PairResult.UNAVAILABLE)
            }
        }
    }

    private fun writeUTFBytes(str: String): ByteArray {
        val bytes = str.encodeToByteArray()
        val buf = ByteArray(2 + bytes.size)
        buf[0] = ((bytes.size shr 8) and 0xFF).toByte()
        buf[1] = (bytes.size and 0xFF).toByte()
        bytes.copyInto(buf, 2)
        return buf
    }

    private fun buildUnpairPacket(selfHost: String, selfPort: Int, selfId: String, selfName: String): ByteArray {
        val header = byteArrayOf(1 /* PROTOCOL_VERSION */, 4 /* TYPE_UNPAIR */)
        val hostBytes = writeUTFBytes(selfHost)
        val portBytes = ByteArray(4)
        portBytes[0] = ((selfPort shr 24) and 0xFF).toByte()
        portBytes[1] = ((selfPort shr 16) and 0xFF).toByte()
        portBytes[2] = ((selfPort shr 8) and 0xFF).toByte()
        portBytes[3] = (selfPort and 0xFF).toByte()
        val idBytes = writeUTFBytes(selfId)
        val nameBytes = writeUTFBytes(selfName)
        return header + hostBytes + portBytes + idBytes + nameBytes
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun disconnectDevice(target: DiscoveredDevice, notifyPeer: Boolean) {
        val key = deviceKey(target.host, target.port)
        val clean = cleanDeviceDisplayName(target.name).trim()
        val targetHost = target.host.trim()
        val targetName = target.name.trim()
        val targetId = target.id.trim()

        // Collect all matching devices from discovery list to ensure every identifier is purged
        val matchingDevices = DeviceDiscoveryManager.devices.value.filter { dev ->
            (targetId.isNotBlank() && dev.id.equals(targetId, ignoreCase = true)) ||
            (targetHost.isNotBlank() && dev.host.isNotBlank() && dev.host.equals(targetHost, ignoreCase = true)) ||
            (clean.isNotBlank() && cleanDeviceDisplayName(dev.name).trim().equals(clean, ignoreCase = true)) ||
            (targetName.isNotBlank() && dev.name.trim().equals(targetName, ignoreCase = true))
        }

        val allHosts = (listOf(targetHost) + matchingDevices.map { it.host.trim() }).filter { it.isNotBlank() }.toSet()
        val allNames = (listOf(targetName, clean) + matchingDevices.flatMap { listOf(it.name.trim(), cleanDeviceDisplayName(it.name).trim()) }).filter { it.isNotBlank() }.toSet()
        val allIds = (listOf(targetId, key, "manual-$targetHost", "remote-$clean") + matchingDevices.flatMap { listOf(it.id.trim(), deviceKey(it.host, it.port), "manual-${it.host.trim()}") }).filter { it.isNotBlank() }.toSet()

        fun isKeyMatchingTarget(existingKey: String): Boolean {
            val k = existingKey.trim()
            val kClean = cleanDeviceDisplayName(k).trim()
            if (allIds.any { it.isNotBlank() && (k.equals(it, ignoreCase = true) || k.contains(it, ignoreCase = true)) }) return true
            if (allNames.any { it.isNotBlank() && (k.equals(it, ignoreCase = true) || kClean.equals(it, ignoreCase = true)) }) return true
            if (allHosts.any { host -> host.isNotBlank() && (k.equals(host, ignoreCase = true) || k.contains(host, ignoreCase = true)) }) return true
            return false
        }

        val newConnected = _connectedDeviceKeys.value.filterNot { isKeyMatchingTarget(it) }.toSet()
        _connectedDeviceKeys.value = newConnected
        SessionManager.saveConnectedDeviceKeys(newConnected)

        val newRemote = _remoteConnectedDeviceKeys.value.filterNot { isKeyMatchingTarget(it) }.toSet()
        _remoteConnectedDeviceKeys.value = newRemote
        SessionManager.saveRemoteConnectedDeviceKeys(newRemote)

        // Retain devices in DeviceDiscoveryManager so they drop into "Ağdaki Diğer Cihazlar" or "Uzaktan / Çevrimdışı Cihazlar" without disappearing
        val hasLocalHost = target.host.isNotBlank() && !target.host.startsWith("remote-")
        val isRemoteDev = target.isRemote || target.host.startsWith("remote-") || target.id.startsWith("remote-") || isRemoteDevice(target)
        val devToKeep = (if (hasLocalHost) target else matchingDevices.firstOrNull()) ?: target

        DeviceDiscoveryManager.addOrUpdateConnectedDevice(
            devToKeep.copy(
                isOnline = if (isRemoteDev) false else true,
                isRemote = isRemoteDev
            )
        )
        DeviceDiscoveryManager.triggerDiscoveryPing()

        if (notifyPeer) {
            scope.launch(Dispatchers.Default) {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = self?.id ?: ""
                val selfName = self?.name ?: ""

                // 1. Try local TCP unpair packet
                if (target.host.isNotBlank() && !target.host.startsWith("remote-") && target.port > 0) {
                    val sock = socket(AF_INET, SOCK_STREAM, 0)
                    if (sock >= 0) {
                        try {
                            memScoped {
                                val timeout = alloc<timeval>()
                                timeout.tv_sec = 2
                                timeout.tv_usec = 0
                                setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                                val nosigpipe = alloc<IntVar>()
                                nosigpipe.value = 1
                                setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                                val serverAddr = alloc<sockaddr_in>()
                                serverAddr.sin_family = AF_INET.convert()
                                serverAddr.sin_port = htons(target.port.toUShort())
                                val parsedIp = parseIpv4(target.host)
                                if (parsedIp != null) {
                                    serverAddr.sin_addr.s_addr = parsedIp.convert()
                                    if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                                        val packet = buildUnpairPacket(selfHost, selfPort, selfId, selfName)
                                        packet.usePinned { pinned ->
                                            send(sock, pinned.addressOf(0), packet.size.convert(), 0)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            close(sock)
                        }
                    }
                }

                // 2. Try remote FCM unpair push notification
                try {
                    val targetPushToken = (target.pushToken ?: "").ifBlank {
                        queryPushTokenFromSupabase(target.host, target.name) ?: ""
                    }
                    if (targetPushToken.isNotBlank()) {
                        sendFcmNotification(
                            token = targetPushToken,
                            title = "",
                            body = "",
                            data = mapOf(
                                "type" to "unpair",
                                "senderHost" to selfHost,
                                "senderPort" to "$selfPort",
                                "senderId" to selfId,
                                "senderName" to selfName
                            )
                        )
                    }
                } catch (e: Exception) {
                    println("[SyncSwift iOS] Unpair push error: ${e.message}")
                }
            }
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun notifyRemoteLogout(target: DiscoveredDevice) {
        if (target.host.isBlank() || target.host.startsWith("remote-") || target.port <= 0) return
        val host = target.host
        val port = target.port
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            memScoped {
                val ipNum = parseIpv4(host) ?: return@memScoped
                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock < 0) return@memScoped
                try {
                    val tv = alloc<timeval>()
                    tv.tv_sec = 2
                    tv.tv_usec = 0
                    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
                    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, tv.ptr, sizeOf<timeval>().convert())

                    val serverAddr = alloc<sockaddr_in>()
                    serverAddr.sin_family = AF_INET.convert()
                    serverAddr.sin_port = htons(port.toUShort())
                    serverAddr.sin_addr.s_addr = ipNum

                    if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                        val header = byteArrayOf(1 /* PROTOCOL_VERSION */, 10 /* TYPE_REMOTE_LOGOUT */)
                        header.usePinned { pinned ->
                            send(sock, pinned.addressOf(0), header.size.convert(), 0)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    close(sock)
                }
            }
        }
    }

    actual fun acceptPairRequest(request: IncomingPairRequest) {
        val key = deviceKey(request.senderHost, request.senderPort)
        if (_pendingPairRequest.value?.requestId != request.requestId ||
            isRequestCancelled(key) ||
            isRequestCancelled(request.senderId) ||
            isRequestCancelled(request.senderHost)) {
            _pendingPairRequest.value = null
            request.deferredResponse.complete(false)
            showNativeToast(currentAppCopy().requestCancelledBySender)
            return
        }
        _pendingPairRequest.value = null
        val completed = request.deferredResponse.complete(true)
        if (!completed) {
            showNativeToast(currentAppCopy().requestCancelledBySender)
            return
        }
        val matchedDev = DeviceDiscoveryManager.devices.value.firstOrNull {
            it.host == request.senderHost || cleanDeviceDisplayName(it.name) == cleanDeviceDisplayName(request.senderName)
        }
        val isLocalIp = request.senderHost.isNotBlank() &&
                !request.senderHost.startsWith("remote-") &&
                (request.senderHost.startsWith("192.168.") || request.senderHost.startsWith("10.") || request.senderHost.startsWith("172.") || request.senderHost.startsWith("127."))
        val isRemote = when {
            request.isRemote -> true
            isLocalIp -> false
            matchedDev != null -> matchedDev.isRemote
            request.senderHost.startsWith("remote-") -> true
            request.senderHost.isBlank() || !request.senderHost.contains(".") -> true
            else -> false
        }
        markDeviceConnected(key, isRemote = isRemote)
        markDeviceConnected(request.senderId, isRemote = isRemote)
        markDeviceConnected(request.senderHost, isRemote = isRemote)
        markDeviceConnected(request.senderName, isRemote = isRemote)
        markDeviceConnected(cleanDeviceDisplayName(request.senderName), isRemote = isRemote)
        if (matchedDev != null) {
            markDeviceConnected(matchedDev.id, isRemote = isRemote)
            markDeviceConnected(matchedDev.host, isRemote = isRemote)
        } else if (isRemote || request.senderHost.isNotBlank()) {
            DeviceDiscoveryManager.addManualDevice(
                host = request.senderHost.ifBlank { "remote-${cleanDeviceDisplayName(request.senderName)}" },
                port = request.senderPort,
                name = request.senderName,
                osType = request.senderOsType
            )
        }

        scope.launch(Dispatchers.Default) {
            val senderToken = if (request.senderPushToken.isNotBlank()) request.senderPushToken else queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "ios-device"
                val myPushToken = net.syncswift.getSavedIosPushToken() ?: ""
                sendFcmNotification(
                    token = senderToken,
                    title = targetCopy.connectionAccepted,
                    body = targetCopy.notificationPairAcceptedStatus.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_accepted",
                        "senderName" to selfName,
                        "senderHost" to selfHost,
                        "senderPort" to "$selfPort",
                        "senderId" to selfId,
                        "senderPushToken" to myPushToken,
                        "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                        "isRemote" to "$isRemote"
                    )
                )
            }
        }
    }

    actual fun rejectPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = null
        request.deferredResponse.complete(false)

        scope.launch(Dispatchers.Default) {
            val senderToken = if (request.senderPushToken.isNotBlank()) {
                request.senderPushToken
            } else {
                queryPushTokenFromSupabase(request.senderHost, request.senderName)
            }
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "ios-device"
                sendFcmNotification(
                    token = senderToken,
                    title = targetCopy.connectionRejected,
                    body = targetCopy.notificationPairRejectedStatus.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_rejected",
                        "senderName" to selfName,
                        "senderHost" to selfHost,
                        "senderPort" to "$selfPort",
                        "senderId" to selfId,
                        "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode())
                    )
                )
            }
        }
    }

    actual fun acceptTransferRequest(request: IncomingTransferRequest) {
        if (_pendingTransferRequest.value?.requestId != request.requestId) {
            showNativeToast(currentAppCopy().transferCancelledBySender)
            return
        }
        _pendingTransferRequest.value = null
        val completed = request.deferredResponse.complete(true)
        if (!completed) {
            showNativeToast(currentAppCopy().transferCancelledBySender)
            return
        }
        if (request.mimeType == "text/plain") {
            val clipText = request.downloadUrl
            val copy = currentAppCopy()
            net.syncswift.transfer.TransferEngine.applyIncomingClipboard(clipText)
            showNativeToast(copy.clipboardCopiedSuccess)
            scope.launch(Dispatchers.Default) {
                val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
                if (!senderToken.isNullOrBlank()) {
                    val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: platform.UIKit.UIDevice.currentDevice.name)
                    sendFcmNotification(
                        token = senderToken,
                        title = targetCopy.syncClipboardAction,
                        body = targetCopy.clipboardSentSuccess.replace("%s", clipText.take(30)),
                        data = mapOf(
                            "type" to "clipboard_accepted",
                            "preview" to clipText.take(30),
                            "transferId" to request.transferId,
                            "senderName" to selfName
                        )
                    )
                }
            }
        } else if (request.isRemote && request.downloadUrl.isNotBlank()) {
            net.syncswift.transfer.TransferEngine.downloadRemoteFile(
                downloadUrl = request.downloadUrl,
                fileName = request.fileName,
                fileSize = request.fileSize,
                mimeType = request.mimeType,
                transferId = request.transferId,
                senderName = request.senderName
            )
        } else if (request.senderHost.isNotBlank() && request.transferId.isNotBlank()) {
            net.syncswift.transfer.TransferEngine.downloadStagedFile(
                senderHost = request.senderHost,
                senderPort = request.senderPort,
                senderName = request.senderName,
                transferId = request.transferId,
                fileName = request.fileName,
                fileSize = request.fileSize,
                mimeType = request.mimeType
            )
        }
    }

    actual fun rejectTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = null
        request.deferredResponse.complete(false)
        scope.launch(Dispatchers.Default) {
            val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: platform.UIKit.UIDevice.currentDevice.name)
                val type = if (request.mimeType == "text/plain") "clipboard_rejected" else "file_rejected"
                val title = if (request.mimeType == "text/plain") targetCopy.operationRejectedOrTimeout else targetCopy.transferRejectedByRecipient
                val body = if (request.mimeType == "text/plain") targetCopy.operationRejectedOrTimeout else targetCopy.notificationFileRejectedStatus.replace("%s", selfName)
                sendFcmNotification(
                    token = senderToken,
                    title = title,
                    body = body,
                    data = mapOf(
                        "type" to type,
                        "transferId" to request.transferId,
                        "senderName" to selfName,
                        "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode())
                    )
                )
            }
        }
    }

    actual fun cancelIncomingPairRequest() {
        dispatch_async(dispatch_get_main_queue()) {
            _pendingPairRequest.value?.deferredResponse?.complete(false)
            _pendingPairRequest.value = null
        }
    }

    actual fun cancelIncomingTransferRequest(transferId: String?) {
        dispatch_async(dispatch_get_main_queue()) {
            val current = _pendingTransferRequest.value
            if (transferId == null || current?.transferId == transferId || current?.requestId == transferId) {
                current?.deferredResponse?.complete(false)
                _pendingTransferRequest.value = null
            }
        }
    }

    fun stopPairingCodeRotation() {
        codeRotationJob?.cancel()
        codeRotationJob = null
    }

    actual fun generateOrRotatePairingCode() {
        val part1 = Random.nextInt(100, 999)
        val part2 = Random.nextInt(100, 999)
        val formatted = "$part1 $part2"
        val rawCode = "$part1$part2"
        _activePairingCode.value = formatted
        _codeRemainingSeconds.value = 60

        codeRotationJob?.cancel()
        codeRotationJob = scope.launch {
            try {
                publishPairingCodeToBackend(rawCode)
                while (isActive) {
                    delay(1000)
                    val remaining = _codeRemainingSeconds.value - 1
                    if (remaining <= 0) {
                        generateOrRotatePairingCode()
                        break
                    } else {
                        _codeRemainingSeconds.value = remaining
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    actual fun ensurePairingCodeActive() {
        if (codeRotationJob == null || codeRotationJob?.isActive != true || _codeRemainingSeconds.value <= 0) {
            generateOrRotatePairingCode()
        }
    }

    private fun publishPairingCodeToBackend(code: String) {
        scope.launch {
            try {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name).replace("\"", "")
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: NSUUID().UUIDString
                val pushToken = net.syncswift.getSavedIosPushToken() ?: ""
                val currentLang = SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()
                val supabaseUrl = PlatformConfig.supabaseUrl
                val serviceKey = PlatformConfig.supabaseServiceRoleKey.ifBlank { PlatformConfig.supabaseAnonKey }

                val url = NSURL.URLWithString("$supabaseUrl/rest/v1/pairing_codes") ?: return@launch
                val req = NSMutableURLRequest.requestWithURL(url)
                req.setHTTPMethod("POST")
                req.setValue("application/json", forHTTPHeaderField = "Content-Type")
                req.setValue(serviceKey, forHTTPHeaderField = "apikey")
                req.setValue("Bearer $serviceKey", forHTTPHeaderField = "Authorization")
                req.setValue("resolution=merge-duplicates", forHTTPHeaderField = "Prefer")

                val jsonBody = """
                    {
                        "code": "$code",
                        "device_id": "$selfId",
                        "device_name": "$selfName",
                        "device_type": "iOS",
                        "push_token": "$pushToken",
                        "host": "$selfHost",
                        "port": $selfPort
                    }
                """.trimIndent()
                val data = (jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                req.setHTTPBody(data)

                NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, err ->
                    if (err == null) {
                        val code = (resp as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                        println("[SyncSwift iOS] Pairing code $code published to Supabase: HTTP $code")
                    }
                }.resume()
            } catch (_: Exception) {}
        }
    }

    actual fun connectViaPairingCode(code: String, onResult: (PairResult) -> Unit) {
        val clean = code.replace(" ", "").trim()
        if (clean.length != 6) {
            onResult(PairResult.UNAVAILABLE)
            return
        }

        scope.launch {
            try {
                val supabaseUrl = PlatformConfig.supabaseUrl
                val serviceKey = PlatformConfig.supabaseServiceRoleKey.ifBlank { PlatformConfig.supabaseAnonKey }
                val url = NSURL.URLWithString("$supabaseUrl/rest/v1/pairing_codes?code=eq.$clean&select=*")
                if (url == null) {
                    withContext(Dispatchers.Main) { onResult(PairResult.UNAVAILABLE) }
                    return@launch
                }

                val req = NSMutableURLRequest.requestWithURL(url)
                req.setHTTPMethod("GET")
                req.setValue(serviceKey, forHTTPHeaderField = "apikey")
                req.setValue("Bearer $serviceKey", forHTTPHeaderField = "Authorization")

                val result = suspendCancellableCoroutine<PairResult> { cont ->
                    val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, error ->
                        if (error == null && data != null) {
                            val body = NSString.create(data, NSUTF8StringEncoding)?.toString() ?: ""
                            if (body.contains("\"code\"")) {
                                val expiresAt = Regex(""""expires_at"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: ""
                                val isExpired = if (expiresAt.isNotBlank()) {
                                    val formatter = platform.Foundation.NSISO8601DateFormatter()
                                    val expDate = formatter.dateFromString(expiresAt)
                                    if (expDate != null) {
                                        expDate.timeIntervalSinceNow < 0
                                    } else false
                                } else false

                                if (isExpired) {
                                    if (cont.isActive) cont.resume(PairResult.EXPIRED_OR_USED) {}
                                    return@dataTaskWithRequest
                                }

                                val devId = Regex(""""device_id"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                                val devName = Regex(""""device_name"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: "Remote Device"
                                val devHost = Regex(""""host"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: ""
                                val devPort = Regex(""""port"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 52424
                                val devType = Regex(""""device_type"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: "Desktop"
                                val pToken = Regex(""""push_token"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: ""
                                val targetLang = Regex(""""language"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)

                                if (!devId.isNullOrBlank() || devHost.isNotBlank()) {
                                    val targetHost = devHost.ifBlank { "remote-$clean" }
                                    val targetDevKey = deviceKey(targetHost, devPort)
                                    DeviceDiscoveryManager.addManualDevice(targetHost, devPort, devName, devType)

                                    if (pToken.isNotBlank()) {
                                        val self = DeviceDiscoveryManager.getSelfDevice()
                                        val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                                        val targetCopy = net.syncswift.copyForLanguageCode(targetLang)
                                        val myPushToken = net.syncswift.getSavedIosPushToken() ?: ""
                                        scope.launch(Dispatchers.Default) {
                                            sendFcmNotification(
                                                token = pToken,
                                                title = targetCopy.notificationPairRequestTitle,
                                                body = targetCopy.notificationPairRequestBody.replace("%s", selfName),
                                                data = mapOf(
                                                    "type" to "pair_request",
                                                    "senderName" to selfName,
                                                    "senderHost" to (self?.host ?: ""),
                                                    "senderPort" to "${self?.port ?: 52424}",
                                                    "senderId" to (self?.id ?: UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""),
                                                    "senderOs" to "iOS",
                                                    "senderPushToken" to myPushToken,
                                                    "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                                                    "timestamp" to "${currentTimeMs()}"
                                                )
                                            )
                                        }
                                    }

                                    // Delete consumed pairing code so it cannot be reused
                                    val delUrl = NSURL.URLWithString("$supabaseUrl/rest/v1/pairing_codes?code=eq.$clean")
                                    if (delUrl != null) {
                                        val delReq = NSMutableURLRequest.requestWithURL(delUrl)
                                        delReq.setHTTPMethod("DELETE")
                                        delReq.setValue(serviceKey, forHTTPHeaderField = "apikey")
                                        delReq.setValue("Bearer $serviceKey", forHTTPHeaderField = "Authorization")
                                        NSURLSession.sharedSession.dataTaskWithRequest(delReq).resume()
                                    }

                                    val cancelAction: () -> Unit = {
                                        markRequestCancelled(clean)
                                        if (!devId.isNullOrBlank()) markRequestCancelled(devId)
                                        markRequestCancelled(targetHost)
                                        markRequestCancelled(targetDevKey)
                                        markDeviceDisconnected(targetHost)
                                        if (!devId.isNullOrBlank()) markDeviceDisconnected(devId)
                                        markDeviceDisconnected(targetDevKey)
                                        DeviceDiscoveryManager.removeManualDevice(targetHost)
                                        OutgoingStatusManager.dismiss()

                                        if (pToken.isNotBlank()) {
                                            val self = DeviceDiscoveryManager.getSelfDevice()
                                            val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                                            val selfHost = self?.host ?: ""
                                            val selfPort = self?.port ?: 52424
                                            val selfId = self?.id ?: UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""
                                            scope.launch(Dispatchers.Default) {
                                                sendFcmNotification(
                                                    token = pToken,
                                                    title = "",
                                                    body = "",
                                                    data = mapOf(
                                                        "type" to "pair_cancelled",
                                                        "senderName" to selfName,
                                                        "senderHost" to selfHost,
                                                        "senderPort" to "$selfPort",
                                                        "senderId" to selfId
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    OutgoingStatusManager.show(
                                        type = OutgoingStatusType.CONNECTION,
                                        deviceName = devName,
                                        step = OutgoingStep.WAITING_APPROVAL,
                                        message = currentAppCopy().transferNotificationSentWaiting,
                                        onCancel = cancelAction
                                    )

                                    if (cont.isActive) cont.resume(PairResult.PENDING_APPROVAL) {}
                                    return@dataTaskWithRequest
                                }
                            }
                        }
                        if (cont.isActive) cont.resume(PairResult.EXPIRED_OR_USED) {}
                    }
                    task.resume()
                }

                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(PairResult.UNAVAILABLE)
                }
            }
        }
    }
}
