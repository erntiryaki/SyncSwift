package net.syncswift.connection

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
import kotlin.random.Random

actual object ConnectionManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    actual fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit) {
        val key = deviceKey(target.host, target.port)
        markDeviceConnected(key)
        markDeviceConnected(target.id)
        onResult(PairResult.ACCEPTED)
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
                val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                val selfHost = self?.host ?: ""
                val selfPort = self?.port ?: 52424
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
                        "device_id": "ios-${UIDevice.currentDevice.name}",
                        "device_name": "$selfName",
                        "device_type": "iOS",
                        "push_token": "",
                        "host": "$selfHost",
                        "port": $selfPort
                    }
                """.trimIndent()
                val data = (jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                req.setHTTPBody(data)

                NSURLSession.sharedSession.dataTaskWithRequest(req) { _, _, _ -> }.resume()
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
                val anonKey = PlatformConfig.supabaseAnonKey
                val url = NSURL.URLWithString("$supabaseUrl/rest/v1/pairing_codes?code=eq.$clean&select=*")
                if (url == null) {
                    withContext(Dispatchers.Main) { onResult(PairResult.UNAVAILABLE) }
                    return@launch
                }

                val req = NSMutableURLRequest.requestWithURL(url)
                req.setHTTPMethod("GET")
                req.setValue(anonKey, forHTTPHeaderField = "apikey")
                req.setValue("Bearer $anonKey", forHTTPHeaderField = "Authorization")

                val sem = platform.darwin.dispatch_semaphore_create(0)
                var result: PairResult = PairResult.UNAVAILABLE

                NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, error ->
                    if (error == null && data != null) {
                        val body = NSString.create(data, NSUTF8StringEncoding).toString()
                        val devId = Regex(""""device_id"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                        val devName = Regex(""""device_name"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: "Remote Device"
                        val devHost = Regex(""""host"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: ""
                        val devPort = Regex(""""port"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 52424
                        val devType = Regex(""""device_type"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1) ?: "Desktop"

                        if (!devId.isNullOrBlank() || devHost.isNotBlank()) {
                            val targetDev = DeviceDiscoveryManager.addManualDevice(devHost, devPort, devName, devType)
                            val key = deviceKey(devHost, devPort)
                            markDeviceConnected(key, isRemote = true)
                            if (!devId.isNullOrBlank()) {
                                markDeviceConnected(devId, isRemote = true)
                            }
                            result = PairResult.ACCEPTED
                        }
                    }
                    platform.darwin.dispatch_semaphore_signal(sem)
                }.resume()

                platform.darwin.dispatch_semaphore_wait(sem, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 7_000_000_000L))
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
