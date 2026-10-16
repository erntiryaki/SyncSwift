package net.syncswift.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OutgoingStatusType {
    CONNECTION,
    FILE
}

enum class OutgoingStep {
    WAITING_APPROVAL,
    IN_PROGRESS,
    SUCCESS,
    REJECTED_OR_FAILED
}

data class OutgoingRequestState(
    val type: OutgoingStatusType,
    val deviceName: String,
    val fileName: String? = null,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val progress: Float = 0f,
    val step: OutgoingStep = OutgoingStep.WAITING_APPROVAL,
    val message: String = "",
    val onCancel: (() -> Unit)? = null
)

object OutgoingStatusManager {
    private val _state = MutableStateFlow<OutgoingRequestState?>(null)
    val state: StateFlow<OutgoingRequestState?> = _state.asStateFlow()

    private var currentCancelAction: (() -> Unit)? = null

    fun show(
        type: OutgoingStatusType,
        deviceName: String,
        fileName: String? = null,
        totalBytes: Long = 0L,
        step: OutgoingStep = OutgoingStep.WAITING_APPROVAL,
        message: String = "",
        onCancel: (() -> Unit)? = null
    ) {
        currentCancelAction = onCancel
        _state.value = OutgoingRequestState(
            type = type,
            deviceName = deviceName,
            fileName = fileName,
            totalBytes = totalBytes,
            transferredBytes = 0L,
            progress = 0f,
            step = step,
            message = message,
            onCancel = onCancel
        )
    }

    fun updateProgress(transferredBytes: Long, totalBytes: Long, message: String = "") {
        val current = _state.value ?: return
        val p = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        _state.value = current.copy(
            step = OutgoingStep.IN_PROGRESS,
            transferredBytes = transferredBytes,
            totalBytes = totalBytes,
            progress = p,
            message = if (message.isNotBlank()) message else current.message
        )
    }

    fun completeSuccess(message: String = "") {
        val current = _state.value ?: return
        currentCancelAction = null
        _state.value = current.copy(
            step = OutgoingStep.SUCCESS,
            progress = 1f,
            transferredBytes = current.totalBytes,
            message = message
        )
    }

    fun fail(message: String = "") {
        val current = _state.value ?: return
        currentCancelAction = null
        _state.value = current.copy(
            step = OutgoingStep.REJECTED_OR_FAILED,
            message = message
        )
    }

    fun cancel() {
        val action = currentCancelAction
        currentCancelAction = null
        _state.value = null
        try {
            action?.invoke()
        } catch (_: Exception) {}
    }

    fun dismiss() {
        currentCancelAction = null
        _state.value = null
    }
}
