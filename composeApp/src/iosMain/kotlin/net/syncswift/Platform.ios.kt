package net.syncswift

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Ios
actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
actual fun getSystemLanguageCode(): String {
    return try {
        val preferred = (platform.Foundation.NSLocale.preferredLanguages().firstOrNull() as? String) ?: "en"
        preferred.split("-", "_").first().lowercase()
    } catch (e: Throwable) {
        "en"
    }
}
actual fun showNativeToast(message: String) {
    println("[SyncSwift Native Toast] $message")
}

@androidx.compose.runtime.Composable
actual fun UpdateSystemBars(isDark: Boolean) {
    // Handled by iOS UIViewController preferredStatusBarStyle
}

actual fun openFile(filePathOrUri: String?, fileName: String, mimeType: String) {
    println("[SyncSwift iOS] Opening file $fileName at $filePathOrUri")
    val path = filePathOrUri ?: return
    val url = platform.Foundation.NSURL.fileURLWithPath(path)
    platform.UIKit.UIApplication.sharedApplication.openURL(url)
}

actual fun showFileLocation(filePathOrUri: String?, fileName: String) {
    println("[SyncSwift iOS] Showing file location for $fileName at $filePathOrUri")
    val filesAppUrl = platform.Foundation.NSURL.URLWithString("shareddocuments://")
    if (filesAppUrl != null && platform.UIKit.UIApplication.sharedApplication.canOpenURL(filesAppUrl)) {
        platform.UIKit.UIApplication.sharedApplication.openURL(filesAppUrl)
    }
}

actual fun formatEpochToDate(epochMillis: Long): String {
    val date = platform.Foundation.NSDate(timeIntervalSinceReferenceDate = (epochMillis / 1000.0) - 978307200.0)
    val formatter = platform.Foundation.NSDateFormatter().apply {
        dateFormat = "dd.MM.yyyy"
    }
    return formatter.stringFromDate(date)
}
