package net.syncswift.relay

import kotlinx.serialization.Serializable

enum class TransferMode {
    DIRECT_LAN,
    WEBRTC_P2P,
    ENCRYPTED_MINIO_RELAY
}

@Serializable
data class RelayConfig(
    val stunUrl: String = "stun:127.0.0.1:3478",
    val turnUrl: String = "turn:127.0.0.1:3478",
    val turnUsername: String = "syncswift",
    val turnCredential: String = "turnsecret2026",
    val turnRealm: String = "syncswift.net",
    val minioEndpoint: String = "http://127.0.0.1:9000",
    val minioTempBucket: String = "transfers-temp",
    val minioVaultBucket: String = "vault"
)

object TransferRouteSelector {
    /**
     * Determines optimal transfer strategy based on peer visibility
     */
    fun selectMode(isPeerOnLan: Boolean, isPeerOnline: Boolean): TransferMode {
        return when {
            isPeerOnLan -> TransferMode.DIRECT_LAN
            isPeerOnline -> TransferMode.WEBRTC_P2P
            else -> TransferMode.ENCRYPTED_MINIO_RELAY
        }
    }
}
