package net.syncswift.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val transferId = "tr_${System.currentTimeMillis()}"
        val item = TransferItem(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = fileBytes.size.toLong(),
            mimeType = mimeType,
            bytesTransferred = 0L,
            isSent = true,
            peerName = targetDeviceName,
            status = TransferStatus.IN_PROGRESS,
            timestamp = System.currentTimeMillis()
        )
        val list = _transfers.value.toMutableList()
        list.add(0, item)
        _transfers.value = list

        TransferEngineV2.enqueueTransfer(
            TransferItemV2(
                transferId = transferId,
                fileName = fileName,
                fileSize = fileBytes.size.toLong(),
                mimeType = mimeType,
                peerDeviceId = targetHost,
                peerDeviceName = targetDeviceName,
                peerOsType = "other",
                direction = TransferDirection.OUTGOING,
                bytesTransferred = 0L,
                status = TransferStatusV2.TRANSFERRING
            )
        )
        onSuccess()
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
        onSuccess()
    }

    fun clearHistory() {
        _transfers.value = emptyList()
    }

    fun init(context: Any) {}
    fun startForegroundServer() {}
}
