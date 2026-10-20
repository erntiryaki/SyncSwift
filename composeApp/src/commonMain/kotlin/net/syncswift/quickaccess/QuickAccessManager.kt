package net.syncswift.quickaccess

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager
import net.syncswift.currentTimeMs
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.cleanDeviceDisplayName
import kotlin.random.Random

enum class QuickSessionState {
    IDLE,
    WAITING_FOR_RECEIVER,
    RECEIVER_CONNECTED,
    TRANSFERRING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    FAILED
}

data class QuickAccessFileMetadata(
    val fileName: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "*/*",
    val textContent: String? = null,
    val localUri: String? = null
)

data class QuickSessionData(
    val sessionId: String,
    val code: String,
    val senderName: String,
    val senderHost: String,
    val senderPort: Int,
    val file: QuickAccessFileMetadata,
    val state: QuickSessionState = QuickSessionState.WAITING_FOR_RECEIVER,
    val expiresAt: Long,
    val progress: Float = 0f
)

object QuickAccessManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Active session as Sender
    private val _activeSendSession = MutableStateFlow<QuickSessionData?>(null)
    val activeSendSession: StateFlow<QuickSessionData?> = _activeSendSession.asStateFlow()

    // Incoming session preview as Receiver
    private val _incomingSession = MutableStateFlow<QuickSessionData?>(null)
    val incomingSession: StateFlow<QuickSessionData?> = _incomingSession.asStateFlow()

    private var countdownJob: Job? = null

    // Cache of active local quick codes (for direct local network lookup)
    private val localActiveSessions = mutableMapOf<String, QuickSessionData>()

    /** Generates a new 6-digit session code (e.g. "482910") and initializes waiting state */
    fun createSession(file: QuickAccessFileMetadata): QuickSessionData {
        cancelActiveSession()

        val self = DeviceDiscoveryManager.getSelfDevice()
        val selfName = cleanDeviceDisplayName(self?.name ?: SessionManager.getMyDeviceName())
        val selfHost = self?.host ?: "127.0.0.1"
        val selfPort = self?.port ?: 52424

        // 6-digit random code
        val code = Random.nextInt(100000, 999999).toString()
        val sessionId = "qs_${currentTimeMs()}_$code"
        val expiresAt = currentTimeMs() + (5 * 60 * 1000L) // 5 minutes validity

        val session = QuickSessionData(
            sessionId = sessionId,
            code = code,
            senderName = selfName,
            senderHost = selfHost,
            senderPort = selfPort,
            file = file,
            state = QuickSessionState.WAITING_FOR_RECEIVER,
            expiresAt = expiresAt,
            progress = 0f
        )

        localActiveSessions[code] = session
        _activeSendSession.value = session

        // Start countdown timer job
        countdownJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val curr = _activeSendSession.value ?: break
                if (currentTimeMs() >= curr.expiresAt) {
                    _activeSendSession.value = curr.copy(state = QuickSessionState.EXPIRED)
                    localActiveSessions.remove(code)
                    break
                }
            }
        }

        return session
    }

    /** Looks up a 6-digit session (checks local network active sessions or simulated relay) */
    fun querySessionByCode(code: String): QuickSessionData? {
        val cleanCode = code.trim().replace(" ", "")
        if (cleanCode.length != 6) return null

        val found = localActiveSessions[cleanCode]
        if (found != null && currentTimeMs() < found.expiresAt && found.state != QuickSessionState.CANCELLED) {
            _incomingSession.value = found
            return found
        }
        return null
    }

    /** Receiver accepts the incoming quick session and begins transfer */
    fun acceptIncomingSession(session: QuickSessionData, onProgress: (Float) -> Unit, onComplete: () -> Unit) {
        scope.launch {
            _incomingSession.value = session.copy(state = QuickSessionState.TRANSFERRING)
            // Update sender state if local
            val activeSend = _activeSendSession.value
            if (activeSend != null && activeSend.code == session.code) {
                _activeSendSession.value = activeSend.copy(state = QuickSessionState.RECEIVER_CONNECTED)
            }

            // Simulate progress / transfer execution
            for (p in 1..10) {
                delay(120L)
                val progress = p / 10f
                onProgress(progress)
                _incomingSession.value = session.copy(state = QuickSessionState.TRANSFERRING, progress = progress)
                _activeSendSession.value?.let {
                    _activeSendSession.value = it.copy(state = QuickSessionState.TRANSFERRING, progress = progress)
                }
            }

            _incomingSession.value = session.copy(state = QuickSessionState.COMPLETED, progress = 1f)
            _activeSendSession.value?.let {
                _activeSendSession.value = it.copy(state = QuickSessionState.COMPLETED, progress = 1f)
            }
            onComplete()
        }
    }

    fun cancelActiveSession() {
        countdownJob?.cancel()
        countdownJob = null
        val current = _activeSendSession.value
        if (current != null) {
            localActiveSessions.remove(current.code)
            _activeSendSession.value = current.copy(state = QuickSessionState.CANCELLED)
        }
        _activeSendSession.value = null
    }

    fun dismissIncomingSession() {
        _incomingSession.value = null
    }
}
