package net.syncswift.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.syncswift.connection.ConnectionManager
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACCEPT_PAIR = "net.syncswift.ACTION_ACCEPT_PAIR"
        const val ACTION_REJECT_PAIR = "net.syncswift.ACTION_REJECT_PAIR"
        const val ACTION_ACCEPT_FILE = "net.syncswift.ACTION_ACCEPT_FILE"
        const val ACTION_REJECT_FILE = "net.syncswift.ACTION_REJECT_FILE"
        const val ACTION_OPEN_FILE = "net.syncswift.ACTION_OPEN_FILE"
        const val ACTION_SHOW_LOCATION = "net.syncswift.ACTION_SHOW_LOCATION"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_HOST = "sender_host"
        const val EXTRA_SENDER_PORT = "sender_port"
        const val EXTRA_SENDER_ID = "sender_id"
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_SIZE = "file_size"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_IS_REMOTE = "is_remote"
        const val EXTRA_FILE_PATH = "file_path"
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
            net.syncswift.ToastNotifier.show(msg)
        }

        when (intent.action) {
            ACTION_ACCEPT_PAIR -> {
                val pending = ConnectionManager.pendingPairRequest.value
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: copy.deviceSingle
                val senderHost = intent.getStringExtra(EXTRA_SENDER_HOST) ?: ""
                val senderPort = intent.getIntExtra(EXTRA_SENDER_PORT, 52424)
                val senderId = intent.getStringExtra(EXTRA_SENDER_ID) ?: ""
                val key = "$senderHost:$senderPort"

                if (pending == null ||
                    ConnectionManager.isRequestCancelled(key) ||
                    ConnectionManager.isRequestCancelled(senderId) ||
                    ConnectionManager.isRequestCancelled(senderHost)) {
                    showToast(copy.requestCancelledBySender)
                    return
                }

                ConnectionManager.acceptPairRequest(pending)
            }
            ACTION_REJECT_PAIR -> {
                val pending = ConnectionManager.pendingPairRequest.value
                if (pending != null) {
                    ConnectionManager.rejectPairRequest(pending)
                }
                showToast(copy.connectionRequestRejected)
            }
            ACTION_ACCEPT_FILE -> {
                val transferId = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: ""
                val pending = ConnectionManager.pendingTransferRequest.value

                if (pending == null || (transferId.isNotBlank() && pending.transferId != transferId && pending.requestId != transferId)) {
                    showToast(copy.transferCancelledBySender)
                    return
                }

                val fileName = pending.fileName
                showToast("${copy.transferringFile} ($fileName)")
                ConnectionManager.acceptTransferRequest(pending)
            }
            ACTION_REJECT_FILE -> {
                val pending = ConnectionManager.pendingTransferRequest.value
                if (pending != null) {
                    ConnectionManager.rejectTransferRequest(pending)
                }
                showToast(copy.transferDeclined)
            }
            ACTION_OPEN_FILE -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File"
                val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "*/*"
                net.syncswift.openFile(filePath, fileName, mimeType)
            }
            ACTION_SHOW_LOCATION -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File"
                net.syncswift.showFileLocation(filePath, fileName)
            }
        }
    }
}
