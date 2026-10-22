package net.syncswift.transfer

import net.syncswift.discovery.LanDiscoveryEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferEngineTest {

    @Test
    fun testTransferProgressCalculation() {
        val item = TransferItemV2(
            transferId = "tr_001",
            fileName = "video.mp4",
            fileSize = 10_000_000L,
            mimeType = "video/mp4",
            peerDeviceId = "win_01",
            peerDeviceName = "Windows PC",
            peerOsType = "windows",
            direction = TransferDirection.OUTGOING,
            bytesTransferred = 5_000_000L
        )

        assertEquals(0.5f, item.progress, 0.01f)
    }

    @Test
    fun testLanDiscoveryEngineLifecycle() {
        val dev = LanDiscoveredDevice(
            deviceId = "dev_123",
            deviceName = "MacBook Pro",
            osType = "macos",
            host = "192.168.1.50",
            port = 52424,
            signingPubKeyBase64 = "pubKeySign",
            agreementPubKeyBase64 = "pubKeyAgree",
            lastSeenMs = System.currentTimeMillis()
        )

        LanDiscoveryEngine.updateDevice(dev)
        assertEquals(1, LanDiscoveryEngine.devices.value.size)
        assertEquals("MacBook Pro", LanDiscoveryEngine.devices.value.first().deviceName)

        LanDiscoveryEngine.removeDevice("dev_123")
        assertTrue(LanDiscoveryEngine.devices.value.isEmpty())
    }
}
