package net.syncswift.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AeadTest {

    @Test
    fun testChaCha20Poly1305EncryptDecrypt() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val nonce = ByteArray(12) { (it + 5).toByte() }
        val aad = "SWIFT_TRANSFER_100".encodeToByteArray()
        val plaintext = "Hello SyncSwift Encrypted Chunk Payload".encodeToByteArray()

        val ciphertext = PlatformAead.encrypt(key, nonce, plaintext, aad)
        assertTrue(ciphertext.size >= plaintext.size + 16) // 16-byte Poly1305 tag

        val decrypted = PlatformAead.decrypt(key, nonce, ciphertext, aad)
        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
    }

    @Test
    fun testTransferManifestCanonicalBytes() {
        val manifest = TransferManifest(
            transferId = "tr_001",
            fileName = "photo.jpg",
            fileSize = 2048L,
            mimeType = "image/jpeg",
            totalChunks = 1,
            senderDeviceId = "dev_a",
            senderSigningPubKeyBase64 = "pubA",
            recipientDeviceId = "dev_b",
            ephemeralSenderPubKeyBase64 = "ephA",
            wrappedDekBase64 = "dekWrapped",
            createdAtMs = 1729623000000L
        )

        val bytes = manifest.canonicalBytes()
        assertTrue(bytes.isNotEmpty())

        val json = manifest.toJson()
        val restored = TransferManifest.fromJson(json)
        assertEquals(manifest.transferId, restored.transferId)
        assertEquals(manifest.fileName, restored.fileName)
    }
}
