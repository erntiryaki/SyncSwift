package net.syncswift.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager
import net.syncswift.currentTimeMs
import net.syncswift.subscription.SubscriptionManager

object ClipboardHistoryManager {
    private val _clips = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clips: StateFlow<List<ClipboardItem>> = _clips.asStateFlow()

    private var lastHandledTextHash: Int = 0

    init {
        loadSavedClips()
        purgeExpiredAndNonProClips()
    }

    /**
     * Infinite loop (echo) prevention. Returns true if this text was just handled.
     */
    fun isEcho(text: String): Boolean {
        return text.hashCode() == lastHandledTextHash
    }

    fun markHandled(text: String) {
        lastHandledTextHash = text.hashCode()
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 32) {
                        val hex = ch.code.toString(16).padStart(4, '0')
                        sb.append("\\u$hex")
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun loadSavedClips() {
        val json = SessionManager.getSavedClipboardHistoryJson() ?: return
        try {
            val regex = Regex("""\{"id":"([^"]*)","text":"([^"]*)","timestamp":(\d+),"isFav":(true|false),"isEph":(true|false)(?:,"label":"([^"]*)")?\}""")
            val list = regex.findAll(json).map { match ->
                val id = match.groupValues[1]
                val text = match.groupValues[2].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                val timestamp = match.groupValues[3].toLongOrNull() ?: currentTimeMs()
                val isFav = match.groupValues[4] == "true"
                val isEph = match.groupValues[5] == "true"
                val label = match.groupValues.getOrNull(6)?.takeIf { it.isNotBlank() }

                val (detectedType, action) = ContentDetector.detect(text)
                ClipboardItem(
                    id = id,
                    text = text,
                    timestamp = timestamp,
                    isFavorite = isFav,
                    isEphemeral = isEph,
                    type = detectedType,
                    smartAction = action,
                    label = label
                )
            }.toList()
            _clips.value = list
        } catch (_: Exception) {}
    }

    private fun saveClips() {
        try {
            val list = _clips.value
            val sb = StringBuilder("[")
            list.forEachIndexed { idx, item ->
                sb.append("""{"id":"${escapeJson(item.id)}","text":"${escapeJson(item.text)}","timestamp":${item.timestamp},"isFav":${item.isFavorite},"isEph":${item.isEphemeral},"label":"${escapeJson(item.label ?: "")}"}""")
                if (idx < list.size - 1) sb.append(",")
            }
            sb.append("]")
            SessionManager.saveClipboardHistoryJson(sb.toString())
        } catch (_: Exception) {}
    }

    fun purgeExpiredAndNonProClips() {
        val now = currentTimeMs()
        val isPro = SubscriptionManager.isPro.value
        val filtered = _clips.value.filter { item ->
            // Keep favorites always
            if (item.isFavorite) return@filter true

            // Ephemeral expiry check
            if (item.isEphemeral && (now - item.timestamp > item.ttlSeconds * 1000L)) {
                return@filter false
            }

            // Free plan: 24h retention limit
            if (!isPro && (now - item.timestamp > 86_400_000L)) {
                return@filter false
            }

            true
        }
        if (filtered.size != _clips.value.size) {
            _clips.value = filtered
            saveClips()
        }
    }

    fun addClip(
        text: String,
        isFavorite: Boolean = false,
        label: String? = null,
        isEphemeral: Boolean = false,
        ttlSeconds: Long = 300L
    ): ClipboardItem? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        markHandled(clean)

        // Don't add duplicate if latest clip has identical text
        val currentList = _clips.value
        val existingIndex = currentList.indexOfFirst { it.text == clean }
        val id = if (existingIndex >= 0) currentList[existingIndex].id else "${currentTimeMs()}-${(1000..9999).random()}"
        val (detectedType, action) = ContentDetector.detect(clean)

        val item = ClipboardItem(
            id = id,
            text = clean,
            timestamp = currentTimeMs(),
            isFavorite = if (existingIndex >= 0) (currentList[existingIndex].isFavorite || isFavorite) else isFavorite,
            isEphemeral = isEphemeral,
            ttlSeconds = ttlSeconds,
            type = detectedType,
            smartAction = action,
            label = label ?: (if (existingIndex >= 0) currentList[existingIndex].label else null)
        )

        val updated = mutableListOf<ClipboardItem>()
        updated.add(item)
        currentList.forEach {
            if (it.text != clean) updated.add(it)
        }

        // Bound to 200 items max
        val finalList = updated.take(200)
        _clips.value = finalList
        saveClips()
        purgeExpiredAndNonProClips()
        return item
    }

    fun toggleFavorite(id: String) {
        _clips.value = _clips.value.map {
            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveClips()
    }

    fun deleteClip(id: String) {
        _clips.value = _clips.value.filter { it.id != id }
        saveClips()
    }

    fun clearHistory(keepFavorites: Boolean = true) {
        _clips.value = if (keepFavorites) {
            _clips.value.filter { it.isFavorite }
        } else {
            emptyList()
        }
        saveClips()
    }
}
