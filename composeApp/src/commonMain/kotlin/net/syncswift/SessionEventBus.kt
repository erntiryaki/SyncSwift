package net.syncswift

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface SessionEvent {
    data class RemoteLoggedOut(val reason: String = "") : SessionEvent
}

object SessionEventBus {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun emitRemoteLogout(reason: String = "") {
        _events.tryEmit(SessionEvent.RemoteLoggedOut(reason))
    }
}
