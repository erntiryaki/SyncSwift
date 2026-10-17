package net.syncswift

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.preferredLanguages

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Ios
actual fun currentTimeMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
actual fun getSystemLanguageCode(): String {
    return try {
        val langs = platform.Foundation.NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>
        val preferred = (langs?.firstOrNull() as? String)
            ?: (platform.Foundation.NSLocale.preferredLanguages.firstOrNull() as? String)
            ?: "en"
        preferred.split("-", "_").first().lowercase()
    } catch (e: Throwable) {
        "en"
    }
}
actual fun showNativeToast(message: String) {
    ToastNotifier.show(message)
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

fun requestIosPermissions() {
    try {
        val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
        val options = platform.UserNotifications.UNAuthorizationOptionAlert or
                platform.UserNotifications.UNAuthorizationOptionBadge or
                platform.UserNotifications.UNAuthorizationOptionSound
        center.requestAuthorizationWithOptions(options) { _, _ -> }
    } catch (_: Throwable) {}
}

fun onIosPushTokenReceived(token: String) {
    if (token.isBlank()) return
    println("[SyncSwift iOS] onIosPushTokenReceived: $token")
    platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(token, forKey = "syncswift_push_token")
    net.syncswift.connection.ConnectionManager.syncDeviceWithSupabase(token)
}

fun getSavedIosPushToken(): String? {
    return platform.Foundation.NSUserDefaults.standardUserDefaults.stringForKey("syncswift_push_token")
}

fun onIosRemoteNotificationReceived(userInfo: Map<Any?, *>) {
    val type = userInfo["type"] as? String ?: return
    val copy = currentAppCopy()
    println("[SyncSwift iOS] Remote notification received: type=$type")
    when (type) {
        "pair_cancelled" -> {
            net.syncswift.connection.ConnectionManager.cancelIncomingPairRequest()
            showNativeToast(copy.requestCancelledBySender)
        }
        "file_cancelled" -> {
            val transferId = userInfo["transferId"] as? String
            net.syncswift.connection.ConnectionManager.cancelIncomingTransferRequest(transferId)
            showNativeToast(copy.transferCancelledBySender)
        }
        "pair_request" -> {
            val senderId = userInfo["senderId"] as? String ?: ""
            val senderName = userInfo["senderName"] as? String ?: copy.deviceSingle
            val senderHost = userInfo["senderHost"] as? String ?: ""
            val senderPort = (userInfo["senderPort"] as? String)?.toIntOrNull() ?: 52424
            val senderOs = userInfo["senderOs"] as? String ?: "iOS"

            val request = net.syncswift.connection.IncomingPairRequest(
                senderId = senderId,
                senderName = senderName,
                senderHost = senderHost,
                senderPort = senderPort,
                senderOsType = senderOs
            )
            net.syncswift.connection.ConnectionManager.dispatchIncomingPairRequest(request)
        }
        "file_request" -> {
            val transferId = userInfo["transferId"] as? String ?: ""
            val senderName = userInfo["senderName"] as? String ?: copy.deviceSingle
            val senderHost = userInfo["senderHost"] as? String ?: ""
            val senderPort = (userInfo["senderPort"] as? String)?.toIntOrNull() ?: 52424
            val fileName = userInfo["fileName"] as? String ?: "File"
            val fileSize = (userInfo["fileSize"] as? String)?.toLongOrNull() ?: 0L
            val mimeType = userInfo["mimeType"] as? String ?: "application/octet-stream"
            val downloadUrl = userInfo["downloadUrl"] as? String ?: ""
            val isRemote = userInfo["isRemote"] == "true"

            val request = net.syncswift.connection.IncomingTransferRequest(
                senderName = senderName,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                senderHost = senderHost,
                senderPort = senderPort,
                transferId = transferId,
                downloadUrl = downloadUrl,
                isRemote = isRemote
            )
            net.syncswift.connection.ConnectionManager.dispatchIncomingTransferRequest(request)
        }
    }
}
