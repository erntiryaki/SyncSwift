package net.syncswift.connection

import net.syncswift.discovery.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object ConnectionManager {
    private val _connectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val connectedDeviceKeys: StateFlow<Set<String>> = _connectedDeviceKeys.asStateFlow()

    private val _pendingPairRequest = MutableStateFlow<IncomingPairRequest?>(null)
    actual val pendingPairRequest: StateFlow<IncomingPairRequest?> = _pendingPairRequest.asStateFlow()

    private val _pendingTransferRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    actual val pendingTransferRequest: StateFlow<IncomingTransferRequest?> = _pendingTransferRequest.asStateFlow()

    private val _remoteConnectedDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    actual val remoteConnectedDeviceKeys: StateFlow<Set<String>> = _remoteConnectedDeviceKeys.asStateFlow()

    private val _activePairingCode = MutableStateFlow("834 291")
    actual val activePairingCode: StateFlow<String> = _activePairingCode.asStateFlow()

    private val _codeRemainingSeconds = MutableStateFlow(60)
    actual val codeRemainingSeconds: StateFlow<Int> = _codeRemainingSeconds.asStateFlow()

    actual fun isDeviceConnected(device: DiscoveredDevice): Boolean = false
    actual fun isRemoteDevice(device: DiscoveredDevice): Boolean = false

    actual fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit) {
        onResult(PairResult.UNAVAILABLE)
    }

    actual fun disconnectDevice(target: DiscoveredDevice) {}

    actual fun acceptPairRequest(request: IncomingPairRequest) {}

    actual fun rejectPairRequest(request: IncomingPairRequest) {}

    actual fun acceptTransferRequest(request: IncomingTransferRequest) {}

    actual fun rejectTransferRequest(request: IncomingTransferRequest) {}

    actual fun markDeviceConnected(deviceKey: String, isRemote: Boolean) {}

    actual fun markDeviceDisconnected(deviceKey: String) {}

    actual fun generateOrRotatePairingCode() {}

    actual fun connectViaPairingCode(code: String, onResult: (PairResult) -> Unit) {
        onResult(PairResult.UNAVAILABLE)
    }
}
