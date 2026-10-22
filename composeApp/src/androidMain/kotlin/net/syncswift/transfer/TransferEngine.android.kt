package net.syncswift.transfer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.connection.ConnectionManager
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import java.util.Collections

actual object TransferEngine {
    private const val TAG = "TransferEngine"
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    actual val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    private var heartbeatJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        LocalSendServer.start(context.applicationContext)
        startHeartbeatLoop()
    }

    fun getServerPort(): Int = LocalSendServer.getPort()

    actual fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        LocalSendClient.sendFile(
            targetHost = targetHost,
            targetPort = targetPort,
            targetDeviceName = targetDeviceName,
            fileName = fileName,
            fileBytes = fileBytes,
            mimeType = mimeType,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    actual fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        LocalSendClient.sendClipboard(
            targetHost = targetHost,
            targetPort = targetPort,
            targetDeviceName = targetDeviceName,
            text = text,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    actual fun clearHistory() {
        _transfers.value = emptyList()
    }

    fun recordTransfer(item: TransferItem) {
        _transfers.value = listOf(item) + _transfers.value.filter { it.id != item.id }
    }

    fun updateTransferProgress(
        id: String,
        bytes: Long,
        status: TransferStatus,
        savedPath: String? = null
    ) {
        _transfers.value = _transfers.value.map { item ->
            if (item.id == id) {
                item.copy(
                    bytesTransferred = bytes,
                    status = status,
                    localFilePath = savedPath ?: item.localFilePath
                )
            } else {
                item
            }
        }
    }

    /**
     * Automatic Heartbeat loop: Pings connected LAN devices every 3 seconds
     * to keep live online/offline status synchronized instantly.
     */
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(3500)
                try {
                    val connectedKeys = ConnectionManager.connectedDeviceKeys.value
                    if (connectedKeys.isNotEmpty()) {
                        val devices = DeviceDiscoveryManager.devices.value
                        for (dev in devices) {
                            if (ConnectionManager.isDeviceConnected(dev) && !dev.isSelf && dev.host.isNotBlank()) {
                                val isReachable = LocalSendClient.ping(dev.host, dev.port)
                                if (dev.isOnline != isReachable) {
                                    DeviceDiscoveryManager.addOrUpdateConnectedDevice(dev.copy(isOnline = isReachable))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Heartbeat check pass error: ${e.message}")
                }
            }
        }
    }
}
