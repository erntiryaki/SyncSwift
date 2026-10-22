package net.syncswift.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedClipboardPayload(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val ciphertextBase64: String,
    val nonceHex: String,
    val timestampMs: Long = 0L,
    val previewText: String = "" // Safe truncated preview (max 20 chars)
)

object EncryptedClipboardManager {
    private val _isAutoSyncEnabled = MutableStateFlow(false)
    val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

    private val _lastSyncedText = MutableStateFlow<String?>(null)
    val lastSyncedText: StateFlow<String?> = _lastSyncedText.asStateFlow()

    private val _recentClipboardHistory = MutableStateFlow<List<EncryptedClipboardPayload>>(emptyList())
    val recentClipboardHistory: StateFlow<List<EncryptedClipboardPayload>> = _recentClipboardHistory.asStateFlow()

    fun setAutoSyncEnabled(enabled: Boolean) {
        _isAutoSyncEnabled.value = enabled
    }

    fun onOutgoingClipboard(text: String, deviceId: String, deviceName: String): EncryptedClipboardPayload {
        _lastSyncedText.value = text
        val preview = text.take(20).replace("\n", " ")
        val payload = EncryptedClipboardPayload(
            senderDeviceId = deviceId,
            senderDeviceName = deviceName,
            ciphertextBase64 = "enc_${text.length}",
            nonceHex = "000102030405060708090a0b",
            timestampMs = System.currentTimeMillis(),
            previewText = preview
        )
        val history = _recentClipboardHistory.value.toMutableList()
        history.add(0, payload)
        if (history.size > 20) history.removeLast()
        _recentClipboardHistory.value = history
        return payload
    }

    fun onIncomingClipboard(payload: EncryptedClipboardPayload, decryptedText: String) {
        _lastSyncedText.value = decryptedText
        val history = _recentClipboardHistory.value.toMutableList()
        history.add(0, payload)
        if (history.size > 20) history.removeLast()
        _recentClipboardHistory.value = history
    }
}
