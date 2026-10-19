package net.syncswift

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlatformFlavor { Android, Ios }

object ToastNotifier {
    private val _currentToast = MutableStateFlow<String?>(null)
    val currentToast: StateFlow<String?> = _currentToast.asStateFlow()

    fun show(message: String) {
        // Remove emojis and excess punctuation for minimalist aesthetic
        val clean = message.replace(Regex("""[\p{So}\p{Cn}\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]"""), "").trim()
        if (clean.isNotBlank()) {
            _currentToast.value = clean
        }
    }

    fun dismiss() {
        _currentToast.value = null
    }
}

expect fun currentPlatform(): PlatformFlavor
expect fun currentTimeMs(): Long
expect fun getSystemLanguageCode(): String
expect fun getSystemCountryCode(): String
expect fun showNativeToast(message: String)
@androidx.compose.runtime.Composable
expect fun UpdateSystemBars(isDark: Boolean, statusBarColor: androidx.compose.ui.graphics.Color? = null)
expect fun setSystemBarsDirect(isDark: Boolean, statusBarColor: androidx.compose.ui.graphics.Color? = null)
expect fun openFile(filePathOrUri: String?, fileName: String, mimeType: String)
expect fun showFileLocation(filePathOrUri: String?, fileName: String)
expect fun formatEpochToDate(epochMillis: Long): String
expect fun getCurrentPaywallSlotKey(): String
