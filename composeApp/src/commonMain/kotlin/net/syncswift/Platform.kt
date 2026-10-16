package net.syncswift

enum class PlatformFlavor { Android, Ios }
expect fun currentPlatform(): PlatformFlavor
expect fun currentTimeMs(): Long
expect fun getSystemLanguageCode(): String
expect fun showNativeToast(message: String)
@androidx.compose.runtime.Composable
expect fun UpdateSystemBars(isDark: Boolean)
expect fun openFile(filePathOrUri: String?, fileName: String, mimeType: String)
expect fun showFileLocation(filePathOrUri: String?, fileName: String)
expect fun formatEpochToDate(epochMillis: Long): String

