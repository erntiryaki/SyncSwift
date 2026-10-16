package net.syncswift

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Ios
actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
actual fun getSystemLanguageCode(): String = platform.Foundation.NSLocale.currentLocale.languageCode ?: "en"
actual fun showNativeToast(message: String) {
    println("[SyncSwift Native Toast] $message")
}

@androidx.compose.runtime.Composable
actual fun UpdateSystemBars(isDark: Boolean) {
    // Handled by iOS UIViewController preferredStatusBarStyle
}

