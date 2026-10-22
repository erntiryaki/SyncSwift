package net.syncswift.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoTest {

    @Test
    fun testBase64EncodingAndDecoding() {
        val original = "SyncSwift v2 Protocol Test".encodeToByteArray()
        val encoded = Base64Util.encode(original)
        val decoded = Base64Util.decode(encoded)
        assertEquals("SyncSwift v2 Protocol Test", decoded.decodeToString())
    }

    @Test
    fun testSigningKeyPairGeneration() {
        val keyPair = PlatformCrypto.generateSigningKeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertTrue(keyPair.publicKeyBase64().isNotEmpty())
    }

    @Test
    fun testAgreementKeyPairGeneration() {
        val keyPair = PlatformCrypto.generateAgreementKeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertTrue(keyPair.publicKeyBase64().isNotEmpty())
    }
}
