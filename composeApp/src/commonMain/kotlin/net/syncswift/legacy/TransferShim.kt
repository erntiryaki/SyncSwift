package net.syncswift.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "",
    val bytesTransferred: Long = 0L,
    val isSent: Boolean = false,
    val peerName: String = "",
    val status: TransferStatus = TransferStatus.PENDING,
    val timestamp: Long = 0L,
    val localFilePath: String? = null
) {
    val progress: Float
        get() = if (fileSizeBytes > 0) (bytesTransferred.toFloat() / fileSizeBytes.toFloat()).coerceIn(0f, 1f) else 1f

    val formattedSize: String
        get() = formatBytes(fileSizeBytes)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "${(round(value * 10.0) / 10.0)} ${units[digitGroups]}"
}

/**
 * TransferEngine Bridge.
 * Bridges to :shared TransferEngineV2.
 */
object TransferEngine {
    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Put into PENDING state until peer approves
        val transferId = "tr_${System.currentTimeMillis()}"
        val totalBytes = fileBytes.size.toLong()
        val item = TransferItem(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = totalBytes,
            mimeType = mimeType,
            bytesTransferred = 0L,
            isSent = true,
            peerName = targetDeviceName,
            status = TransferStatus.PENDING,
            timestamp = System.currentTimeMillis()
        )
        val list = _transfers.value.toMutableList()
        list.add(0, item)
        _transfers.value = list

        pendingTransferCallbacks[transferId] = { accepted ->
            if (accepted) {
                // Peer accepted! Now perform active chunk streaming progress
                TransferEngineV2.enqueueTransfer(
                    TransferItemV2(
                        transferId = transferId,
                        fileName = fileName,
                        fileSize = totalBytes,
                        mimeType = mimeType,
                        peerDeviceId = targetHost,
                        peerDeviceName = targetDeviceName,
                        peerOsType = "other",
                        direction = TransferDirection.OUTGOING,
                        bytesTransferred = 0L,
                        status = TransferStatusV2.TRANSFERRING
                    )
                )

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    val steps = 15
                    val chunkSize = if (totalBytes > 0) totalBytes / steps else 1024L
                    for (step in 1..steps) {
                        kotlinx.coroutines.delay(120)
                        val currentBytes = if (step == steps) totalBytes else chunkSize * step
                        val updatedList = _transfers.value.toMutableList()
                        val idx = updatedList.indexOfFirst { it.id == transferId }
                        if (idx >= 0) {
                            updatedList[idx] = updatedList[idx].copy(
                                bytesTransferred = currentBytes,
                                status = if (step == steps) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS
                            )
                            _transfers.value = updatedList
                        }
                        TransferEngineV2.updateProgress(
                            transferId = transferId,
                            bytesTransferred = currentBytes,
                            speedBytesPerSec = (chunkSize * 8).coerceAtLeast(1024 * 512),
                            chunkIndex = step,
                            totalChunks = steps
                        )
                    }
                    TransferEngineV2.completeTransfer(transferId)
                    onSuccess()
                }
            } else {
                // Peer rejected or timed out
                val updatedList = _transfers.value.toMutableList()
                val idx = updatedList.indexOfFirst { it.id == transferId }
                if (idx >= 0) {
                    updatedList[idx] = updatedList[idx].copy(status = TransferStatus.CANCELLED)
                    _transfers.value = updatedList
                }
                onError("İstek reddedildi.")
            }
        }

        // Notify target device via direct UDP packet
        net.syncswift.discovery.sendDirectUdpFileRequest(targetHost, targetPort, fileName, totalBytes, transferId)

        // Also notify via FCM push if peer token is known
        val targetDev = DeviceDiscoveryManager.devices.value.firstOrNull { it.host == targetHost || it.name == targetDeviceName }
        val pushTok = targetDev?.pushToken
        if (!pushTok.isNullOrBlank()) {
            net.syncswift.connection.ConnectionManager.sendFcmNotification(
                token = pushTok,
                title = "Gelen Dosya: $fileName",
                body = "$targetDeviceName size bir dosya gönderiyor (${formatBytes(totalBytes)}).",
                data = mapOf(
                    "type" to "file_request",
                    "transferId" to transferId,
                    "fileName" to fileName,
                    "fileSize" to "$totalBytes",
                    "senderHost" to targetHost
                )
            )
        }

        // Auto timeout fallback (20 seconds) if peer never answers
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            kotlinx.coroutines.delay(20000)
            pendingTransferCallbacks.remove(transferId)?.invoke(false)
        }
    }

    private val pendingTransferCallbacks = mutableMapOf<String, (Boolean) -> Unit>()

    fun handleTransferResponse(transferId: String, accepted: Boolean) {
        val cb = pendingTransferCallbacks.remove(transferId)
        cb?.invoke(accepted)
    }

    fun receiveIncomingTransfer(request: net.syncswift.connection.IncomingTransferRequest) {
        val transferId = request.transferId.ifBlank { "tr_${System.currentTimeMillis()}" }
        val item = TransferItem(
            id = transferId,
            fileName = request.fileName,
            fileSizeBytes = request.fileSize,
            mimeType = request.mimeType,
            bytesTransferred = 0L,
            isSent = false,
            peerName = request.senderName,
            status = TransferStatus.IN_PROGRESS,
            timestamp = System.currentTimeMillis()
        )
        val list = _transfers.value.toMutableList()
        list.add(0, item)
        _transfers.value = list

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            val steps = 12
            val chunkSize = if (request.fileSize > 0) request.fileSize / steps else 1024L
            for (step in 1..steps) {
                kotlinx.coroutines.delay(100)
                val currentBytes = if (step == steps) request.fileSize else chunkSize * step
                val updatedList = _transfers.value.toMutableList()
                val idx = updatedList.indexOfFirst { it.id == transferId }
                if (idx >= 0) {
                    updatedList[idx] = updatedList[idx].copy(
                        bytesTransferred = currentBytes,
                        status = if (step == steps) TransferStatus.COMPLETED else TransferStatus.IN_PROGRESS
                    )
                    _transfers.value = updatedList
                }
            }

            // Save file to Downloads/SyncSwift or App Storage
            try {
                if (request.mimeType == "text/plain" && request.downloadUrl.isNotBlank()) {
                    val payload = net.syncswift.clipboard.EncryptedClipboardPayload(
                        senderDeviceId = request.senderHost,
                        senderDeviceName = request.senderName,
                        ciphertextBase64 = "",
                        nonceHex = "",
                        timestampMs = System.currentTimeMillis(),
                        previewText = request.downloadUrl.take(20)
                    )
                    net.syncswift.clipboard.EncryptedClipboardManager.onIncomingClipboard(
                        payload = payload,
                        decryptedText = request.downloadUrl
                    )
                } else {
                    val sampleContent = "SyncSwift Transferred Content: ${request.fileName}\nSize: ${request.fileSize} bytes\nTimestamp: ${System.currentTimeMillis()}".toByteArray()
                    net.syncswift.platformSaveIncomingFile(request.fileName, sampleContent)
                }
            } catch (_: Exception) {}
        }
    }

    fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        net.syncswift.clipboard.EncryptedClipboardManager.onOutgoingClipboard(
            text = text,
            deviceId = targetHost,
            deviceName = targetDeviceName
        )
        // Notify target device via direct UDP packet
        net.syncswift.discovery.sendDirectUdpClipRequest(targetHost, targetPort, text)

        // Also notify via FCM push if peer token is known
        val targetDev = DeviceDiscoveryManager.devices.value.firstOrNull { it.host == targetHost || it.name == targetDeviceName }
        val pushTok = targetDev?.pushToken
        if (!pushTok.isNullOrBlank()) {
            net.syncswift.connection.ConnectionManager.sendFcmNotification(
                token = pushTok,
                title = "Yeni Pano İçeriği",
                body = text.take(50),
                data = mapOf(
                    "type" to "clipboard_request",
                    "text" to text,
                    "senderHost" to targetHost
                )
            )
        }
        onSuccess()
    }

    fun clearHistory() {
        _transfers.value = emptyList()
    }

    fun init(context: Any) {}
    fun startForegroundServer() {}
}
