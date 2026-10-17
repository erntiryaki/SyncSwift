package net.syncswift.connection

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.PlatformConfig
import net.syncswift.SessionManager
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.darwin.*
import platform.posix.*
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

private fun sendPairRequestViaFcm(target: DiscoveredDevice, selfName: String, selfHost: String, selfPort: Int, selfOs: String): Boolean {
    var token = target.pushToken
    if (token.isNullOrBlank() && target.host.isNotBlank()) {
        token = ConnectionManager.queryPushTokenFromSupabase(target.host, target.name)
    }
    if (token.isNullOrBlank()) return false

    val copy = currentAppCopy()
    return ConnectionManager.sendFcmNotification(
        token = token,
        title = copy.notificationPairRequestTitle,
        body = copy.notificationPairRequestBody.replace("%s", selfName),
        data = mapOf(
            "type" to "pair_request",
            "senderName" to selfName,
            "senderHost" to selfHost,
            "senderPort" to "$selfPort",
            "senderOs" to selfOs
        )
    )
}

actual object ConnectionManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _connectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val connectedDeviceKeys: StateFlow<Set<String>> = _connectedDeviceKeys.asStateFlow()

    private val _remoteConnectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val remoteConnectedDeviceKeys: StateFlow<Set<String>> = _remoteConnectedDeviceKeys.asStateFlow()

    private val _pendingPairRequest = MutableStateFlow<IncomingPairRequest?>(null)
    actual val pendingPairRequest: StateFlow<IncomingPairRequest?> = _pendingPairRequest.asStateFlow()

    fun dispatchIncomingPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = request
    }

    private val _pendingTransferRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    actual val pendingTransferRequest: StateFlow<IncomingTransferRequest?> = _pendingTransferRequest.asStateFlow()

    fun dispatchIncomingTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = request
    }

    fun cancelIncomingTransferRequest(requestId: String) {
        if (_pendingTransferRequest.value?.requestId == requestId) {
            _pendingTransferRequest.value = null
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
        generateOrRotatePairingCode()
    }

    private fun deviceKey(host: String, port: Int): String = "$host:$port"

    actual fun isDeviceConnected(device: DiscoveredDevice): Boolean {
        val key = deviceKey(device.host, device.port)
        return _connectedDeviceKeys.value.contains(key) ||
               _connectedDeviceKeys.value.contains(device.id) ||
               _remoteConnectedDeviceKeys.value.contains(key) ||
               _remoteConnectedDeviceKeys.value.contains(device.id)
    }

    actual fun isRemoteDevice(device: DiscoveredDevice): Boolean {
        val key = deviceKey(device.host, device.port)
        return device.isRemote ||
               _remoteConnectedDeviceKeys.value.contains(key) ||
               _remoteConnectedDeviceKeys.value.contains(device.id)
    }

    actual fun markDeviceConnected(deviceKey: String, isRemote: Boolean) {
        val newConnected = _connectedDeviceKeys.value + deviceKey
        _connectedDeviceKeys.value = newConnected
        SessionManager.saveConnectedDeviceKeys(newConnected)
        if (isRemote) {
            val newRemote = _remoteConnectedDeviceKeys.value + deviceKey
            _remoteConnectedDeviceKeys.value = newRemote
            SessionManager.saveRemoteConnectedDeviceKeys(newRemote)
        }
    }

    actual fun markDeviceDisconnected(deviceKey: String) {
        val newConnected = _connectedDeviceKeys.value - deviceKey
        _connectedDeviceKeys.value = newConnected
        SessionManager.saveConnectedDeviceKeys(newConnected)
        val newRemote = _remoteConnectedDeviceKeys.value - deviceKey
        _remoteConnectedDeviceKeys.value = newRemote
        SessionManager.saveRemoteConnectedDeviceKeys(newRemote)
    }

    fun queryPushTokenFromSupabase(host: String, name: String): String? {
        return try {
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
            val url = NSURL.URLWithString("$supabaseUrl/rest/v1/devices?select=push_token&$queryParam&limit=1") ?: return null
            val req = NSMutableURLRequest.requestWithURL(url)
            req.setHTTPMethod("GET")
            req.setValue(anonKey, forHTTPHeaderField = "apikey")
            req.setValue("Bearer $anonKey", forHTTPHeaderField = "Authorization")

            var resultToken: String? = null
            val sem = dispatch_semaphore_create(0)
            NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, err ->
                if (err == null && data != null) {
                    val body = NSString.create(data, NSUTF8StringEncoding).toString()
                    val match = Regex(""""push_token"\s*:\s*"([^"]+)"""").find(body)
                    resultToken = match?.groupValues?.get(1)?.ifBlank { null }
                }
                dispatch_semaphore_signal(sem)
            }.resume()
            dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 4_000_000_000L))
            resultToken
        } catch (_: Exception) {
            null
        }
    }

    fun sendFcmNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        if (token.isBlank()) return false
        return try {
            val url = NSURL.URLWithString("https://api.syncswift.net/api/fcm/send") ?: return false
            val req = NSMutableURLRequest.requestWithURL(url)
            req.setHTTPMethod("POST")
            req.setValue("application/json", forHTTPHeaderField = "Content-Type")

            val dataPairs = data.entries.joinToString(",") { (k, v) ->
                "\"$k\": \"${v.replace("\"", "\\\"").replace("\n", "\\n")}\""
            }
            val jsonBody = """
                {
                    "targetToken": "$token",
                    "token": "$token",
                    "title": "${title.replace("\"", "\\\"")}",
                    "body": "${body.replace("\"", "\\\"")}",
                    "data": { $dataPairs }
                }
            """.trimIndent()
            req.setHTTPBody((jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding))

            var success = false
            val sem = dispatch_semaphore_create(0)
            NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, err ->
                if (err == null && resp != null) {
                    val http = resp as? NSHTTPURLResponse
                    if (http?.statusCode?.toInt() in 200..299) {
                        success = true
                    }
                }
                dispatch_semaphore_signal(sem)
            }.resume()
            dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 6_000_000_000L))
            success
        } catch (_: Exception) {
            false
        }
    }

    fun syncDeviceWithSupabase(pushToken: String) {
        if (pushToken.isBlank()) return
        scope.launch(Dispatchers.Default) {
            try {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val deviceId = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "ios-${self?.name}"
                val cleanName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                val localIp = self?.host ?: ""
                val subnet = if (localIp.contains(".")) localIp.substringBeforeLast(".") else ""
                val supabaseUrl = PlatformConfig.supabaseUrl
                val serviceKey = PlatformConfig.supabaseServiceRoleKey

                val url = NSURL.URLWithString("$supabaseUrl/rest/v1/devices") ?: return@launch
                val req = NSMutableURLRequest.requestWithURL(url)
                req.setHTTPMethod("POST")
                req.setValue("application/json", forHTTPHeaderField = "Content-Type")
                req.setValue(serviceKey, forHTTPHeaderField = "apikey")
                req.setValue("Bearer $serviceKey", forHTTPHeaderField = "Authorization")
                req.setValue("resolution=merge-duplicates", forHTTPHeaderField = "Prefer")

                val jsonBody = """
                    {
                        "id": "$deviceId",
                        "device_name": "$cleanName",
                        "device_type": "iOS",
                        "push_token": "$pushToken",
                        "local_ip": "$localIp",
                        "port": 52424,
                        "network_ssid": "$subnet",
                        "is_online": true
                    }
                """.trimIndent()
                req.setHTTPBody((jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding))

                NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, err ->
                    if (err == null) {
                        val code = (resp as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                        println("[SyncSwift iOS] Registered device and push token in Supabase: HTTP $code")
                    }
                }.resume()
            } catch (e: Exception) {
                println("[SyncSwift iOS] Failed to sync device with Supabase: ${e.message}")
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
        val cancelPairing: () -> Unit = {
            if (activeSocket >= 0) {
                close(activeSocket)
                activeSocket = -1
            }
        }

        OutgoingStatusManager.show(
            type = OutgoingStatusType.CONNECTION,
            deviceName = target.name,
            step = OutgoingStep.WAITING_APPROVAL,
            message = copy.waitingForRecipientApproval,
            onCancel = cancelPairing
        )

        scope.launch(Dispatchers.Default) {
            // 1. Try direct local TCP socket pairing
            if (target.host.isNotBlank() && target.host != "127.0.0.1" && !target.host.startsWith("remote-")) {
                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock >= 0) {
                    activeSocket = sock
                    val connected = memScoped {
                        val timeout = alloc<timeval>()
                        timeout.tv_sec = 30
                        timeout.tv_usec = 0
                        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())

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
                                        markDeviceConnected(key)
                                        markDeviceConnected(target.id)
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

            // 2. Cross-network FCM Relay fallback
            val fcmSent = sendPairRequestViaFcm(target, selfName, selfHost, selfPort, selfOs)
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

    actual fun disconnectDevice(target: DiscoveredDevice) {
        val key = deviceKey(target.host, target.port)
        markDeviceDisconnected(key)
        markDeviceDisconnected(target.id)
    }

    actual fun acceptPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = null
        val key = deviceKey(request.senderHost, request.senderPort)
        markDeviceConnected(key)
        markDeviceConnected(request.senderId)
        request.deferredResponse.complete(true)
    }

    actual fun rejectPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = null
        request.deferredResponse.complete(false)
    }

    actual fun acceptTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = null
        request.deferredResponse.complete(true)
    }

    actual fun rejectTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = null
        request.deferredResponse.complete(false)
    }

    actual fun cancelIncomingPairRequest() {
        _pendingPairRequest.value?.deferredResponse?.complete(false)
        _pendingPairRequest.value = null
    }

    actual fun cancelIncomingTransferRequest(transferId: String?) {
        _pendingTransferRequest.value?.deferredResponse?.complete(false)
        _pendingTransferRequest.value = null
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
                            val body = NSString.create(data, NSUTF8StringEncoding).toString()
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

                                if (!devId.isNullOrBlank() || devHost.isNotBlank()) {
                                    val targetHost = devHost.ifBlank { "remote-$clean" }
                                    DeviceDiscoveryManager.addManualDevice(targetHost, devPort, devName, devType)
                                    val key = deviceKey(targetHost, devPort)
                                    markDeviceConnected(key, isRemote = true)
                                    if (!devId.isNullOrBlank()) {
                                        markDeviceConnected(devId, isRemote = true)
                                    }

                                    if (pToken.isNotBlank()) {
                                        val self = DeviceDiscoveryManager.getSelfDevice()
                                        val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                                        sendFcmNotification(
                                            token = pToken,
                                            title = "SyncSwift Uzaktan Bağlantı",
                                            body = "$selfName cihazınızla eşleşti.",
                                            data = mapOf(
                                                "type" to "pair_request",
                                                "senderName" to selfName,
                                                "senderHost" to (self?.host ?: ""),
                                                "senderPort" to "${self?.port ?: 52424}",
                                                "senderOs" to "iOS"
                                            )
                                        )
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

                                    if (cont.isActive) cont.resume(PairResult.ACCEPTED) {}
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
