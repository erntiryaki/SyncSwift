package net.syncswift.pairing

import kotlinx.serialization.Serializable

enum class PairingStatus {
    PENDING,
    APPROVED,
    TRUSTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}

@Serializable
data class PairingSession(
    val sessionId: String,
    val initiatorDeviceId: String,
    val targetDeviceId: String? = null,
    val pairingCode: String,
    val sasCode: String,
    val status: PairingStatus,
    val expiresAtMs: Long
) {
    fun isExpired(nowMs: Long): Boolean = nowMs > expiresAtMs || status == PairingStatus.EXPIRED
}

@Serializable
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val osType: String,
    val signingPubKeyBase64: String,
    val agreementPubKeyBase64: String,
    val isTrusted: Boolean = false,
    val lastSeenMs: Long = 0L,
    val canTransferFiles: Boolean = true,
    val canSyncClipboard: Boolean = true,
    val canAccessVault: Boolean = false
)

object SasGenerator {
    /**
     * Compute a 6-digit Short Authentication String (SAS) for visual peer verification.
     */
    fun computeSas(keyA: ByteArray, keyB: ByteArray, salt: ByteArray): String {
        var hash = 0x811c9dc5.toInt() // FNV-1a 32-bit offset basis
        val prime = 0x01000193

        for (b in keyA) {
            hash = (hash xor (b.toInt() and 0xFF)) * prime
        }
        for (b in keyB) {
            hash = (hash xor (b.toInt() and 0xFF)) * prime
        }
        for (b in salt) {
            hash = (hash xor (b.toInt() and 0xFF)) * prime
        }

        val positive = hash and 0x7FFFFFFF
        val digits = (positive % 1_000_000).toString().padStart(6, '0')
        return digits
    }

    /**
     * Generate a human-readable 8-character pairing code: e.g. "SWIFT-9482"
     */
    fun generatePairingCode(): String {
        val randomNum = (1000..9999).random()
        return "SWIFT-$randomNum"
    }
}
