package net.syncswift.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.currentTimeMs

enum class ClipType {
    TEXT,
    URL,
    PHONE,
    COLOR,
    CODE
}

data class ColorInfo(
    val hex: String,
    val r: Int,
    val g: Int,
    val b: Int
)

sealed class SmartAction {
    data class OpenUrl(val url: String) : SmartAction()
    data class PhoneAction(val number: String, val formatted: String = "") : SmartAction()
    data class ColorAction(val colorInfo: ColorInfo) : SmartAction()
    data class CodeAction(val previewText: String, val lineCount: Int) : SmartAction()
}

data class ClipboardItem(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = currentTimeMs(),
    val isFavorite: Boolean = false,
    val isEphemeral: Boolean = false,
    val ttlSeconds: Long = 300L,
    val type: ClipType = ClipType.TEXT,
    val smartAction: SmartAction? = null,
    val label: String? = null
)

/**
 * ClipboardHistoryManager Bridge.
 * Bridges clipboard additions and history to :shared EncryptedClipboardManager.
 */
object ClipboardHistoryManager {
    private val _history = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val history: StateFlow<List<ClipboardItem>> = _history.asStateFlow()
    val clips: StateFlow<List<ClipboardItem>> = _history.asStateFlow()

    fun clearHistory(keepFavorites: Boolean = false) {
        _history.value = if (keepFavorites) _history.value.filter { it.isFavorite } else emptyList()
    }

    fun toggleFavorite(id: String) {
        _history.value = _history.value.map {
            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun deleteClip(id: String) {
        _history.value = _history.value.filter { it.id != id }
    }

    fun addClip(text: String, label: String? = null, isFavorite: Boolean = false) {
        if (text.isBlank()) return
        val item = ClipboardItem(id = "${currentTimeMs()}", text = text, label = label, isFavorite = isFavorite)
        _history.value = listOf(item) + _history.value
        EncryptedClipboardManager.onOutgoingClipboard(
            text = text,
            deviceId = "self",
            deviceName = "This Device"
        )
    }
}
