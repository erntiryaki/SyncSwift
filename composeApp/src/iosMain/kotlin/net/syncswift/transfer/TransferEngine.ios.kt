package net.syncswift.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object TransferEngine {
    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    actual val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    actual fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("iOS transfer not implemented yet")
    }

    actual fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("iOS transfer not implemented yet")
    }
}
