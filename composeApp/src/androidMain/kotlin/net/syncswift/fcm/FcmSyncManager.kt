package net.syncswift.fcm

import android.content.Context
import android.os.Build
import android.util.Log
import net.syncswift.SessionManager
import net.syncswift.SyncSwiftConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object FcmSyncManager {
    private const val TAG = "SyncSwiftFCM"

    fun init(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val copy = net.syncswift.currentAppCopy()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channel = android.app.NotificationChannel(
                    "syncswift_requests_channel",
                    copy.notificationChannelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = copy.notificationChannelDesc
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.w(TAG, "FCM Token retrieved successfully: $token")
                    SyncSwiftFirebaseMessagingService.saveToken(context, token)
                    syncTokenWithBackend(context, token)
                } else {
                    Log.w(TAG, "Failed to get FCM token: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM: ${e.message}")
        }
    }

    fun syncTokenWithBackend(context: Context, token: String) {
        val email = SessionManager.getLoggedInEmail() ?: ""
        val deviceId = java.util.UUID.nameUUIDFromBytes("${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}".toByteArray()).toString()
        val localIp = getLocalIpAddress() ?: ""
        val selfDev = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
        val cleanName = net.syncswift.discovery.cleanDeviceDisplayName(selfDev?.name ?: Build.MODEL)
        val userId = SessionManager.getLoggedInUserId()

        Log.w(TAG, "Syncing FCM token for $email (Device ID: $deviceId, Name: $cleanName, IP: $localIp, UserId: $userId)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolvedUserId = userId ?: if (email.isNotBlank()) net.syncswift.SupabaseNetwork.getUserId(email) else null
                net.syncswift.SupabaseNetwork.registerDeviceInSupabase(
                    email = email,
                    deviceId = deviceId,
                    deviceName = cleanName,
                    deviceType = "Android",
                    pushToken = token,
                    localIp = localIp,
                    port = 52424,
                    userId = resolvedUserId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register device in Supabase: ${e.message}")
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        return net.syncswift.discovery.DeviceDiscoveryManager.getLocalIpAddress()
    }
}
