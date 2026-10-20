package net.syncswift.transfer

import kotlinx.coroutines.flow.StateFlow

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
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val bytesTransferred: Long = 0,
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

expect object TransferEngine {
    val transfers: StateFlow<List<TransferItem>>
    fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    fun clearHistory()
}
