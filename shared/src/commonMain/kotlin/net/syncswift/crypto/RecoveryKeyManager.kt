package net.syncswift.crypto

object RecoveryKeyManager {
    private const val KEY_RECOVERY_KEY = "syncswift_vault_recovery_key"

    /**
     * Generate or load existing 256-bit master recovery key formatted as a 64-char hex string
     */
    fun getOrCreateRecoveryKey(): String {
        val existing = SecureKeyStore.getString(KEY_RECOVERY_KEY)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val rawKey = PlatformCrypto.generateSigningKeyPair().privateKey // 32 random bytes
        val hex = rawKey.joinToString("") { "%02x".format(it) }
        SecureKeyStore.putString(KEY_RECOVERY_KEY, hex)
        return hex
    }

    /**
     * Restore vault access using user's backup key
     */
    fun restoreRecoveryKey(hexKey: String): Boolean {
        val clean = hexKey.trim().lowercase()
        if (clean.length != 64 || clean.any { it !in "0123456789abcdef" }) {
            return false
        }
        SecureKeyStore.putString(KEY_RECOVERY_KEY, clean)
        return true
    }

    /**
     * Check if recovery key is configured on this device
     */
    fun hasRecoveryKey(): Boolean {
        val key = SecureKeyStore.getString(KEY_RECOVERY_KEY)
        return !key.isNullOrBlank() && key.length == 64
    }
}
