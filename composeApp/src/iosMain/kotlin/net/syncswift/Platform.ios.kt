package net.syncswift

import kotlinx.coroutines.launch
import net.syncswift.discovery.cleanDeviceDisplayName
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

actual fun getCurrentPaywallSlotKey(): String {
    val cal = platform.Foundation.NSCalendar.currentCalendar
    val date = platform.Foundation.NSDate()
    val comps = cal.components(
        platform.Foundation.NSCalendarUnitYear or
        platform.Foundation.NSCalendarUnitMonth or
        platform.Foundation.NSCalendarUnitDay or
        platform.Foundation.NSCalendarUnitHour,
        fromDate = date
    )
    val year = comps.year
    val month = comps.month
    val day = comps.day
    val hour = comps.hour.toInt()
    val slot = hour / 3
    return "$year-$month-$day-$slot"
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
    println("[SyncSwift iOS] App entered background - safely stopping discovery & server")
    try {
        net.syncswift.discovery.DeviceDiscoveryManager.stopDiscovery()
        net.syncswift.transfer.TransferEngine.stopServer()
    } catch (_: Throwable) {}
}

fun onIosAppWillEnterForeground() {
    println("[SyncSwift iOS] App will enter foreground - resuming discovery & server")
    try {
        net.syncswift.transfer.TransferEngine.startForegroundServer()
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
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val key = "$senderHost:$senderPort"

            net.syncswift.connection.ConnectionManager.markRequestCancelled(key)
            if (senderId.isNotBlank()) net.syncswift.connection.ConnectionManager.markRequestCancelled(senderId)
            if (senderHost.isNotBlank()) net.syncswift.connection.ConnectionManager.markRequestCancelled(senderHost)

            net.syncswift.connection.ConnectionManager.markDeviceDisconnected(key)
            if (senderId.isNotBlank()) net.syncswift.connection.ConnectionManager.markDeviceDisconnected(senderId)
            if (senderHost.isNotBlank()) net.syncswift.connection.ConnectionManager.markDeviceDisconnected(senderHost)

            net.syncswift.connection.ConnectionManager.cancelIncomingPairRequest()
            showNativeToast(copy.requestCancelledBySender)
        }
        "pair_accepted" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderPushToken = extractNotificationField(userInfo, "senderPushToken") ?: ""
            val key = "$senderHost:$senderPort"
            if (net.syncswift.connection.ConnectionManager.isRequestCancelled(key) ||
                net.syncswift.connection.ConnectionManager.isRequestCancelled(senderId) ||
                net.syncswift.connection.ConnectionManager.isRequestCancelled(senderHost)) {
                println("[SyncSwift iOS] Ignoring pair_accepted for cancelled request from $senderName, echoing pair_cancelled")
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    val token = if (senderPushToken.isNotBlank()) senderPushToken else net.syncswift.connection.ConnectionManager.queryPushTokenFromSupabase(senderHost, senderName)
                    if (!token.isNullOrBlank()) {
                        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                        val selfName = cleanDeviceDisplayName(self?.name ?: platform.UIKit.UIDevice.currentDevice.name)
                        val selfHost = self?.host ?: ""
                        val selfPort = self?.port ?: 52424
                        val selfId = self?.id ?: platform.UIKit.UIDevice.currentDevice.identifierForVendor?.UUIDString ?: ""
                        net.syncswift.connection.ConnectionManager.sendFcmNotification(
                            token = token,
                            title = "",
                            body = "",
                            data = mapOf(
                                "type" to "pair_cancelled",
                                "senderName" to selfName,
                                "senderHost" to selfHost,
                                "senderPort" to "$selfPort",
                                "senderId" to selfId
                            )
                        )
                    }
                }
                return
            }
            if (senderHost.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(key, isRemote = true)
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
        "clipboard_cancelled" -> {
            val transferId = extractNotificationField(userInfo, "transferId")
            net.syncswift.connection.ConnectionManager.cancelIncomingTransferRequest(transferId)
            showNativeToast(copy.clipboardCancelledBySender)
        }
        "clipboard_accepted" -> {
            val preview = extractNotificationField(userInfo, "preview") ?: ""
            net.syncswift.connection.OutgoingStatusManager.completeSuccess(copy.clipboardSentSuccess.replace("%s", preview))
        }
        "clipboard_rejected" -> {
            net.syncswift.connection.OutgoingStatusManager.fail(copy.operationRejectedOrTimeout)
        }
        "pair_accepted" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderPushToken = extractNotificationField(userInfo, "senderPushToken") ?: ""
            val key = "$senderHost:$senderPort"

            val matchedDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
                net.syncswift.discovery.cleanDeviceDisplayName(it.name) == net.syncswift.discovery.cleanDeviceDisplayName(senderName) ||
                (it.host.isNotBlank() && it.host == senderHost) ||
                it.id == senderId
            }
            val isRemote = when {
                matchedDev != null -> matchedDev.isRemote
                senderHost.startsWith("remote-") -> true
                senderHost.isBlank() || !senderHost.contains(".") -> true
                else -> false
            }
            if (senderHost.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(key, isRemote = isRemote)
                net.syncswift.connection.ConnectionManager.markDeviceConnected(senderHost, isRemote = isRemote)
            }
            if (senderId.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(senderId, isRemote = isRemote)
            }
            if (senderName.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(senderName, isRemote = isRemote)
                net.syncswift.connection.ConnectionManager.markDeviceConnected(net.syncswift.discovery.cleanDeviceDisplayName(senderName), isRemote = isRemote)
            }
            if (matchedDev != null) {
                net.syncswift.connection.ConnectionManager.markDeviceConnected(matchedDev.id, isRemote = isRemote)
                net.syncswift.connection.ConnectionManager.markDeviceConnected(matchedDev.host, isRemote = isRemote)
                val devKey = "${matchedDev.host}:${matchedDev.port}"
                net.syncswift.connection.ConnectionManager.markDeviceConnected(devKey, isRemote = isRemote)
                net.syncswift.discovery.DeviceDiscoveryManager.saveManualDevices()
            } else if (senderHost.isNotBlank() || senderPushToken.isNotBlank()) {
                net.syncswift.discovery.DeviceDiscoveryManager.addManualDevice(
                    host = senderHost.ifBlank { "remote-${net.syncswift.discovery.cleanDeviceDisplayName(senderName)}" },
                    port = senderPort,
                    name = senderName,
                    osType = extractNotificationField(userInfo, "senderOs") ?: "iOS"
                )
            }
            net.syncswift.connection.OutgoingStatusManager.completeSuccess(copy.notificationPairAcceptedStatus.replace("%s", senderName))
        }
        "pair_rejected" -> {
            val senderId = extractNotificationField(userInfo, "senderId") ?: ""
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            if (senderHost.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
            }
            if (senderId.isNotBlank()) {
                net.syncswift.connection.ConnectionManager.markDeviceDisconnected(senderId)
            }
            net.syncswift.connection.OutgoingStatusManager.fail(copy.notificationPairRejectedStatus.replace("%s", senderName))
        }
        "clipboard_request", "clipboard_sync" -> {
            val clipText = extractNotificationField(userInfo, "text")
                ?: extractNotificationField(userInfo, "content")
                ?: extractNotificationField(userInfo, "body") ?: ""
            val transferId = extractNotificationField(userInfo, "transferId") ?: "clip-${currentTimeMs()}"
            val senderName = extractNotificationField(userInfo, "senderName") ?: copy.deviceSingle
            val senderHost = extractNotificationField(userInfo, "senderHost") ?: ""
            val senderPort = extractNotificationField(userInfo, "senderPort")?.toIntOrNull() ?: 52424
            val senderLanguage = extractNotificationField(userInfo, "senderLanguage") ?: "en"
            val timestamp = extractNotificationField(userInfo, "timestamp")?.toLongOrNull() ?: 0L
            val isExpired = timestamp > 0L && (currentTimeMs() - timestamp > 180_000L)

            if (isExpired || net.syncswift.connection.ConnectionManager.isRequestCancelled(transferId)) {
                showNativeToast(copy.clipboardCancelledBySender)
                return
            }

            if (clipText.isNotBlank()) {
                val preview = clipText.take(35).replace("\n", " ")
                val request = net.syncswift.connection.IncomingTransferRequest(
                    senderName = senderName,
                    fileName = "${copy.clipboard}: $preview",
                    fileSize = clipText.encodeToByteArray().size.toLong(),
                    mimeType = "text/plain",
                    senderHost = senderHost,
                    senderPort = senderPort,
                    transferId = transferId,
                    downloadUrl = clipText,
                    isRemote = true,
                    senderLanguage = senderLanguage
                )
                net.syncswift.connection.ConnectionManager.dispatchIncomingTransferRequest(request)
                net.syncswift.notification.NotificationHistoryManager.addNotification(
                    title = copy.syncClipboardAction,
                    body = copy.clipboardSynced.replace("%s", preview),
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
            val senderLanguage = extractNotificationField(userInfo, "senderLanguage") ?: "en"
            val senderPushToken = extractNotificationField(userInfo, "senderPushToken") ?: ""
            val timestamp = extractNotificationField(userInfo, "timestamp")?.toLongOrNull() ?: 0L
            val key = "$senderHost:$senderPort"
            val isExpired = timestamp > 0L && (currentTimeMs() - timestamp > 120_000L)

            if (isExpired || net.syncswift.connection.ConnectionManager.isRequestCancelled(key) ||
                net.syncswift.connection.ConnectionManager.isRequestCancelled(senderId) ||
                net.syncswift.connection.ConnectionManager.isRequestCancelled(senderHost)) {
                showNativeToast(copy.requestCancelledBySender)
                return
            }

            val request = net.syncswift.connection.IncomingPairRequest(
                senderId = senderId,
                senderName = senderName,
                senderHost = senderHost,
                senderPort = senderPort,
                senderOsType = senderOs,
                senderLanguage = senderLanguage,
                senderPushToken = senderPushToken
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
            val senderLanguage = extractNotificationField(userInfo, "senderLanguage") ?: "en"
            val timestamp = extractNotificationField(userInfo, "timestamp")?.toLongOrNull() ?: 0L
            val isExpired = timestamp > 0L && (currentTimeMs() - timestamp > 180_000L)

            if (isExpired || net.syncswift.connection.ConnectionManager.isRequestCancelled(transferId)) {
                showNativeToast(copy.transferCancelledBySender)
                return
            }

            val request = net.syncswift.connection.IncomingTransferRequest(
                senderName = senderName,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                senderHost = senderHost,
                senderPort = senderPort,
                transferId = transferId,
                downloadUrl = downloadUrl,
                isRemote = isRemote,
                senderLanguage = senderLanguage
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

