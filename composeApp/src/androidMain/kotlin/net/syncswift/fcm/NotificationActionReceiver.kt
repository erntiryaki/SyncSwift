package net.syncswift.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import net.syncswift.connection.ConnectionManager
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACCEPT_PAIR = "net.syncswift.ACTION_ACCEPT_PAIR"
        const val ACTION_REJECT_PAIR = "net.syncswift.ACTION_REJECT_PAIR"
        const val ACTION_ACCEPT_FILE = "net.syncswift.ACTION_ACCEPT_FILE"
        const val ACTION_REJECT_FILE = "net.syncswift.ACTION_REJECT_FILE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_HOST = "sender_host"
        const val EXTRA_SENDER_PORT = "sender_port"
        const val EXTRA_SENDER_ID = "sender_id"
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_SIZE = "file_size"
        const val EXTRA_MIME_TYPE = "mime_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        net.syncswift.SessionManager.init(appCtx)
        net.syncswift.transfer.TransferEngine.init(appCtx)

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        val copy = net.syncswift.currentAppCopy()

        fun showToast(msg: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            }
        }

        when (intent.action) {
            ACTION_ACCEPT_PAIR -> {
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: copy.deviceSingle
                val senderHost = intent.getStringExtra(EXTRA_SENDER_HOST) ?: ""
                val senderPort = intent.getIntExtra(EXTRA_SENDER_PORT, 52424)
                val senderId = intent.getStringExtra(EXTRA_SENDER_ID) ?: ""

                if (senderHost.isNotBlank()) {
                    ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                }
                if (senderId.isNotBlank()) {
                    ConnectionManager.markDeviceConnected(senderId)
                }

                // Notify sender that their request was accepted
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val senderToken = ConnectionManager.queryPushTokenFromSupabase(senderHost, senderName)
                    if (!senderToken.isNullOrBlank()) {
                        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                        val selfName = net.syncswift.discovery.cleanDeviceDisplayName(self?.name ?: android.os.Build.MODEL)
                        val selfHost = self?.host ?: ""
                        val selfPort = self?.port ?: 52424
                        val selfId = java.util.UUID.nameUUIDFromBytes("${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.ID}".toByteArray()).toString()

                        ConnectionManager.sendFcmNotification(
                            token = senderToken,
                            title = copy.connectionAccepted,
                            body = copy.notificationPairAcceptedStatus.replace("%s", selfName),
                            data = mapOf(
                                "type" to "pair_accepted",
                                "senderName" to selfName,
                                "senderHost" to selfHost,
                                "senderPort" to "$selfPort",
                                "senderId" to selfId
                            )
                        )
                    }
                }

                showToast(copy.operationCompletedDesc.replace("%s", senderName))
            }
            ACTION_REJECT_PAIR -> {
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: copy.deviceSingle
                val senderHost = intent.getStringExtra(EXTRA_SENDER_HOST) ?: ""
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val senderToken = ConnectionManager.queryPushTokenFromSupabase(senderHost, senderName)
                    if (!senderToken.isNullOrBlank()) {
                        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                        val selfName = net.syncswift.discovery.cleanDeviceDisplayName(self?.name ?: android.os.Build.MODEL)
                        ConnectionManager.sendFcmNotification(
                            token = senderToken,
                            title = copy.connectionRejected,
                            body = copy.notificationPairRejectedStatus.replace("%s", selfName),
                            data = mapOf(
                                "type" to "pair_rejected",
                                "senderName" to selfName
                            )
                        )
                    }
                }
                showToast(copy.connectionRequestRejected)
            }
            ACTION_ACCEPT_FILE -> {
                val transferId = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: ""
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: copy.deviceSingle
                val senderHost = intent.getStringExtra(EXTRA_SENDER_HOST) ?: ""
                val senderPort = intent.getIntExtra(EXTRA_SENDER_PORT, 52424)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File"
                val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
                val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream"

                showToast("${copy.transferringFile} ($fileName)")

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    net.syncswift.transfer.TransferEngine.downloadStagedFile(
                        senderHost = senderHost,
                        senderPort = senderPort,
                        senderName = senderName,
                        transferId = transferId,
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType
                    )
                }
            }
            ACTION_REJECT_FILE -> {
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: copy.deviceSingle
                val senderHost = intent.getStringExtra(EXTRA_SENDER_HOST) ?: ""
                val transferId = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: ""
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val senderToken = ConnectionManager.queryPushTokenFromSupabase(senderHost, senderName)
                    if (!senderToken.isNullOrBlank()) {
                        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                        val selfName = net.syncswift.discovery.cleanDeviceDisplayName(self?.name ?: android.os.Build.MODEL)
                        ConnectionManager.sendFcmNotification(
                            token = senderToken,
                            title = copy.transferRejectedByRecipient,
                            body = copy.notificationFileRejectedStatus.replace("%s", selfName),
                            data = mapOf(
                                "type" to "file_rejected",
                                "transferId" to transferId,
                                "senderName" to selfName
                            )
                        )
                    }
                }
                showToast(copy.transferDeclined)
            }
        }
    }
}
