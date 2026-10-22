package net.syncswift.clipboard

import net.syncswift.agent.SyncDirection
import net.syncswift.agent.SyncRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardAndAgentTest {

    @Test
    fun testEncryptedClipboardLifecycle() {
        EncryptedClipboardManager.setAutoSyncEnabled(true)
        assertTrue(EncryptedClipboardManager.isAutoSyncEnabled.value)

        val payload = EncryptedClipboardManager.onOutgoingClipboard(
            text = "Confidential Token 12345",
            deviceId = "phone_01",
            deviceName = "Pixel 8"
        )

        assertEquals("Confidential Token 1", payload.previewText)
        assertEquals("Confidential Token 12345", EncryptedClipboardManager.lastSyncedText.value)
        assertEquals(1, EncryptedClipboardManager.recentClipboardHistory.value.size)
    }

    @Test
    fun testSyncRuleModel() {
        val rule = SyncRule(
            id = "rule_01",
            name = "Project Docs Mirror",
            localPath = "C:\\Projects",
            remoteDeviceId = "phone_01",
            remotePath = "/storage/emulated/0/Projects",
            direction = SyncDirection.TWO_WAY_MIRROR
        )

        assertTrue(rule.isEnabled)
        assertEquals(SyncDirection.TWO_WAY_MIRROR, rule.direction)
    }
}
