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

fun onIosAppDidEnterBackground() {
    println("[SyncSwift iOS] App entered background - safely stopping discovery")
    try {
        net.syncswift.discovery.DeviceDiscoveryManager.stopDiscovery()
    } catch (_: Throwable) {}
}

fun onIosAppWillEnterForeground() {
    println("[SyncSwift iOS] App will enter foreground - resuming discovery")
    try {
        net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
    } catch (_: Throwable) {}
}

private fun findRawInMap(map: Map<*, *>, targetKey: String): Any? {
    for ((k, v) in map) {
        if (k?.toString().equals(targetKey, ignoreCase = true)) {
            return v
        }
    }
    return null
}

private fun findInMap(map: Map<*, *>, targetKey: String): String? {
    val raw = findRawInMap(map, targetKey) ?: return null
    return raw.toString()
}

fun extractNotificationField(userInfo: Map<Any?, *>, key: String): String? {
    findInMap(userInfo, key)?.let { return it }

    val dataObj = findRawInMap(userInfo, "data")
    if (dataObj is Map<*, *>) {
        findInMap(dataObj, key)?.let { return it }
    } else if (dataObj is String) {
        val match = Regex(""""$key"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(dataObj)
        if (match != null) return match.groupValues[1]
    }

    val apsObj = findRawInMap(userInfo, "aps")
    if (apsObj is Map<*, *>) {
        findInMap(apsObj, key)?.let { return it }
    }

    val alertObj = findRawInMap(userInfo, "alert")
    if (alertObj is Map<*, *>) {
        findInMap(alertObj, key)?.let { return it }
    }

    return null
}

fun onIosRemoteNotificationReceived(userInfo: Map<Any?, *>) {
    val type = extractNotificationField(userInfo, "type")
        ?: extractNotificationField(userInfo, "requestType")
        ?: return
    val copy = currentAppCopy()
    println("[SyncSwift iOS] Remote notification received: type=$type, raw=$userInfo")
    when (type.lowercase()) {
        "pair_cancelled" -> {
            net.syncswift.connection.ConnectionManager.cancelIncomingPairRequest()
            showNativeToast(copy.requestCancelledBySender)
        }
        "pair_accepted" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            if (senderHost.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected("$senderHost:$senderPort", isRemote = true)
            }
            if (senderId.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(senderId, isRemote = true)
            }
            net.syncswift.connection.OutgoingStatusManager.completeSuccess(copy.notificationPairAcceptedStatus.replace("%s", senderName))
            net.syncswift.notification.NotificationHistoryManager.addNotification(
                title = copy.connectionAccepted,
                body = copy.notificationPairAcceptedStatus.replace("%s", senderName),
                type = "pair",
                senderName = senderName
            )
        }
        "pair_rejected" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            if (senderHost.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
            }
            if (senderId.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceDisconnected(senderId)
            }
            net.syncswift.connection.OutgoingStatusManager.fail(copy.notificationPairRejectedStatus.replace("%s", senderName))
            net.syncswift.notification.NotificationHistoryManager.addNotification(
                title = copy.connectionRejected,
                body = copy.notificationPairRejectedStatus.replace("%s", senderName),
                type = "pair",
                senderName = senderName
            )
        }
        "file_cancelled" -> {
            val transferId = extractNotificationField(userInfo, "transferId")
            net.syncswift.connection.ConnectionManager.cancelIncomingTransferRequest(transferId)
            showNativeToast(copy.transferCancelledBySender)
        }
        "clipboard_sync" -> {
            val clipText = extractNotificationField(userInfo, "text")
                ?: extractNotificationField(userInfo, "content")
                ?: extractNotificationField(userInfo, "body") ?: ""
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            if (clipText.isNotBlank()) {
                net.syncswift.transfer.TransferEngine.applyIncomingClipboard(clipText)
                net.syncswift.notification.NotificationHistoryManager.addNotification(
                    title = copy.syncClipboardAction,
                    body = copy.clipboardSynced.replace("%s", clipText.take(50)),
                    type = "clipboard",
                    senderName = senderName
                )
            }
        }
        "pair_request" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderOs = extractNotificationField(userInfo, "senderOs") ?: "iOS"

            val request = net.syncswift.connection.IncomingPairRequest(
                senderId = senderId,
                senderName = senderName,
                senderHost = senderHost,
                senderPort = senderPort,
                senderOsType = senderOs
            )
            net.syncswift.connection.ConnectionManager.dispatchIncomingPairRequest(request)
            net.syncswift.notification.NotificationHistoryManager.addNotification(
                title = copy.notificationPairRequestTitle,
                body = copy.notificationPairRequestBody.replace("%s", senderName),
                type = "pair",
                senderName = senderName
            )
        }
        "file_request", "file_transfer" -> {
            val transferId = extractNotificationField(userInfo, "transferId") ?: ""
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val fileName = extractNotificationField(userInfo, "fileName") ?: "File"
            val fileSize = extractNotificationField(userInfo, "fileSize")?.toLongOrNull() ?: 0L
            val mimeType = extractNotificationField(userInfo, "mimeType") ?: "application/octet-stream"
            val downloadUrl = extractNotificationField(userInfo, "downloadUrl") ?: ""
            val isRemote = extractNotificationField(userInfo, "isRemote")?.equals("true", ignoreCase = true) == true || downloadUrl.isNotBlank()

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
            val sizeStr = if (fileSize > 0) " (${net.syncswift.transfer.formatBytes(fileSize)})" else ""
            net.syncswift.notification.NotificationHistoryManager.addNotification(
                title = copy.notificationFileRequestTitle,
                body = copy.notificationFileRequestBody
                    .replace("%s", senderName)
                    .replace("%s", fileName)
                    .replace("(%s)", sizeStr),
                type = "file",
                senderName = senderName
            )
        }
    }
}

