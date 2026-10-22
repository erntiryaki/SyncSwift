package net.syncswift.webrtc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.syncswift.SessionManager
import net.syncswift.SyncSwiftConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Signaling client for establishing peer-to-peer WebRTC connections.
 * Uses Supabase REST/Realtime signaling message channel with end-to-end device targeting.
 */
object WebRtcSignalingClient {
    private const val TAG = "WebRtcSignaling"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val DEFAULT_ICE_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302"
    )

    private val _incomingMessages = MutableSharedFlow<SignalingEnvelope>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SignalingEnvelope> = _incomingMessages.asSharedFlow()

    private var pollingJob: Job? = null
    private var isListening = false
    private var lastSeenTimestamp: Long = System.currentTimeMillis()

    /**
     * Start listening for incoming signaling messages addressed to our device.
     */
    fun startListening(myDeviceId: String) {
        if (isListening) return
        isListening = true
        lastSeenTimestamp = System.currentTimeMillis() - 5000L

        pollingJob = scope.launch {
            while (isActive && isListening) {
                try {
                    fetchPendingMessages(myDeviceId)
                } catch (e: Exception) {
                    // Silently wait on network hiccups
                }
                delay(1200) // Poll signaling channel smoothly
            }
        }
    }

    /**
     * Stop listening to signaling channel.
     */
    fun stopListening() {
        isListening = false
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Send an offer, answer, or ICE candidate to a remote peer.
     */
    suspend fun sendEnvelope(envelope: SignalingEnvelope): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${SyncSwiftConfig.SUPABASE_URL}/rest/v1/signaling_messages"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }

            val body = JSONObject().apply {
                put("sender_id", envelope.senderId)
                put("sender_name", envelope.senderName)
                put("target_id", envelope.targetId)
                put("type", envelope.type.name.lowercase())
                put("sdp", envelope.sdp ?: JSONObject.NULL)
                put("candidate", envelope.candidate ?: JSONObject.NULL)
                put("sdp_mid", envelope.sdpMid ?: JSONObject.NULL)
                put("sdp_mline_index", envelope.sdpMLineIndex ?: JSONObject.NULL)
                put("timestamp", envelope.timestamp)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun fetchPendingMessages(myDeviceId: String) = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${SyncSwiftConfig.SUPABASE_URL}/rest/v1/signaling_messages?target_id=eq.$myDeviceId&timestamp=gt.$lastSeenTimestamp&order=timestamp.asc&limit=20"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonArray = org.json.JSONArray(responseText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ts = obj.optLong("timestamp", System.currentTimeMillis())
                    if (ts > lastSeenTimestamp) {
                        lastSeenTimestamp = ts
                    }

                    val typeStr = obj.optString("type", "ping").uppercase()
                    val type = try {
                        SignalingType.valueOf(typeStr)
                    } catch (_: Exception) {
                        SignalingType.PING
                    }

                    val envelope = SignalingEnvelope(
                        id = obj.optString("id", generateRandomId()),
                        senderId = obj.optString("sender_id"),
                        senderName = obj.optString("sender_name", "Remote Device"),
                        targetId = obj.optString("target_id"),
                        type = type,
                        sdp = if (obj.isNull("sdp")) null else obj.optString("sdp"),
                        candidate = if (obj.isNull("candidate")) null else obj.optString("candidate"),
                        sdpMid = if (obj.isNull("sdp_mid")) null else obj.optString("sdp_mid"),
                        sdpMLineIndex = if (obj.isNull("sdp_mline_index")) null else obj.optInt("sdp_mline_index"),
                        timestamp = ts
                    )
                    _incomingMessages.tryEmit(envelope)
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {}
    }
}
