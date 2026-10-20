package net.syncswift.connection

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import net.syncswift.SessionManager
import net.syncswift.SyncSwiftConfig
import net.syncswift.SupabaseNetwork
import net.syncswift.currentAppCopy
import net.syncswift.showNativeToast
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.fcm.SyncSwiftFirebaseMessagingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.random.Random

actual object ConnectionManager {
    private const val TAG = "SyncSwiftConnection"
    private const val PROTOCOL_VERSION: Byte = 1
    private const val TYPE_PAIR_REQUEST: Byte = 3
    private const val TYPE_UNPAIR: Byte = 4
    private const val RESP_ACCEPTED: Byte = 7
    private const val RESP_REJECTED: Byte = 8
    private const val TYPE_REMOTE_LOGOUT: Byte = 10

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val connectedDeviceKeys: StateFlow<Set<String>> = _connectedDeviceKeys.asStateFlow()

    private val _remoteConnectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val remoteConnectedDeviceKeys: StateFlow<Set<String>> = _remoteConnectedDeviceKeys.asStateFlow()

    private val _pendingPairRequest = MutableStateFlow<IncomingPairRequest?>(null)
    actual val pendingPairRequest: StateFlow<IncomingPairRequest?> = _pendingPairRequest.asStateFlow()

    private val _pendingTransferRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    actual val pendingTransferRequest: StateFlow<IncomingTransferRequest?> = _pendingTransferRequest.asStateFlow()

    private val _activePairingCode = MutableStateFlow("834 291")
    actual val activePairingCode: StateFlow<String> = _activePairingCode.asStateFlow()

    private val _codeRemainingSeconds = MutableStateFlow(60)
    actual val codeRemainingSeconds: StateFlow<Int> = _codeRemainingSeconds.asStateFlow()

    private var codeRotationJob: Job? = null

    init {
        val savedConnected = SessionManager.getSavedConnectedDeviceKeys()
        val savedRemote = SessionManager.getSavedRemoteConnectedDeviceKeys()
        if (savedConnected.isNotEmpty()) {
            _connectedDeviceKeys.value = savedConnected
        }
        if (savedRemote.isNotEmpty()) {
            _remoteConnectedDeviceKeys.value = savedRemote
        }
        generateOrRotatePairingCode()
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

    private val cancelledRequestTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    actual fun isRequestCancelled(requestId: String): Boolean {
        if (requestId.isBlank()) return false
        val ts = cancelledRequestTimestamps[requestId] ?: return false
        if (System.currentTimeMillis() - ts > 30_000L) {
            cancelledRequestTimestamps.remove(requestId)
            return false
        }
        return true
    }

    actual fun markRequestCancelled(requestId: String) {
        if (requestId.isNotBlank()) {
            cancelledRequestTimestamps[requestId] = System.currentTimeMillis()
        }
    }

    fun clearRequestCancelled(requestId: String) {
        if (requestId.isNotBlank()) {
            cancelledRequestTimestamps.remove(requestId)
        }
    }

    actual fun markDeviceConnected(deviceKey: String, isRemote: Boolean) {
        if (deviceKey.isNotBlank()) {
            cancelledRequestTimestamps.remove(deviceKey)
            cancelledRequestTimestamps.remove(cleanDeviceDisplayName(deviceKey))
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

    private val recentlyHandledTransferIds = java.util.Collections.synchronizedMap(object : java.util.LinkedHashMap<String, Long>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 50
    })
    private val recentlyHandledPairKeys = java.util.Collections.synchronizedMap(object : java.util.LinkedHashMap<String, Long>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 50
    })

    fun dispatchIncomingPairRequest(request: IncomingPairRequest) {
        val now = System.currentTimeMillis()
        val key = "${request.senderId}_${request.senderHost}_${request.senderName}"
        val lastHandled = recentlyHandledPairKeys[key] ?: 0L
        if (now - lastHandled < 10000L) {
            Log.d(TAG, "Ignoring duplicate pair request for $key within 10s")
            return
        }
        val current = _pendingPairRequest.value
        if (current != null && (current.requestId == request.requestId ||
            (request.senderId.isNotBlank() && current.senderId == request.senderId) ||
            (request.senderHost.isNotBlank() && current.senderHost == request.senderHost))) {
            Log.d(TAG, "Pair request already pending for ${request.senderName}, ignoring duplicate")
            return
        }
        recentlyHandledPairKeys[key] = now
        _pendingPairRequest.value = request
    }

    fun dispatchIncomingTransferRequest(request: IncomingTransferRequest) {
        val now = System.currentTimeMillis()
        val key = if (request.transferId.isNotBlank()) request.transferId else "${request.senderHost}_${request.fileName}_${request.fileSize}"
        val lastHandled = recentlyHandledTransferIds[key] ?: 0L
        if (now - lastHandled < 15000L) {
            Log.d(TAG, "Ignoring duplicate transfer request for $key within 15s")
            return
        }
        val current = _pendingTransferRequest.value
        if (current != null && (current.requestId == request.requestId ||
            (request.transferId.isNotBlank() && current.transferId == request.transferId))) {
            Log.d(TAG, "Transfer request already pending for $key, ignoring duplicate")
            return
        }
        recentlyHandledTransferIds[key] = now
        _pendingTransferRequest.value = request
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
        val isRemote = when {
            request.isRemote -> true
            matchedDev != null -> matchedDev.isRemote
            request.senderHost.startsWith("remote-") -> true
            request.senderHost.isBlank() || !request.senderHost.contains(".") -> true
            request.senderHost.startsWith("192.168.") || request.senderHost.startsWith("10.") || request.senderHost.startsWith("172.") -> false
            else -> false
        }
        markDeviceConnected(key, isRemote = isRemote)
        markDeviceConnected(request.senderId, isRemote = isRemote)
        markDeviceConnected(request.senderHost, isRemote = isRemote)
        markDeviceConnected(request.senderName, isRemote = isRemote)
        markDeviceConnected(cleanDeviceDisplayName(request.senderName), isRemote = isRemote)
        DeviceDiscoveryManager.markDeviceSeen(request.senderHost, request.senderName)
        DeviceDiscoveryManager.triggerDiscoveryPing()

        if (matchedDev != null) {
            markDeviceConnected(matchedDev.id, isRemote = isRemote)
            markDeviceConnected(matchedDev.host, isRemote = isRemote)
            DeviceDiscoveryManager.saveManualDevices()
        } else if (isRemote || request.senderHost.isNotBlank()) {
            DeviceDiscoveryManager.addManualDevice(
                host = request.senderHost.ifBlank { "remote-${cleanDeviceDisplayName(request.senderName)}" },
                port = request.senderPort,
                name = request.senderName,
                osType = request.senderOsType
            )
        }

        // Notify sender via FCM using sender's own language
        scope.launch {
            val senderToken = if (request.senderPushToken.isNotBlank()) request.senderPushToken else queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = java.util.UUID.nameUUIDFromBytes("${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".toByteArray()).toString()

                val ctx = SessionManager.getAppContext()
                val myPushToken = if (ctx != null) SyncSwiftFirebaseMessagingService.getSavedToken(ctx) ?: "" else ""
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
        if (_pendingPairRequest.value?.requestId == request.requestId) {
            _pendingPairRequest.value = null
        }
        request.deferredResponse.complete(false)

        // Ensure sender is completely disconnected locally
        if (request.senderHost.isNotBlank()) {
            markDeviceDisconnected(request.senderHost)
            markDeviceDisconnected(deviceKey(request.senderHost, request.senderPort))
        }
        if (request.senderId.isNotBlank()) markDeviceDisconnected(request.senderId)
        if (request.senderName.isNotBlank()) {
            markDeviceDisconnected(request.senderName)
            markDeviceDisconnected(cleanDeviceDisplayName(request.senderName))
        }
        SessionManager.getAppContext()?.let { ctx: Context ->
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(net.syncswift.fcm.SyncSwiftFirebaseMessagingService.NOTIFICATION_PAIR_ID)
        }

        scope.launch {
            val senderToken = if (request.senderPushToken.isNotBlank()) {
                request.senderPushToken
            } else {
                queryPushTokenFromSupabase(request.senderHost, request.senderName)
            }
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = java.util.UUID.nameUUIDFromBytes("${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".toByteArray()).toString()
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

    actual fun cancelIncomingPairRequest() {
        val req = _pendingPairRequest.value
        if (req != null) {
            req.deferredResponse.complete(false)
            _pendingPairRequest.value = null
        }
        SessionManager.getAppContext()?.let { ctx: Context ->
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(net.syncswift.fcm.SyncSwiftFirebaseMessagingService.NOTIFICATION_PAIR_ID)
        }
    }

    actual fun cancelIncomingTransferRequest(transferId: String?) {
        val req = _pendingTransferRequest.value
        if (req != null) {
            if (transferId.isNullOrBlank() || req.transferId == transferId || req.requestId == transferId) {
                req.deferredResponse.complete(false)
                _pendingTransferRequest.value = null
            }
        }
        SessionManager.getAppContext()?.let { ctx: Context ->
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(net.syncswift.fcm.SyncSwiftFirebaseMessagingService.NOTIFICATION_FILE_ID)
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
        if (request.senderHost.isNotBlank()) {
            DeviceDiscoveryManager.markDeviceSeen(request.senderHost, request.senderName)
            DeviceDiscoveryManager.triggerDiscoveryPing()
        }
        if (request.mimeType == "text/plain") {
            val clipText = request.downloadUrl
            val copy = currentAppCopy()
            net.syncswift.transfer.TransferEngine.applyReceivedClipboard(clipText, copy)
            showNativeToast(copy.clipboardCopiedSuccess)
            scope.launch {
                val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
                if (!senderToken.isNullOrBlank()) {
                    val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
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
            scope.launch {
                val senderToken = if (request.senderPushToken.isNotBlank()) request.senderPushToken else queryPushTokenFromSupabase(request.senderHost, request.senderName)
                if (!senderToken.isNullOrBlank()) {
                    val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                    sendFcmNotification(
                        token = senderToken,
                        title = targetCopy.notificationFileRequestTitle,
                        body = targetCopy.fileSentSuccessfully.replace("%s", request.fileName),
                        data = mapOf(
                            "type" to "file_accepted",
                            "transferId" to request.transferId,
                            "fileName" to request.fileName,
                            "senderName" to selfName
                        )
                    )
                }
            }
            net.syncswift.transfer.TransferEngine.downloadRemoteFile(
                downloadUrl = request.downloadUrl,
                fileName = request.fileName,
                fileSize = request.fileSize,
                mimeType = request.mimeType,
                transferId = request.transferId,
                senderName = request.senderName,
                senderHost = request.senderHost,
                senderPushToken = request.senderPushToken,
                senderLanguage = request.senderLanguage
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
        if (_pendingTransferRequest.value?.requestId == request.requestId) {
            _pendingTransferRequest.value = null
        }
        request.deferredResponse.complete(false)
        scope.launch {
            val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(request.senderLanguage)
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
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

    actual fun ensurePairingCodeActive() {
        if (codeRotationJob == null || codeRotationJob?.isActive != true || _codeRemainingSeconds.value <= 0) {
            generateOrRotatePairingCode()
        }
    }

    private fun publishPairingCodeToBackend(code: String) {
        scope.launch {
            try {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfId = java.util.UUID.nameUUIDFromBytes("${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".toByteArray()).toString()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val ctx = SessionManager.getAppContext()
                val pushToken = if (ctx != null) SyncSwiftFirebaseMessagingService.getSavedToken(ctx) ?: "" else ""

                val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

                val payload = JSONObject().apply {
                    put("code", code)
                    put("device_id", selfId)
                    put("device_name", selfName)
                    put("device_type", "Android")
                    put("push_token", pushToken)
                    put("host", selfHost)
                    put("port", selfPort)
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray())
                }
                val codeResp = conn.responseCode
                Log.d(TAG, "Pairing code published to Supabase: $code (HTTP $codeResp)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not publish pairing code to Supabase: ${e.message}")
            }
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
                val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean&select=*")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Authorization", "Bearer $key")

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val expiresAtStr = obj.optString("expires_at", "")
                        val isExpired = try {
                            if (expiresAtStr.isNotBlank()) {
                                java.time.Instant.parse(expiresAtStr).isBefore(java.time.Instant.now())
                            } else false
                        } catch (_: Exception) { false }

                        if (isExpired) {
                            withContext(Dispatchers.Main) {
                                onResult(PairResult.EXPIRED_OR_USED)
                            }
                            return@launch
                        }

                        val deviceId = obj.optString("device_id", "remote-$clean")
                        val deviceName = cleanDeviceDisplayName(obj.optString("device_name", "SyncSwift Remote Device"))
                        val deviceType = obj.optString("device_type", "Desktop")
                        val pushToken = obj.optString("push_token", "")
                        val host = obj.optString("host", "")
                        val port = obj.optInt("port", 52424)
                        val targetLang = obj.optString("language", "")
                        val targetHost = host.ifBlank { "remote-$clean" }
                        val targetDevKey = deviceKey(targetHost, port)

                        // Register remote device in DeviceDiscoveryManager
                        DeviceDiscoveryManager.addManualDevice(
                            host = targetHost,
                            port = port,
                            name = deviceName,
                            osType = deviceType
                        )

                        // Send FCM pair request to notify the target for approval in their language
                        if (pushToken.isNotBlank()) {
                            val self = DeviceDiscoveryManager.getSelfDevice()
                            val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                            val targetCopy = net.syncswift.copyForLanguageCode(targetLang.ifBlank { null })
                            val ctx = SessionManager.getAppContext()
                            val myPushToken = if (ctx != null) SyncSwiftFirebaseMessagingService.getSavedToken(ctx) ?: "" else ""
                            sendFcmNotification(
                                token = pushToken,
                                title = targetCopy.notificationPairRequestTitle,
                                body = targetCopy.notificationPairRequestBody.replace("%s", selfName),
                                data = mapOf(
                                    "type" to "pair_request",
                                    "senderId" to (self?.id ?: ""),
                                    "senderName" to selfName,
                                    "senderHost" to (self?.host ?: ""),
                                    "senderPort" to "${self?.port ?: 52424}",
                                    "senderOs" to "Android",
                                    "senderPushToken" to myPushToken,
                                    "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                                    "timestamp" to "${System.currentTimeMillis()}"
                                )
                            )
                        }

                        val rawEmail = SessionManager.getLoggedInEmail()?.trim().orEmpty()
                        val rawUserId = SessionManager.getLoggedInUserId()?.trim().orEmpty()
                        val isLocal = !SessionManager.isLoggedIn() || (rawEmail.isBlank() && rawUserId.isBlank()) || SessionManager.getLoggedInProvider() == net.syncswift.IdentityProvider.Local
                        val effectiveIdentifier = if (isLocal) {
                            "local_user"
                        } else if (rawEmail.isNotBlank()) {
                            rawEmail
                        } else {
                            rawUserId
                        }

                        // Await PATCH so desktop receives pairing signal before mobile returns ACCEPTED
                        withContext(Dispatchers.IO) {
                            try {
                                val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean")
                                val patchConn = patchUrl.openConnection() as HttpURLConnection
                                patchConn.requestMethod = "PATCH"
                                patchConn.doOutput = true
                                patchConn.connectTimeout = 6000
                                patchConn.readTimeout = 6000
                                patchConn.setRequestProperty("Content-Type", "application/json")
                                patchConn.setRequestProperty("apikey", key)
                                patchConn.setRequestProperty("Authorization", "Bearer $key")
                                patchConn.outputStream.use { os ->
                                    val userField = if (!isLocal && rawUserId.isNotBlank()) """"$rawUserId"""" else "null"
                                    os.write("""{"push_token": "$effectiveIdentifier", "user_id": $userField}""".toByteArray())
                                    os.flush()
                                }
                                val patchCode = patchConn.responseCode
                                Log.d("ConnectionManager", "PATCH pairing_codes code=$patchCode token=$effectiveIdentifier")
                            } catch (e: Exception) {
                                Log.e("ConnectionManager", "PATCH pairing_codes error: ${e.message}")
                            }

                            if (deviceId.isNotBlank() && !isLocal) {
                                try {
                                    SupabaseNetwork.registerDeviceInSupabase(
                                        email = effectiveIdentifier,
                                        deviceId = deviceId,
                                        deviceName = deviceName,
                                        deviceType = deviceType,
                                        pushToken = "",
                                        localIp = host,
                                        port = port,
                                        userId = rawUserId.ifBlank { null }
                                    )
                                } catch (_: Exception) {}
                            }
                        }

                        // Mark device locally connected
                        markDeviceConnected(targetHost, isRemote = true)
                        if (deviceId.isNotBlank()) markDeviceConnected(deviceId, isRemote = true)
                        markDeviceConnected(targetDevKey, isRemote = true)
                        val newDev = DiscoveredDevice(
                            id = deviceId,
                            name = deviceName,
                            host = targetHost,
                            port = port,
                            osType = deviceType,
                            isRemote = true,
                            isOnline = true
                        )
                        DeviceDiscoveryManager.addOrUpdateConnectedDevice(newDev)

                        withContext(Dispatchers.Main) {
                            onResult(PairResult.ACCEPTED)
                        }
                        return@launch

                        withContext(Dispatchers.Main) {
                            onResult(PairResult.PENDING_APPROVAL)
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(PairResult.EXPIRED_OR_USED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting via pairing code: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(PairResult.UNAVAILABLE)
                }
            }
        }
    }

    actual fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit) {
        val copy = currentAppCopy()
        val self = DeviceDiscoveryManager.getSelfDevice()
        val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
        val selfHost = self?.host ?: ""
        val selfPort = self?.port ?: 52424
        val selfOs = self?.osType ?: "Android"

        // Clear any old cancellation flags for this target device so new pairing is never blocked
        val targetDevKey = deviceKey(target.host, target.port)
        val cleanTargetName = cleanDeviceDisplayName(target.name)
        clearRequestCancelled(target.id)
        clearRequestCancelled(target.host)
        clearRequestCancelled(targetDevKey)
        clearRequestCancelled(target.name)
        clearRequestCancelled(cleanTargetName)

        var activeSocket: Socket? = null
        var isCancelled = false
        var pairJob: Job? = null

        val cancelPairing: () -> Unit = {
            isCancelled = true
            pairJob?.cancel()
            OutgoingStatusManager.dismiss()
            try {
                activeSocket?.let { s ->
                    try {
                        val out = s.getOutputStream()
                        out.write(10) // TYPE_CANCEL
                        out.flush()
                    } catch (_: Exception) {}
                    s.close()
                }
            } catch (_: Exception) {}
            activeSocket = null

            // Mark locally cancelled so any late pair_accepted doesn't register as connected
            markRequestCancelled(target.id)
            markRequestCancelled(target.host)
            markRequestCancelled(targetDevKey)
            markDeviceDisconnected(targetDevKey)
            markDeviceDisconnected(target.id)

            scope.launch {
                val token = if (target.pushToken?.isNotBlank() == true) target.pushToken else queryPushTokenFromSupabase(target.host, target.name)
                if (!token.isNullOrBlank()) {
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                    val selfHost = self?.host ?: ""
                    val selfPort = self?.port ?: 52424
                    val selfId = self?.id ?: ""
                    sendFcmNotification(
                        token = token,
                        title = "",
                        body = "",
                        data = mapOf(
                            "type" to "pair_cancelled",
                            "senderId" to selfId,
                            "senderName" to selfName,
                            "senderHost" to selfHost,
                            "senderPort" to "$selfPort"
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

        pairJob = scope.launch {
            val isLocalDiscovered = target.isOnline && !target.isRemote && !target.host.startsWith("remote-") &&
                    !isRemoteDevice(target) && DeviceDiscoveryManager.devices.value.any { it.host == target.host && it.isOnline && !it.isRemote }

            // 1. Direct TCP handshake if local discovered
            if (isLocalDiscovered && target.host.isNotBlank()) {
                try {
                    val socket = Socket()
                    activeSocket = socket
                    socket.connect(InetSocketAddress(target.host, target.port), 3500)
                    socket.soTimeout = 35000
                    socket.use { s ->
                        val out = DataOutputStream(s.getOutputStream())
                        val input = DataInputStream(s.getInputStream())

                        out.writeByte(PROTOCOL_VERSION.toInt())
                        out.writeByte(TYPE_PAIR_REQUEST.toInt())
                        out.writeUTF(selfName)
                        out.writeUTF(selfHost)
                        out.writeInt(selfPort)
                        out.writeUTF(selfOs)
                        out.flush()

                        val response = input.readByte()
                        val currentCopy = currentAppCopy()
                        if (response == RESP_ACCEPTED) {
                            markDeviceConnected(deviceKey(target.host, target.port), isRemote = target.isRemote)
                            markDeviceConnected(target.id, isRemote = target.isRemote)
                            markDeviceConnected(target.host, isRemote = target.isRemote)
                            markDeviceConnected(target.name, isRemote = target.isRemote)
                            markDeviceConnected(cleanDeviceDisplayName(target.name), isRemote = target.isRemote)
                            DeviceDiscoveryManager.addOrUpdateConnectedDevice(target)
                            DeviceDiscoveryManager.saveManualDevices()
                            OutgoingStatusManager.completeSuccess(currentCopy.connectionAccepted)
                            withContext(Dispatchers.Main) {
                                onResult(PairResult.ACCEPTED)
                            }
                            return@launch
                        } else {
                            markDeviceDisconnected(deviceKey(target.host, target.port))
                            markDeviceDisconnected(target.id)
                            markDeviceDisconnected(target.host)
                            markDeviceDisconnected(target.name)
                            markDeviceDisconnected(cleanDeviceDisplayName(target.name))
                            OutgoingStatusManager.fail(currentCopy.connectionRequestRejected)
                            withContext(Dispatchers.Main) {
                                onResult(PairResult.REJECTED)
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Direct socket pair failed: ${e.message}")
                } finally {
                    activeSocket = null
                }
            }

            if (isCancelled || !isActive) return@launch

            // 2. Fallback or Direct to FCM Wake-up Push Notification in target's language
            var token = target.pushToken
            if (token.isNullOrBlank() && target.host.isNotBlank()) {
                token = queryPushTokenFromSupabase(target.host, target.name)
            }
            if (isCancelled || !isActive) return@launch

            val targetCopy = net.syncswift.copyForLanguageCode(target.language)
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Sending FCM wake-up pairing request to token: $token")
                val ctx = SessionManager.getAppContext()
                val myPushToken = if (ctx != null) SyncSwiftFirebaseMessagingService.getSavedToken(ctx) ?: "" else ""
                val isRemoteTarget = target.isRemote || !isLocalDiscovered || target.host.startsWith("remote-")
                val fcmSent = sendFcmNotification(
                    token = token,
                    title = targetCopy.notificationPairRequestTitle,
                    body = targetCopy.notificationPairRequestBody.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_request",
                        "senderId" to (self?.id ?: ""),
                        "senderName" to selfName,
                        "senderHost" to selfHost,
                        "senderPort" to "$selfPort",
                        "senderOs" to selfOs,
                        "senderPushToken" to myPushToken,
                        "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                        "isRemote" to "$isRemoteTarget",
                        "timestamp" to "${System.currentTimeMillis()}"
                    )
                )
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
                        onResult(PairResult.PENDING_APPROVAL)
                    }
                    return@launch
                }
            }

            if (isCancelled || !isActive) return@launch
            OutgoingStatusManager.fail(copy.deviceDidNotRespond)
            withContext(Dispatchers.Main) {
                onResult(PairResult.UNAVAILABLE)
            }
        }
    }

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
            scope.launch(Dispatchers.IO) {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = self?.id ?: ""
                val selfName = self?.name ?: ""

                // 1. Try local TCP unpair packet if target has local host
                if (target.host.isNotBlank() && !target.host.startsWith("remote-") && target.port > 0) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(target.host, target.port), 2500)
                        socket.use { s ->
                            val out = DataOutputStream(s.getOutputStream())
                            out.writeByte(PROTOCOL_VERSION.toInt())
                            out.writeByte(TYPE_UNPAIR.toInt())
                            out.writeUTF(selfHost)
                            out.writeInt(selfPort)
                            out.writeUTF(selfId)
                            out.writeUTF(selfName)
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Local unpair notification skipped/failed: ${e.message}")
                    }
                }

                // 2. Try remote FCM unpair push notification
                try {
                    val targetPushToken = (target.pushToken ?: "").ifBlank {
                        queryPushTokenFromSupabase(target.host, target.name)
                    }
                    if (!targetPushToken.isNullOrBlank()) {
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
                    Log.d(TAG, "Remote unpair push failed: ${e.message}")
                }
            }
        }
    }

    fun queryPushTokenFromSupabase(host: String, name: String): String? {
        return try {
            val cleanName = cleanDeviceDisplayName(name)
            val encodedName = java.net.URLEncoder.encode(cleanName, "UTF-8")
            val queryParam = if (host.isNotBlank() && !host.startsWith("remote-")) {
                val encodedHost = java.net.URLEncoder.encode(host, "UTF-8")
                "or=(local_ip.eq.$encodedHost,device_name.eq.$encodedName)"
            } else {
                "device_name=eq.$encodedName"
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?select=push_token&$queryParam&push_token=not.is.null&push_token=neq.&order=created_at.desc&limit=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("apikey", SyncSwiftConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${SyncSwiftConfig.SUPABASE_ANON_KEY}")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val tok = arr.getJSONObject(0).optString("push_token", "")
                    return tok.ifBlank { null }
                }
            } else {
                Log.w(TAG, "Supabase push token query returned HTTP ${conn.responseCode}")
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query push token from Supabase: ${e.message}")
            null
        }
    }

    actual fun notifyRemoteLogout(target: DiscoveredDevice) {
        if (target.host.isBlank() || target.host.startsWith("remote-") || target.port <= 0) return
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(target.host, target.port), 2000)
                socket.use { s ->
                    val out = DataOutputStream(s.getOutputStream())
                    out.writeByte(PROTOCOL_VERSION.toInt())
                    out.writeByte(TYPE_REMOTE_LOGOUT.toInt())
                    out.flush()
                }
                Log.d(TAG, "Sent TYPE_REMOTE_LOGOUT to ${target.host}:${target.port}")
            } catch (e: Exception) {
                Log.d(TAG, "Failed sending local TYPE_REMOTE_LOGOUT: ${e.message}")
            }
        }
    }

    fun sendFcmNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Boolean {
        return try {
            val url = URL("https://api.syncswift.net/api/fcm/send")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val payload = JSONObject().apply {
                put("targetToken", token)
                put("token", token)
                put("title", title)
                put("body", body)
                if (title.isNotBlank() || body.isNotBlank()) {
                    val notifObj = JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("sound", "default")
                        put("badge", 1)
                    }
                    put("notification", notifObj)
                    val apsObj = JSONObject().apply {
                        put("sound", "default")
                        put("badge", 1)
                        put("content-available", 1)
                        put("alert", JSONObject().apply {
                            put("title", title)
                            put("body", body)
                        })
                    }
                    put("aps", apsObj)
                }
                val dataObj = JSONObject()
                data.forEach { (k, v) -> dataObj.put(k, v) }
                if (title.isNotBlank()) dataObj.put("title", title)
                if (body.isNotBlank()) dataObj.put("body", body)
                put("data", dataObj)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(respText)
                val success = respJson.optBoolean("success", false)
                Log.w(TAG, "FCM send result: success=$success (HTTP $code)")
                success
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "FCM send failed: HTTP $code, error: $errText")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM push error: ${e.message}")
            false
        }
    }
}
