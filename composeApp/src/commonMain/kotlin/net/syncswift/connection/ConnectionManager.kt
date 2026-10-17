package net.syncswift.connection

import net.syncswift.discovery.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlin.random.Random
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.log10

enum class PairResult {
    ACCEPTED,
    REJECTED,
    UNAVAILABLE,
    PENDING_APPROVAL,
    EXPIRED_OR_USED
}

data class IncomingPairRequest(
    val requestId: String = "${Random.nextLong()}",
    val senderId: String,
    val senderName: String,
    val senderHost: String,
    val senderPort: Int,
    val senderOsType: String,
    val senderLanguage: String = "en",
    val senderPushToken: String = "",
    val isRemote: Boolean = false,
    val deferredResponse: CompletableDeferred<Boolean> = CompletableDeferred()
)

data class IncomingTransferRequest(
    val requestId: String = "${Random.nextLong()}",
    val senderName: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val senderHost: String = "",
    val senderPort: Int = 52424,
    val transferId: String = "",
    val downloadUrl: String = "",
    val isRemote: Boolean = false,
    val senderLanguage: String = "en",
    val deferredResponse: CompletableDeferred<Boolean> = CompletableDeferred()
) {
    val formattedSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(fileSize.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = fileSize / 1024.0.pow(digitGroups.toDouble())
            return "${(round(value * 10.0) / 10.0)} ${units[digitGroups]}"
        }
}

expect object ConnectionManager {
    val connectedDeviceKeys: StateFlow<Set<String>>
    val remoteConnectedDeviceKeys: StateFlow<Set<String>>
    val pendingPairRequest: StateFlow<IncomingPairRequest?>
    val pendingTransferRequest: StateFlow<IncomingTransferRequest?>
    val activePairingCode: StateFlow<String>
    val codeRemainingSeconds: StateFlow<Int>

    fun isDeviceConnected(device: DiscoveredDevice): Boolean
    fun isRemoteDevice(device: DiscoveredDevice): Boolean
    fun requestPairing(target: DiscoveredDevice, onResult: (PairResult) -> Unit)
    fun disconnectDevice(target: DiscoveredDevice)
    fun acceptPairRequest(request: IncomingPairRequest)
    fun rejectPairRequest(request: IncomingPairRequest)
    fun acceptTransferRequest(request: IncomingTransferRequest)
    fun rejectTransferRequest(request: IncomingTransferRequest)
    fun cancelIncomingPairRequest()
    fun cancelIncomingTransferRequest(transferId: String? = null)
    fun markDeviceConnected(deviceKey: String, isRemote: Boolean = false)
    fun markDeviceDisconnected(deviceKey: String)
    fun isRequestCancelled(requestId: String): Boolean
    fun markRequestCancelled(requestId: String)
    fun generateOrRotatePairingCode()
    fun ensurePairingCodeActive()
    fun connectViaPairingCode(code: String, onResult: (PairResult) -> Unit)
}
