package net.syncswift

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExternalAction {
    data class SendFile(
        val fileName: String,
        val bytes: ByteArray,
        val mimeType: String
    ) : ExternalAction {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SendFile) return false
            return fileName == other.fileName && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }
        override fun hashCode(): Int {
            var result = fileName.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }

    data class SendClipboard(
        val text: String
    ) : ExternalAction

    object TriggerFilePick : ExternalAction
}

object ExternalActionManager {
    private val _pendingAction = MutableStateFlow<ExternalAction?>(null)
    val pendingAction: StateFlow<ExternalAction?> = _pendingAction.asStateFlow()

    fun setPendingAction(action: ExternalAction) {
        _pendingAction.value = action
    }

    fun consumePendingAction(): ExternalAction? {
        val current = _pendingAction.value
        _pendingAction.value = null
        return current
    }

    fun clear() {
        _pendingAction.value = null
    }
}
