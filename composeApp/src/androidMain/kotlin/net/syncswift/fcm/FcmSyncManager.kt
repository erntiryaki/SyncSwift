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
        val subnet = if (localIp.contains(".")) localIp.substringBeforeLast(".") else ""
        val cleanName = net.syncswift.discovery.cleanDeviceDisplayName(Build.MODEL)

        Log.w(TAG, "Syncing FCM token for $email (Device ID: $deviceId, Name: $cleanName, IP: $localIp)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

                val payload = JSONObject().apply {
                    put("id", deviceId)
                    put("device_name", cleanName)
                    put("device_type", "Android")
                    put("push_token", token)
                    put("local_ip", localIp)
                    put("port", 52424)
                    put("network_ssid", subnet)
                    put("is_online", true)
                }

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                if (code in 200..299) {
                    Log.w(TAG, "Device registered in Supabase devices table: HTTP $code")
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Failed to register device in Supabase: HTTP $code, error: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register device in Supabase: ${e.message}")
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
