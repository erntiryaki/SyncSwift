package net.syncswift.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingTest {

    @Test
    fun testSasCodeGenerationIsDeterministic() {
        val keyA = ByteArray(32) { 1 }
        val keyB = ByteArray(32) { 2 }
        val salt = "SWIFT-1234".encodeToByteArray()

        val sas1 = SasGenerator.computeSas(keyA, keyB, salt)
        val sas2 = SasGenerator.computeSas(keyA, keyB, salt)

        assertEquals(6, sas1.length)
        assertEquals(sas1, sas2)
    }

    @Test
    fun testPairingSessionLifecycle() {
        val session = PairingManager.startInitiatorSession(
            deviceId = "dev_phone_1",
            deviceName = "Pixel 8",
            osType = "android"
        )

        assertEquals(PairingStatus.PENDING, session.status)
        assertFalse(session.isExpired(System.currentTimeMillis()))

        val approved = PairingManager.approveSession(session.sessionId, "dev_desktop_1")
        assertTrue(approved)
        assertEquals(PairingStatus.APPROVED, PairingManager.activeSession.value?.status)
    }
}
