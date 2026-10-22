package net.syncswift.crypto

object DeviceIdentityManager {
    private const val KEY_DEVICE_ID = "syncswift_device_id"
    private const val KEY_SIGNING_PUB = "syncswift_signing_pub"
    private const val KEY_SIGNING_PRIV = "syncswift_signing_priv"
    private const val KEY_AGREEMENT_PUB = "syncswift_agreement_pub"
    private const val KEY_AGREEMENT_PRIV = "syncswift_agreement_priv"

    private var cachedIdentity: DeviceIdentity? = null

    fun getOrCreateIdentity(deviceName: String, osType: String): DeviceIdentity {
        cachedIdentity?.let { return it }

        var deviceId = SecureKeyStore.getString(KEY_DEVICE_ID)
        val signPub = SecureKeyStore.getBytes(KEY_SIGNING_PUB)
        val signPriv = SecureKeyStore.getBytes(KEY_SIGNING_PRIV)
        val agreePub = SecureKeyStore.getBytes(KEY_AGREEMENT_PUB)
        val agreePriv = SecureKeyStore.getBytes(KEY_AGREEMENT_PRIV)

        val signingPair = if (signPub != null && signPriv != null && signPub.size == 32) {
            SigningKeyPair(publicKey = signPub, privateKey = signPriv)
        } else {
            val generated = PlatformCrypto.generateSigningKeyPair()
            SecureKeyStore.putBytes(KEY_SIGNING_PUB, generated.publicKey)
            SecureKeyStore.putBytes(KEY_SIGNING_PRIV, generated.privateKey)
            generated
        }

        val agreementPair = if (agreePub != null && agreePriv != null && agreePub.size == 32) {
            AgreementKeyPair(publicKey = agreePub, privateKey = agreePriv)
        } else {
            val generated = PlatformCrypto.generateAgreementKeyPair()
            SecureKeyStore.putBytes(KEY_AGREEMENT_PUB, generated.publicKey)
            SecureKeyStore.putBytes(KEY_AGREEMENT_PRIV, generated.privateKey)
            generated
        }

        if (deviceId.isNullOrBlank()) {
            deviceId = generateRandomUuid()
            SecureKeyStore.putString(KEY_DEVICE_ID, deviceId)
        }

        val identity = DeviceIdentity(
            deviceId = deviceId,
            deviceName = deviceName,
            osType = osType,
            signingKeyPair = signingPair,
            agreementKeyPair = agreementPair
        )
        cachedIdentity = identity
        return identity
    }

    private fun generateRandomUuid(): String {
        // Simple 128-bit hex UUID format
        val bytes = ByteArray(16)
        val temp = PlatformCrypto.generateSigningKeyPair().publicKey
        for (i in 0 until 16) {
            bytes[i] = temp[i]
        }
        // Set version 4 and variant RFC 4122
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        val sb = StringBuilder()
        for (i in bytes.indices) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append("-")
            val b = bytes[i].toInt() and 0xFF
            if (b < 16) sb.append("0")
            sb.append(b.toString(16))
        }
        return sb.toString()
    }
}
