package net.syncswift

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.syncswift.clipboard.AndroidClipboardWatcher
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.connection.IncomingTransferRequest
import net.syncswift.shortcuts.ShortcutHelper

class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionManager.init(this)
        if (net.syncswift.biometric.BiometricAuthManager.isEnabled()) {
            net.syncswift.biometric.BiometricAuthManager.lockApp()
        }
        net.syncswift.discovery.DeviceDiscoveryManager.init(this)
        net.syncswift.transfer.TransferEngine.init(this)
        net.syncswift.fcm.FcmSyncManager.init(this)
        net.syncswift.NetworkMonitor.init(this)
        val preferences = getSharedPreferences("syncswift_preferences", MODE_PRIVATE)
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        AndroidClipboardWatcher.init(this)
        ShortcutHelper.updateShortcuts(this)
        handleNotificationIntent(intent)
        handleSharedOrShortcutIntent(intent)

        setContent {
            var onboardingSeen by remember { mutableStateOf(preferences.getBoolean("onboarding_seen", false)) }
            SyncSwiftApp(
                onboardingSeen = onboardingSeen,
                onOnboardingFinished = {
                    preferences.edit().putBoolean("onboarding_seen", true).apply()
                    onboardingSeen = true
                    val shouldAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        !preferences.getBoolean("notification_permission_asked", false)
                    if (shouldAsk) {
                        preferences.edit().putBoolean("notification_permission_asked", true).apply()
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.setCurrentActivity(this)
        net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
        net.syncswift.discovery.DeviceDiscoveryManager.triggerDiscoveryPing()
        AndroidClipboardWatcher.startListening()
        ShortcutHelper.updateShortcuts(this)
        if (net.syncswift.biometric.BiometricAuthManager.isEnabled() && net.syncswift.biometric.BiometricAuthManager.isAppLocked.value) {
            net.syncswift.biometric.BiometricAuthManager.requestPrompt()
        }
    }

    override fun onPause() {
        super.onPause()
        AndroidClipboardWatcher.stopListening()
        // Kept alive so device remains visible and connected in background
    }

    override fun onStop() {
        super.onStop()
        if (net.syncswift.biometric.BiometricAuthManager.isEnabled()) {
            net.syncswift.biometric.BiometricAuthManager.lockApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        net.syncswift.discovery.DeviceDiscoveryManager.broadcastGoodbye()
        net.syncswift.discovery.DeviceDiscoveryManager.stopDiscovery()
        SessionManager.setCurrentActivity(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleSharedOrShortcutIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val type = intent?.getStringExtra("type") ?: return
        val senderName = intent.getStringExtra("senderName") ?: "SyncSwift Cihazı"
        if (type == "clipboard_sync" || type == "clipboard_request") {
            val transferId = intent.getStringExtra("transferId") ?: ""
            val clipText = intent.getStringExtra("text") ?: intent.getStringExtra("content") ?: ""
            val timestamp = intent.getLongExtra("timestamp", 0L)
            val isExpired = timestamp > 0L && (System.currentTimeMillis() - timestamp > 180_000L)

            if (isExpired || ConnectionManager.isRequestCancelled(transferId)) {
                showNativeToast(currentAppCopy().clipboardCancelledBySender)
                return
            }

            val preview = clipText.take(35).replace("\n", " ")
            ConnectionManager.dispatchIncomingTransferRequest(
                IncomingTransferRequest(
                    senderName = senderName,
                    fileName = "${currentAppCopy().clipboard}: $preview",
                    fileSize = clipText.toByteArray().size.toLong(),
                    mimeType = "text/plain",
                    senderHost = intent.getStringExtra("senderHost") ?: "",
                    senderPort = intent.getIntExtra("senderPort", 52424),
                    transferId = transferId,
                    downloadUrl = clipText,
                    isRemote = true,
                    senderLanguage = intent.getStringExtra("senderLanguage") ?: "en"
                )
            )
        } else if (type == "pair_request") {
            val senderHost = intent.getStringExtra("senderHost") ?: ""
            val senderPort = intent.getIntExtra("senderPort", 52424)
            val senderId = intent.getStringExtra("senderId") ?: ""
            val senderOs = intent.getStringExtra("senderOs") ?: "Android"
            val senderLanguage = intent.getStringExtra("senderLanguage") ?: "en"
            val senderPushToken = intent.getStringExtra("senderPushToken") ?: ""
            val timestamp = intent.getLongExtra("timestamp", 0L)
            val key = "$senderHost:$senderPort"
            val isExpired = timestamp > 0L && (System.currentTimeMillis() - timestamp > 120_000L)

            if (isExpired || ConnectionManager.isRequestCancelled(key) ||
                ConnectionManager.isRequestCancelled(senderId) ||
                ConnectionManager.isRequestCancelled(senderHost)) {
                showNativeToast(currentAppCopy().requestCancelledBySender)
                return
            }

            ConnectionManager.dispatchIncomingPairRequest(
                IncomingPairRequest(
                    senderId = senderId.ifBlank { key },
                    senderName = senderName,
                    senderHost = senderHost,
                    senderPort = senderPort,
                    senderOsType = senderOs,
                    senderLanguage = senderLanguage,
                    senderPushToken = senderPushToken
                )
            )
        } else if (type == "file_request") {
            val transferId = intent.getStringExtra("transferId") ?: ""
            val senderHost = intent.getStringExtra("senderHost") ?: ""
            val senderPort = intent.getIntExtra("senderPort", 52424)
            val fileName = intent.getStringExtra("fileName") ?: "Dosya"
            val fileSize = intent.getStringExtra("fileSize")?.toLongOrNull() ?: 0L
            val mimeType = intent.getStringExtra("mimeType") ?: "application/octet-stream"
            val senderLanguage = intent.getStringExtra("senderLanguage") ?: "en"
            val timestamp = intent.getLongExtra("timestamp", 0L)
            val isExpired = timestamp > 0L && (System.currentTimeMillis() - timestamp > 180_000L)

            if (isExpired || ConnectionManager.isRequestCancelled(transferId)) {
                showNativeToast(currentAppCopy().transferCancelledBySender)
                return
            }

            ConnectionManager.dispatchIncomingTransferRequest(
                IncomingTransferRequest(
                    senderName = senderName,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    senderHost = senderHost,
                    senderPort = senderPort,
                    transferId = transferId,
                    senderLanguage = senderLanguage
                )
            )
        }
        intent?.removeExtra("type")
    }

    private fun handleSharedOrShortcutIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_SEND -> {
                handleSendIntent(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultipleIntent(intent)
            }
            ShortcutHelper.ACTION_SEND_FILE, "net.syncswift.ACTION_SHORTCUT_SEND_FILE" -> {
                ExternalActionManager.setPendingAction(ExternalAction.TriggerFilePick)
            }
            ShortcutHelper.ACTION_SEND_CLIPBOARD, "net.syncswift.ACTION_SHORTCUT_SEND_CLIPBOARD" -> {
                val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (text.isNullOrBlank()) {
                    showNativeToast(currentAppCopy().clipboardEmpty)
                } else {
                    ExternalActionManager.setPendingAction(ExternalAction.SendClipboard(text))
                }
            }
        }
        // Clear action to prevent re-handling on configuration change
        intent.action = null
    }

    private fun handleSendIntent(intent: Intent) {
        val type = intent.type ?: "*/*"
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val extraStream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (extraStream != null) {
            readUriAndEmitFile(extraStream, type)
        } else if (!extraText.isNullOrBlank()) {
            ExternalActionManager.setPendingAction(ExternalAction.SendClipboard(extraText.trim()))
        }
    }

    private fun handleSendMultipleIntent(intent: Intent) {
        val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        val firstUri = uris?.firstOrNull()
        if (firstUri != null) {
            readUriAndEmitFile(firstUri, intent.type ?: "*/*")
        }
    }

    private fun readUriAndEmitFile(uri: Uri, fallbackMime: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var fileName = "shared_file"
                val mimeType = contentResolver.getType(uri) ?: fallbackMime

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                }

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        ExternalActionManager.setPendingAction(
                            ExternalAction.SendFile(fileName, bytes, mimeType)
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed reading shared uri: ${e.message}")
            }
        }
    }
}
