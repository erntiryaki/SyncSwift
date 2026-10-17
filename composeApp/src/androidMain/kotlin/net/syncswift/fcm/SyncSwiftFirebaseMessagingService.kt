package net.syncswift.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import net.syncswift.MainActivity
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.connection.IncomingTransferRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SyncSwiftFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SyncSwiftFCM"
        private const val CHANNEL_ID = "syncswift_requests_channel"
        private const val CHANNEL_NAME = "SyncSwift Bildirimleri"
        private const val PREFS_NAME = "syncswift_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        const val NOTIFICATION_PAIR_ID = 2001
        const val NOTIFICATION_FILE_ID = 2002

        fun getSavedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }

        fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        saveToken(applicationContext, token)
        FcmSyncManager.syncTokenWithBackend(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        net.syncswift.SessionManager.init(applicationContext)
        val copy = net.syncswift.currentAppCopy()
        val data = remoteMessage.data
        val type = data["type"] ?: ""
        val senderName = data["senderName"]?.ifBlank { null } ?: copy.deviceSingle

        when (type) {
            "pair_cancelled" -> {
                ConnectionManager.cancelIncomingPairRequest()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_PAIR_ID)
                net.syncswift.showNativeToast(copy.requestCancelledBySender)
                return
            }
            "file_cancelled" -> {
                val transferId = data["transferId"] ?: ""
                ConnectionManager.cancelIncomingTransferRequest(transferId)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_FILE_ID)
                net.syncswift.showNativeToast(copy.transferCancelledBySender)
                return
            }
            "clipboard_cancelled" -> {
                val transferId = data["transferId"] ?: ""
                ConnectionManager.cancelIncomingTransferRequest(transferId)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_FILE_ID)
                net.syncswift.showNativeToast(copy.clipboardCancelledBySender)
                return
            }
            "clipboard_request", "clipboard_sync" -> {
                val clipText = data["text"] ?: data["content"] ?: data["body"] ?: remoteMessage.notification?.body ?: ""
                val transferId = data["transferId"] ?: "clip-${System.currentTimeMillis()}"
                val senderHost = data["senderHost"] ?: ""
                val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
                val senderLanguage = data["senderLanguage"] ?: "en"
                val preview = clipText.take(35).replace("\n", " ")

                val request = IncomingTransferRequest(
                    senderName = senderName,
                    fileName = "${copy.clipboard}: $preview",
                    fileSize = clipText.toByteArray().size.toLong(),
                    mimeType = "text/plain",
                    senderHost = senderHost,
                    senderPort = senderPort,
                    transferId = transferId,
                    downloadUrl = clipText,
                    isRemote = true,
                    senderLanguage = senderLanguage
                )
                ConnectionManager.dispatchIncomingTransferRequest(request)
            }
            "clipboard_accepted" -> {
                val preview = data["preview"] ?: ""
                net.syncswift.connection.OutgoingStatusManager.completeSuccess(copy.clipboardSentSuccess.replace("%s", preview))
            }
            "clipboard_rejected" -> {
                net.syncswift.connection.OutgoingStatusManager.fail(copy.operationRejectedOrTimeout)
            }
            "pair_request" -> {
                val senderId = data["senderId"] ?: ""
                val senderHost = data["senderHost"] ?: ""
                val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
                val senderOs = data["senderOs"] ?: "Android"
                val senderLanguage = data["senderLanguage"] ?: "en"
                val senderPushToken = data["senderPushToken"] ?: ""

                val request = IncomingPairRequest(
                    senderId = senderId,
                    senderName = senderName,
                    senderHost = senderHost,
                    senderPort = senderPort,
                    senderOsType = senderOs,
                    senderLanguage = senderLanguage,
                    senderPushToken = senderPushToken
                )
                ConnectionManager.dispatchIncomingPairRequest(request)
            }
            "file_request" -> {
                val transferId = data["transferId"] ?: ""
                val senderHost = data["senderHost"] ?: ""
                val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
                val fileName = data["fileName"] ?: "File"
                val fileSize = data["fileSize"]?.toLongOrNull() ?: 0L
                val mimeType = data["mimeType"] ?: "application/octet-stream"
                val downloadUrl = data["downloadUrl"] ?: ""
                val isRemote = data["isRemote"] == "true"
                val senderLanguage = data["senderLanguage"] ?: "en"

                val request = IncomingTransferRequest(
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
                ConnectionManager.dispatchIncomingTransferRequest(request)
            }
            "pair_accepted" -> {
                val senderId = data["senderId"] ?: ""
                val senderHost = data["senderHost"] ?: ""
                val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
                val key = "$senderHost:$senderPort"
                if (ConnectionManager.isRequestCancelled(key) ||
                    ConnectionManager.isRequestCancelled(senderId) ||
                    ConnectionManager.isRequestCancelled(senderHost)) {
                    Log.d(TAG, "Ignoring pair_accepted for cancelled request from $senderName")
                    return
                }
                if (senderHost.isNotBlank()) {
                    ConnectionManager.markDeviceConnected(key, isRemote = true)
                }
                if (senderId.isNotBlank()) {
                    ConnectionManager.markDeviceConnected(senderId, isRemote = true)
                }
                net.syncswift.connection.OutgoingStatusManager.completeSuccess(copy.notificationPairAcceptedStatus.replace("%s", senderName))
            }
            "pair_rejected" -> {
                val senderId = data["senderId"] ?: ""
                val senderHost = data["senderHost"] ?: ""
                val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
                if (senderHost.isNotBlank()) {
                    ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
                }
                if (senderId.isNotBlank()) {
                    ConnectionManager.markDeviceDisconnected(senderId)
                }
                net.syncswift.connection.OutgoingStatusManager.fail(copy.notificationPairRejectedStatus.replace("%s", senderName))
            }
            "file_rejected" -> {
                val transferId = data["transferId"]
                if (!transferId.isNullOrBlank()) {
                    net.syncswift.transfer.TransferEngine.updateTransferProgress(transferId, 0, net.syncswift.transfer.TransferStatus.FAILED)
                }
                net.syncswift.connection.OutgoingStatusManager.fail(copy.notificationFileRejectedStatus.replace("%s", senderName))
            }
        }

        // Generate title and message purely in receiver's own selected language
        val title: String
        val body: String
        val notificationId: Int

        when (type) {
            "pair_request" -> {
                title = copy.notificationPairRequestTitle
                body = copy.notificationPairRequestBody.replace("%s", senderName)
                notificationId = NOTIFICATION_PAIR_ID
            }
            "file_request" -> {
                title = copy.notificationFileRequestTitle
                val fileName = data["fileName"] ?: "File"
                val fileSize = data["fileSize"]?.toLongOrNull() ?: 0L
                val sizeStr = if (fileSize > 0) " (${net.syncswift.transfer.formatBytes(fileSize)})" else ""
                body = copy.notificationFileRequestBody
                    .replace("%s", senderName)
                    .replace("%s", fileName)
                    .replace("(%s)", sizeStr)
                notificationId = NOTIFICATION_FILE_ID
            }
            "pair_accepted" -> {
                title = copy.connectionAccepted
                body = copy.notificationPairAcceptedStatus.replace("%s", senderName)
                notificationId = (System.currentTimeMillis() % 100000).toInt()
            }
            "pair_rejected" -> {
                title = copy.connectionRejected
                body = copy.notificationPairRejectedStatus.replace("%s", senderName)
                notificationId = (System.currentTimeMillis() % 100000).toInt()
            }
            "file_rejected" -> {
                title = copy.transferRejectedByRecipient
                body = copy.notificationFileRejectedStatus.replace("%s", senderName)
                notificationId = (System.currentTimeMillis() % 100000).toInt()
            }
            "clipboard_request", "clipboard_sync" -> {
                title = copy.syncClipboardAction
                val clipText = data["text"] ?: data["content"] ?: data["body"] ?: remoteMessage.notification?.body ?: ""
                val preview = clipText.take(35).replace("\n", " ")
                body = "$senderName: $preview"
                notificationId = NOTIFICATION_FILE_ID
            }
            else -> {
                title = remoteMessage.notification?.title ?: data["title"] ?: copy.notificationPairRequestTitle
                body = remoteMessage.notification?.body ?: data["body"] ?: copy.notificationPairRequestBody.replace("%s", senderName)
                notificationId = (System.currentTimeMillis() % 100000).toInt()
            }
        }

        // Record to in-app Notification History
        net.syncswift.notification.NotificationHistoryManager.addNotification(
            title = title,
            body = body,
            type = type,
            senderName = senderName
        )

        // Show native Android system notification with localized actions
        showSystemNotification(title, body, data, notificationId)
    }

    private fun showSystemNotification(title: String, message: String, data: Map<String, String>, notificationId: Int) {
        val copy = net.syncswift.currentAppCopy()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                copy.notificationChannelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = copy.notificationChannelDesc
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val type = data["type"] ?: ""
        val senderName = data["senderName"] ?: "Cihaz"
        val senderHost = data["senderHost"] ?: ""
        val senderPort = data["senderPort"]?.toIntOrNull() ?: 52424
        val senderId = data["senderId"] ?: ""

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", type)
            putExtra("senderName", senderName)
            putExtra("senderHost", senderHost)
            putExtra("senderPort", senderPort)
            putExtra("senderId", senderId)
            putExtra("fileName", data["fileName"] ?: "")
            putExtra("fileSize", data["fileSize"] ?: "")
            putExtra("mimeType", data["mimeType"] ?: "")
            putExtra("timestamp", data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis())
            putExtra("senderLanguage", data["senderLanguage"] ?: "en")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (type == "pair_request") {
            // "Onayla" Action button
            val acceptIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_ACCEPT_PAIR
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_HOST, senderHost)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_PORT, senderPort)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_ID, senderId)
            }
            val acceptPending = PendingIntent.getBroadcast(
                this,
                notificationId + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "Reddet" Action button
            val rejectIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REJECT_PAIR
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val rejectPending = PendingIntent.getBroadcast(
                this,
                notificationId + 2,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, copy.notificationActionAccept, acceptPending)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, copy.notificationActionDecline, rejectPending)
        } else if (type == "file_request") {
            val transferId = data["transferId"] ?: ""
            val fileName = data["fileName"] ?: "File"
            val fileSize = data["fileSize"]?.toLongOrNull() ?: 0L
            val mimeType = data["mimeType"] ?: "application/octet-stream"

            // Accept Action button
            val acceptFileIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_ACCEPT_FILE
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(NotificationActionReceiver.EXTRA_TRANSFER_ID, transferId)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_HOST, senderHost)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_PORT, senderPort)
                putExtra(NotificationActionReceiver.EXTRA_FILE_NAME, fileName)
                putExtra(NotificationActionReceiver.EXTRA_FILE_SIZE, fileSize)
                putExtra(NotificationActionReceiver.EXTRA_MIME_TYPE, mimeType)
                putExtra(NotificationActionReceiver.EXTRA_DOWNLOAD_URL, data["downloadUrl"] ?: "")
                putExtra(NotificationActionReceiver.EXTRA_IS_REMOTE, data["isRemote"] == "true")
            }
            val acceptFilePending = PendingIntent.getBroadcast(
                this,
                notificationId + 3,
                acceptFileIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Decline Action button
            val rejectFileIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REJECT_FILE
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(NotificationActionReceiver.EXTRA_TRANSFER_ID, transferId)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_NAME, senderName)
                putExtra(NotificationActionReceiver.EXTRA_SENDER_HOST, senderHost)
            }
            val rejectFilePending = PendingIntent.getBroadcast(
                this,
                notificationId + 4,
                rejectFileIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.checkbox_on_background, copy.notificationActionAccept, acceptFilePending)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, copy.notificationActionDecline, rejectFilePending)
        }

        notificationManager.notify(notificationId, builder.build())
    }
}
