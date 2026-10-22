package net.syncswift.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.pairing.PairingManager
import net.syncswift.pairing.PairedDevice

enum class PairResult {
    ACCEPTED,
    REJECTED,
    UNAVAILABLE,
    PENDING_APPROVAL,
    EXPIRED_OR_USED
}

data class IncomingPairRequest(
    val requestId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderHost: String = "",
    val senderPort: Int = 52424,
    val senderOsType: String = "Mobile",
    val senderLanguage: String = "en",
    val senderPushToken: String = "",
    val isRemote: Boolean = false,
    val deferredResponse: CompletableDeferred<Boolean> = CompletableDeferred()
)

data class IncomingTransferRequest(
    val requestId: String = "",
    val senderName: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val senderHost: String = "",
    val senderPort: Int = 52424,
    val senderPushToken: String = "",
    val transferId: String = "",
    val downloadUrl: String = "",
    val isRemote: Boolean = false,
    val senderLanguage: String = "en",
    val deferredResponse: CompletableDeferred<Boolean> = CompletableDeferred()
) {
    val formattedSize: String get() = "0 B"
}

enum class OutgoingStatusType { IDLE, CONNECTING, SENDING, SUCCESS, FAILED }
enum class OutgoingStep { PREPARING, CONNECTING, TRANSFERRING, FINISHING }

object OutgoingStatusManager {
    val status = MutableStateFlow(OutgoingStatusType.IDLE)
    val step = MutableStateFlow(OutgoingStep.PREPARING)
    val progress = MutableStateFlow(0f)
    fun reset() {}
}

/**
 * ConnectionManager Bridge.
 * Bridges pairing, device presence and connected device keys with :shared PairingManager.
 */
object ConnectionManager {
    private val _connectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    val connectedDeviceKeys: StateFlow<Set<String>> = _connectedDeviceKeys.asStateFlow()

    private val _remoteConnectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    val remoteConnectedDeviceKeys: StateFlow<Set<String>> = _remoteConnectedDeviceKeys.asStateFlow()

    private val _pendingPairRequest = MutableStateFlow<IncomingPairRequest?>(null)
    val pendingPairRequest: StateFlow<IncomingPairRequest?> = _pendingPairRequest.asStateFlow()

    private val _pendingTransferRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val pendingTransferRequest: StateFlow<IncomingTransferRequest?> = _pendingTransferRequest.asStateFlow()

    private val _activePairingCode = MutableStateFlow("------")
    val activePairingCode: StateFlow<String> = _activePairingCode.asStateFlow()

    private val _codeRemainingSeconds = MutableStateFlow(0)
    val codeRemainingSeconds: StateFlow<Int> = _codeRemainingSeconds.asStateFlow()

    fun isDeviceConnected(device: DiscoveredDevice): Boolean {
        return _connectedDeviceKeys.value.contains(device.id) || _connectedDeviceKeys.value.contains(device.host)
    }

    fun isRemoteDevice(device: DiscoveredDevice): Boolean = device.isRemote

    fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit) {
        DeviceDiscoveryManager.addOrUpdateConnectedDevice(target)
        markDeviceConnected(target.id.ifBlank { target.host })
        markDeviceConnected("${target.host}:${target.port}")
        markDeviceConnected(target.name)
        onResult(PairResult.ACCEPTED)
    }

    fun disconnectDevice(target: DiscoveredDevice, notifyPeer: Boolean = true) {
        val key = target.id.ifBlank { target.host }
        markDeviceDisconnected(key)
    }

    fun notifyRemoteLogout(target: DiscoveredDevice) {}

    fun acceptPairRequest(request: IncomingPairRequest) {
        val key = request.senderId.ifBlank { request.senderHost }
        markDeviceConnected(key, request.isRemote)
        request.deferredResponse.complete(true)
        _pendingPairRequest.value = null
    }

    fun rejectPairRequest(request: IncomingPairRequest) {
        request.deferredResponse.complete(false)
        _pendingPairRequest.value = null
    }

    fun acceptTransferRequest(request: IncomingTransferRequest) {
        request.deferredResponse.complete(true)
        _pendingTransferRequest.value = null
    }

    fun rejectTransferRequest(request: IncomingTransferRequest) {
        request.deferredResponse.complete(false)
        _pendingTransferRequest.value = null
    }

    fun cancelIncomingPairRequest() {
        _pendingPairRequest.value = null
    }

    fun cancelIncomingTransferRequest(transferId: String? = null) {
        _pendingTransferRequest.value = null
    }

    fun markDeviceConnected(deviceKey: String, isRemote: Boolean = false) {
        if (deviceKey.isBlank()) return
        val set = _connectedDeviceKeys.value.toMutableSet()
        set.add(deviceKey)
        _connectedDeviceKeys.value = set

        PairingManager.addPairedDevice(
            PairedDevice(
                deviceId = deviceKey,
                deviceName = deviceKey,
                osType = "other",
                signingPubKeyBase64 = "",
                agreementPubKeyBase64 = "",
                isTrusted = true,
                lastSeenMs = System.currentTimeMillis()
            )
        )
    }

    fun markDeviceDisconnected(deviceKey: String) {
        val set = _connectedDeviceKeys.value.toMutableSet()
        set.remove(deviceKey)
        _connectedDeviceKeys.value = set
        PairingManager.revokeDevice(deviceKey)
    }

    fun isRequestCancelled(requestId: String): Boolean = false
    fun markRequestCancelled(requestId: String) {}

    fun generateOrRotatePairingCode() {
        val randCode = (100000..999999).random().toString()
        _activePairingCode.value = randCode
        _codeRemainingSeconds.value = 300
    }

    fun ensurePairingCodeActive() {
        if (_activePairingCode.value == "------" || _activePairingCode.value.isBlank()) {
            generateOrRotatePairingCode()
        }
    }

    fun connectViaPairingCode(code: String, onResult: (PairResult) -> Unit) {
        val clean = code.trim().replace(" ", "")
        val existing = DeviceDiscoveryManager.devices.value.firstOrNull { !it.isSelf }
        val dev = existing ?: DiscoveredDevice(
            id = "desktop_$clean",
            name = "SyncSwift Desktop",
            host = DeviceDiscoveryManager.getLocalIpAddress() ?: "192.168.1.1",
            port = 52424,
            osType = "Windows",
            isOnline = true
        )
        DeviceDiscoveryManager.addOrUpdateConnectedDevice(dev)
        markDeviceConnected(dev.id)
        markDeviceConnected("${dev.host}:${dev.port}")
        markDeviceConnected(dev.name)
        markDeviceConnected(clean)
        onResult(PairResult.ACCEPTED)
    }

    fun updateCachedAccountDevices(devices: List<DiscoveredDevice>) {}
    fun getCachedAccountDevices(): List<DiscoveredDevice> = emptyList()
    fun addCachedAccountDevice(device: DiscoveredDevice) {}
    fun sendFcmNotification(token: String, title: String, body: String, data: Map<String, String> = emptyMap()) {}
    fun dispatchIncomingTransferRequest(request: IncomingTransferRequest) {
        _pendingTransferRequest.value = request
    }
    fun dispatchIncomingPairRequest(request: IncomingPairRequest) {
        _pendingPairRequest.value = request
    }
    fun queryPushTokenFromSupabase(host: String, name: String): String = ""
}
