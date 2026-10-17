package net.syncswift.connection

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import net.syncswift.SessionManager
import net.syncswift.SyncSwiftConfig
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

    fun dispatchIncomingPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = request
    }

    fun dispatchIncomingTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = request
    }

    actual fun acceptPairRequest(request: IncomingPairRequest) {
        if (_pendingPairRequest.value?.requestId != request.requestId) {
            showNativeToast(currentAppCopy().requestCancelledBySender)
            return
        }
        _pendingPairRequest.value = null
        val completed = request.deferredResponse.complete(true)
        if (!completed) {
            showNativeToast(currentAppCopy().requestCancelledBySender)
            return
        }
        val key = deviceKey(request.senderHost, request.senderPort)
        markDeviceConnected(key)
        markDeviceConnected(request.senderId)

        // Notify sender via FCM that their connection request was accepted
        scope.launch {
            val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val copy = currentAppCopy()
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
                val selfId = java.util.UUID.nameUUIDFromBytes("${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".toByteArray()).toString()

                sendFcmNotification(
                    token = senderToken,
                    title = copy.connectionAccepted,
                    body = copy.notificationPairAcceptedStatus.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_accepted",
                        "senderName" to selfName,
                        "senderHost" to selfHost,
                        "senderPort" to "$selfPort",
                        "senderId" to selfId
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

        scope.launch {
            val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val copy = currentAppCopy()
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                sendFcmNotification(
                    token = senderToken,
                    title = copy.connectionRejected,
                    body = copy.notificationPairRejectedStatus.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_rejected",
                        "senderName" to selfName
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
        if (request.isRemote && request.downloadUrl.isNotBlank()) {
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
        if (_pendingTransferRequest.value?.requestId == request.requestId) {
            _pendingTransferRequest.value = null
        }
        request.deferredResponse.complete(false)
        scope.launch {
            val senderToken = queryPushTokenFromSupabase(request.senderHost, request.senderName)
            if (!senderToken.isNullOrBlank()) {
                val copy = currentAppCopy()
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: Build.MODEL)
                sendFcmNotification(
                    token = senderToken,
                    title = copy.transferRejectedByRecipient,
                    body = copy.notificationFileRejectedStatus.replace("%s", selfName),
                    data = mapOf(
                        "type" to "file_rejected",
                        "transferId" to request.transferId,
                        "senderName" to selfName
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
                conn.setRequestProperty("apikey", SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY}")
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

                        // Register remote device in DeviceDiscoveryManager
                        DeviceDiscoveryManager.addManualDevice(
                            host = host.ifBlank { "remote-$clean" },
                            port = port,
                            name = deviceName,
                            osType = deviceType
                        )

                        // Mark connected as remote
                        markDeviceConnected(deviceId, isRemote = true)
                        if (host.isNotBlank()) {
                            markDeviceConnected(deviceKey(host, port), isRemote = true)
                        }

                        // Send FCM pair request to notify the target
                        if (pushToken.isNotBlank()) {
                            val self = DeviceDiscoveryManager.getSelfDevice()
                            val selfName = self?.name ?: Build.MODEL
                            sendFcmNotification(
                                token = pushToken,
                                title = "SyncSwift Uzaktan Bağlantı",
                                body = "$selfName cihazınızla uzaktan eşleşti.",
                                data = mapOf(
                                    "type" to "pair_request",
                                    "senderId" to (self?.id ?: ""),
                                    "senderName" to selfName,
                                    "senderHost" to (self?.host ?: ""),
                                    "senderPort" to "${self?.port ?: 52424}",
                                    "senderOs" to "Android"
                                )
                            )
                        }

                        // Delete consumed pairing code so it cannot be reused
                        scope.launch {
                            try {
                                val delUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean")
                                val delConn = delUrl.openConnection() as HttpURLConnection
                                delConn.requestMethod = "DELETE"
                                delConn.setRequestProperty("apikey", key)
                                delConn.setRequestProperty("Authorization", "Bearer $key")
                                delConn.responseCode
                            } catch (_: Exception) {}
                        }

                        withContext(Dispatchers.Main) {
                            onResult(PairResult.ACCEPTED)
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
        val selfName = self?.name ?: Build.MODEL
        val selfHost = self?.host ?: ""
        val selfPort = self?.port ?: 52424
        val selfOs = self?.osType ?: "Android"

        var activeSocket: Socket? = null
        var pairJob: Job? = null

        val cancelPairing: () -> Unit = {
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
            pairJob?.cancel()

            // If target has push token, send pair_cancelled via FCM so peer dialog/notification closes instantly
            scope.launch {
                var tok = target.pushToken
                if (tok.isNullOrBlank() && target.host.isNotBlank()) {
                    tok = queryPushTokenFromSupabase(target.host, target.name)
                }
                if (!tok.isNullOrBlank()) {
                    sendFcmNotification(
                        token = tok,
                        title = "SyncSwift",
                        body = "",
                        data = mapOf(
                            "type" to "pair_cancelled",
                            "senderName" to selfName,
                            "senderHost" to selfHost
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
            // 1. Direct TCP handshake if valid IP
            if (target.host.isNotBlank() && !target.host.startsWith("remote-")) {
                try {
                    val socket = Socket()
                    activeSocket = socket
                    socket.connect(InetSocketAddress(target.host, target.port), 3000)
                    socket.soTimeout = 25000
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
                            OutgoingStatusManager.completeSuccess(currentCopy.connectionAccepted)
                            withContext(Dispatchers.Main) {
                                onResult(PairResult.ACCEPTED)
                            }
                            return@launch
                        } else {
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

            // 2. Fallback to FCM Wake-up Push Notification (App closed / device sleeping)
            var token = target.pushToken
            if (token.isNullOrBlank() && target.host.isNotBlank()) {
                token = queryPushTokenFromSupabase(target.host, target.name)
            }
            val currentCopy = currentAppCopy()
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Sending FCM wake-up pairing request to token: $token")
                val fcmSent = sendFcmNotification(
                    token = token,
                    title = currentCopy.notificationPairRequestTitle,
                    body = currentCopy.notificationPairRequestBody.replace("%s", selfName),
                    data = mapOf(
                        "type" to "pair_request",
                        "senderId" to (self?.id ?: ""),
                        "senderName" to selfName,
                        "senderHost" to selfHost,
                        "senderPort" to "$selfPort",
                        "senderOs" to selfOs
                    )
                )
                if (fcmSent) {
                    OutgoingStatusManager.show(
                        type = OutgoingStatusType.CONNECTION,
                        deviceName = target.name,
                        step = OutgoingStep.WAITING_APPROVAL,
                        message = currentCopy.transferNotificationSentWaiting,
                        onCancel = cancelPairing
                    )
                    withContext(Dispatchers.Main) {
                        onResult(PairResult.PENDING_APPROVAL)
                    }
                    return@launch
                }
            }

            OutgoingStatusManager.fail(currentCopy.deviceDidNotRespond)
            withContext(Dispatchers.Main) {
                onResult(PairResult.UNAVAILABLE)
            }
        }
    }

    actual fun disconnectDevice(target: DiscoveredDevice) {
        val key = deviceKey(target.host, target.port)
        markDeviceDisconnected(key)
        markDeviceDisconnected(target.id)

        scope.launch {
            try {
                if (target.host.isNotBlank() && !target.host.startsWith("remote-")) {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(target.host, target.port), 3000)
                    socket.use { s ->
                        val out = DataOutputStream(s.getOutputStream())
                        out.writeByte(PROTOCOL_VERSION.toInt())
                        out.writeByte(TYPE_UNPAIR.toInt())
                        out.writeUTF(target.host)
                        out.writeInt(target.port)
                        out.flush()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Unpair notification sent/skipped: ${e.message}")
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
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?select=push_token&$queryParam&limit=1")
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
                val dataObj = JSONObject()
                data.forEach { (k, v) -> dataObj.put(k, v) }
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
