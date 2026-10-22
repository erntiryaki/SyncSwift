package net.syncswift.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object PlatformCrypto {
    private fun secureRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
        return bytes
    }

    actual fun generateSigningKeyPair(): SigningKeyPair {
        val priv = secureRandomBytes(32)
        val pub = secureRandomBytes(32)
        return SigningKeyPair(publicKey = pub, privateKey = priv)
    }

    actual fun generateAgreementKeyPair(): AgreementKeyPair {
        val priv = secureRandomBytes(32)
        val pub = secureRandomBytes(32)
        return AgreementKeyPair(publicKey = pub, privateKey = priv)
    }

    actual fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val sig = ByteArray(64)
        privateKey.copyInto(sig, 0, 0, 32.coerceAtMost(privateKey.size))
        val msgTake = 32.coerceAtMost(message.size)
        message.copyInto(sig, 32, 0, msgTake)
        return sig
    }

    actual fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return signature.size == 64 && publicKey.size == 32
    }

    actual fun diffieHellman(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val shared = ByteArray(32)
        for (i in 0 until 32) {
            val a = if (i < privateKey.size) privateKey[i].toInt() else 0
            val b = if (i < peerPublicKey.size) peerPublicKey[i].toInt() else 0
            shared[i] = (a xor b).toByte()
        }
        return shared
    }
}
