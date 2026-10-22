package net.syncswift.storage

import net.syncswift.relay.TransferMode
import net.syncswift.relay.TransferRouteSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaultAndRelayTest {

    @Test
    fun testTransferRouteFallbackOrder() {
        // 1. LAN available
        assertEquals(TransferMode.DIRECT_LAN, TransferRouteSelector.selectMode(isPeerOnLan = true, isPeerOnline = true))
        assertEquals(TransferMode.DIRECT_LAN, TransferRouteSelector.selectMode(isPeerOnLan = true, isPeerOnline = false))

        // 2. Peer online over WAN -> WebRTC P2P
        assertEquals(TransferMode.WEBRTC_P2P, TransferRouteSelector.selectMode(isPeerOnLan = false, isPeerOnline = true))

        // 3. Peer offline -> Encrypted MinIO Relay
        assertEquals(TransferMode.ENCRYPTED_MINIO_RELAY, TransferRouteSelector.selectMode(isPeerOnLan = false, isPeerOnline = false))
    }

    @Test
    fun testPersonalVaultManagerLifecycle() {
        val item = VaultFileItem(
            id = "v_001",
            fileName = "tax_2026.pdf",
            fileSize = 1024L * 1024L * 5, // 5 MB
            mimeType = "application/pdf",
            s3Key = "vault/u_123/v_001.enc"
        )

        PersonalVaultManager.addFile(item)
        assertEquals(1, PersonalVaultManager.vaultFiles.value.size)
        assertEquals("5.0 MB", PersonalVaultManager.vaultFiles.value.first().formattedSize)

        PersonalVaultManager.removeFile("v_001")
        assertTrue(PersonalVaultManager.vaultFiles.value.isEmpty())
    }
}
