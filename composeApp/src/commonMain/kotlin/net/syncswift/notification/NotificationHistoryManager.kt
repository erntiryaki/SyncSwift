package net.syncswift.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager
import net.syncswift.currentTimeMs

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val type: String, // "clipboard", "file", "pair", "info"
    val senderName: String,
    val timestampMs: Long,
    val isRead: Boolean = false
)

object NotificationHistoryManager {
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    init {
        loadSavedNotifications()
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

    private fun loadSavedNotifications() {
        val json = SessionManager.getSavedNotificationsJson() ?: return
        try {
            val regex = Regex("""\{"id":"([^"]*)","title":"([^"]*)","body":"([^"]*)","type":"([^"]*)","senderName":"([^"]*)","timestampMs":(\d+),"isRead":(true|false)\}""")
            val list = regex.findAll(json).map { match ->
                val (id, title, body, type, senderName, timestampStr, isReadStr) = match.destructured
                NotificationItem(
                    id = id,
                    title = title.replace("\\n", "\n").replace("\\\"", "\""),
                    body = body.replace("\\n", "\n").replace("\\\"", "\""),
                    type = type,
                    senderName = senderName,
                    timestampMs = timestampStr.toLongOrNull() ?: currentTimeMs(),
                    isRead = isReadStr == "true"
                )
            }.toList()
            _notifications.value = list
        } catch (_: Exception) {}
    }

    private fun saveNotifications() {
        try {
            val list = _notifications.value
            val sb = StringBuilder("[")
            list.forEachIndexed { idx, item ->
                sb.append("""{"id":"${escapeJson(item.id)}","title":"${escapeJson(item.title)}","body":"${escapeJson(item.body)}","type":"${escapeJson(item.type)}","senderName":"${escapeJson(item.senderName)}","timestampMs":${item.timestampMs},"isRead":${item.isRead}}""")
                if (idx < list.size - 1) sb.append(",")
            }
            sb.append("]")
            SessionManager.saveNotificationsJson(sb.toString())
        } catch (_: Exception) {}
    }

    fun addNotification(
        title: String,
        body: String,
        type: String = "info",
        senderName: String = ""
    ) {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanTitle.isBlank() && cleanBody.isBlank()) return

        val id = "${currentTimeMs()}-${(0..9999).random()}"
        val item = NotificationItem(
            id = id,
            title = if (cleanTitle.isNotBlank()) cleanTitle else "SyncSwift",
            body = cleanBody,
            type = type,
            senderName = senderName,
            timestampMs = currentTimeMs(),
            isRead = false
        )
        val current = _notifications.value.toMutableList()
        current.add(0, item)
        // Keep last 100 notifications maximum
        val trimmed = if (current.size > 100) current.take(100) else current
        _notifications.value = trimmed
        saveNotifications()
    }

    fun deleteNotification(id: String) {
        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == id }
        _notifications.value = current
        saveNotifications()
    }

    fun clearAll() {
        _notifications.value = emptyList()
        saveNotifications()
    }

    fun markAllRead() {
        val current = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = current
        saveNotifications()
    }

    fun markAsRead(id: String) {
        val current = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _notifications.value = current
        saveNotifications()
    }
}
