package net.syncswift.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TransferManifest(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val totalChunks: Int,
    val chunkSize: Int = 1048576, // 1 MiB standard chunk size
    val senderDeviceId: String,
    val senderSigningPubKeyBase64: String,
    val recipientDeviceId: String,
    val ephemeralSenderPubKeyBase64: String,
    val wrappedDekBase64: String,
    val signatureBase64: String = "",
    val createdAtMs: Long
) {
    /**
     * Normalized canonical byte representation for Ed25519 signature
     */
    fun canonicalBytes(): ByteArray {
        val canonical = "$transferId:$fileName:$fileSize:$totalChunks:$senderDeviceId:$recipientDeviceId:$wrappedDekBase64:$createdAtMs"
        return canonical.encodeToByteArray()
    }

    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): TransferManifest = Json.decodeFromString(serializer(), json)
    }
}
