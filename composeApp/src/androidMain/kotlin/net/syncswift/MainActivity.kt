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
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.connection.IncomingTransferRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionManager.init(this)
        net.syncswift.discovery.DeviceDiscoveryManager.init(this)
        net.syncswift.transfer.TransferEngine.init(this)
        net.syncswift.fcm.FcmSyncManager.init(this)
        val preferences = getSharedPreferences("syncswift_preferences", MODE_PRIVATE)
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        handleNotificationIntent(intent)

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

    override fun onResume() {
        super.onResume()
        SessionManager.setCurrentActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionManager.setCurrentActivity(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val type = intent?.getStringExtra("type") ?: return
        val senderName = intent.getStringExtra("senderName") ?: "SyncSwift Cihazı"
        if (type == "clipboard_sync") {
            val clipText = intent.getStringExtra("text") ?: ""
            if (clipText.isNotBlank()) {
                try {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("SyncSwift", clipText)
                    cm?.setPrimaryClip(clip)
                    val copy = currentAppCopy()
                    showNativeToast(copy.clipboardSynced.replace("%s", clipText.take(30)))
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to set clipboard: ${e.message}")
                }
            }
        } else if (type == "pair_request") {
            val senderHost = intent.getStringExtra("senderHost") ?: ""
            val senderPort = intent.getIntExtra("senderPort", 52424)
            val senderId = intent.getStringExtra("senderId") ?: ""
            val senderOs = intent.getStringExtra("senderOs") ?: "Android"

            ConnectionManager.dispatchIncomingPairRequest(
                IncomingPairRequest(
                    senderId = senderId.ifBlank { "$senderHost:$senderPort" },
                    senderName = senderName,
                    senderHost = senderHost,
                    senderPort = senderPort,
                    senderOsType = senderOs
                )
            )
        } else if (type == "file_request") {
            val transferId = intent.getStringExtra("transferId") ?: ""
            val senderHost = intent.getStringExtra("senderHost") ?: ""
            val senderPort = intent.getIntExtra("senderPort", 52424)
            val fileName = intent.getStringExtra("fileName") ?: "Dosya"
            val fileSize = intent.getStringExtra("fileSize")?.toLongOrNull() ?: 0L
            val mimeType = intent.getStringExtra("mimeType") ?: "application/octet-stream"

            ConnectionManager.dispatchIncomingTransferRequest(
                IncomingTransferRequest(
                    senderName = senderName,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    senderHost = senderHost,
                    senderPort = senderPort,
                    transferId = transferId
                )
            )
        }
    }
}
