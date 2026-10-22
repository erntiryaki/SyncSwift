package net.syncswift.crypto

import kotlinx.serialization.Serializable

@Serializable
data class SigningKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    fun publicKeyBase64(): String = Base64Util.encode(publicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigningKeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

@Serializable
data class AgreementKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    fun publicKeyBase64(): String = Base64Util.encode(publicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgreementKeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

data class DeviceIdentity(
    val deviceId: String,
    val deviceName: String,
    val osType: String,
    val signingKeyPair: SigningKeyPair,
    val agreementKeyPair: AgreementKeyPair
) {
    val signingPublicKeyBase64: String get() = signingKeyPair.publicKeyBase64()
    val agreementPublicKeyBase64: String get() = agreementKeyPair.publicKeyBase64()
}

object Base64Util {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0

            out.append(CHARS[b0 shr 2])
            out.append(CHARS[((b0 and 0x03) shl 4) or (b1 shr 4)])
            if (i + 1 < data.size) {
                out.append(CHARS[((b1 and 0x0F) shl 2) or (b2 shr 6)])
            } else {
                out.append('=')
            }
            if (i + 2 < data.size) {
                out.append(CHARS[b2 and 0x3F])
            } else {
                out.append('=')
            }
            i += 3
        }
        return out.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.trim().replace("\r", "").replace("\n", "")
        if (clean.isEmpty()) return ByteArray(0)
        val pad = clean.count { it == '=' }
        val len = (clean.length * 6 / 8) - pad
        val out = ByteArray(len)
        var buffer = 0
        var bits = 0
        var outIdx = 0

        for (ch in clean) {
            if (ch == '=') break
            val idx = CHARS.indexOf(ch)
            if (idx == -1) continue
            buffer = (buffer shl 6) or idx
            bits += 6
            if (bits >= 8) {
                bits -= 8
                if (outIdx < len) {
                    out[outIdx++] = ((buffer shr bits) and 0xFF).toByte()
                }
            }
        }
        return out
    }
}
