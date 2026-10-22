package net.syncswift.webrtc

/**
 * Connection states for WebRTC DataChannel P2P sessions.
 */
enum class WebRtcState {
    IDLE,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    DISCONNECTED,
    FAILED
}

/**
 * Message types exchanged through the signaling relay (Supabase Realtime / REST).
 */
enum class SignalingType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    PING,
    PONG,
    DISCONNECT
}

/**
 * Signaling envelope exchanged between peers to establish P2P DataChannels.
 */
data class SignalingEnvelope(
    val id: String = generateRandomId(),
    val senderId: String,
    val senderName: String,
    val targetId: String,
    val type: SignalingType,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * High-speed binary chunk framing for WebRTC DataChannel file streaming.
 */
data class WebRtcFileChunk(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val payloadSize: Int,
    val payload: ByteArray,
    val checksum: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebRtcFileChunk) return false
        return transferId == other.transferId && chunkIndex == other.chunkIndex
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + chunkIndex
        return result
    }
}

/**
 * Control messages sent across WebRTC DataChannel.
 */
sealed class WebRtcDataMessage {
    data class StartTransfer(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val totalChunks: Int,
        val chunkSize: Int
    ) : WebRtcDataMessage()

    data class ChunkAck(
        val transferId: String,
        val chunkIndex: Int
    ) : WebRtcDataMessage()

    data class CompleteTransfer(
        val transferId: String,
        val sha256: String? = null
    ) : WebRtcDataMessage()

    data class CancelTransfer(
        val transferId: String,
        val reason: String
    ) : WebRtcDataMessage()

    data class ClipboardSync(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebRtcDataMessage()
}

internal fun generateRandomId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16).map { chars.random() }.joinToString("")
}
