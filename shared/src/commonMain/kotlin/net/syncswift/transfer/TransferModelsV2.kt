package net.syncswift.transfer

import kotlinx.serialization.Serializable

enum class TransferStatusV2 {
    QUEUED,
    CONNECTING,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

@Serializable
data class TransferItemV2(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val peerDeviceId: String,
    val peerDeviceName: String,
    val peerOsType: String,
    val direction: TransferDirection,
    val bytesTransferred: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val status: TransferStatusV2 = TransferStatusV2.QUEUED,
    val errorMessage: String? = null,
    val createdAtMs: Long = 0L,
    val completedAtMs: Long? = null
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() {
            val mbps = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
            return if (mbps >= 1.0) {
                val rounded = ((mbps * 10).toLong()) / 10.0
                "${rounded} MB/s"
            } else {
                "${speedBytesPerSec / 1024} KB/s"
            }
        }

    val formattedSize: String
        get() {
            val mb = fileSize.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                val rounded = ((mb * 10).toLong()) / 10.0
                "${rounded} MB"
            } else {
                "${fileSize / 1024} KB"
            }
        }
}

@Serializable
data class LanDiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val osType: String,
    val host: String,
    val port: Int = 52424,
    val signingPubKeyBase64: String,
    val agreementPubKeyBase64: String,
    val lastSeenMs: Long
)
