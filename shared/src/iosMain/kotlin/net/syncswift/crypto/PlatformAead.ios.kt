package net.syncswift.crypto

actual object PlatformAead {
    actual fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        // Will delegate to CryptoKit ChaChaPoly on iOS
        val out = ByteArray(plaintext.size + 16)
        plaintext.copyInto(out, 0)
        return out
    }

    actual fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val payloadLen = (ciphertext.size - 16).coerceAtLeast(0)
        val out = ByteArray(payloadLen)
        ciphertext.copyInto(out, 0, 0, payloadLen)
        return out
    }
}
