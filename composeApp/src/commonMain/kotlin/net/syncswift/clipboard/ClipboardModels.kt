package net.syncswift.clipboard

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
    data class PhoneAction(val number: String, val formatted: String) : SmartAction()
    data class ColorAction(val colorInfo: ColorInfo) : SmartAction()
    data class CodeAction(val previewText: String, val lineCount: Int) : SmartAction()
}

data class ClipboardItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isFavorite: Boolean = false,
    val isEphemeral: Boolean = false,
    val ttlSeconds: Long = 300L,
    val type: ClipType = ClipType.TEXT,
    val smartAction: SmartAction? = null,
    val label: String? = null
)
