package net.syncswift.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransferEngineV2 {
    private val _transfers = MutableStateFlow<List<TransferItemV2>>(emptyList())
    val transfers: StateFlow<List<TransferItemV2>> = _transfers.asStateFlow()

    fun enqueueTransfer(item: TransferItemV2) {
        val list = _transfers.value.toMutableList()
        list.add(0, item)
        _transfers.value = list
    }

    fun updateProgress(
        transferId: String,
        bytesTransferred: Long,
        speedBytesPerSec: Long,
        chunkIndex: Int,
        totalChunks: Int
    ) {
        val list = _transfers.value.toMutableList()
        val index = list.indexOfFirst { it.transferId == transferId }
        if (index >= 0) {
            val current = list[index]
            list[index] = current.copy(
                bytesTransferred = bytesTransferred,
                speedBytesPerSec = speedBytesPerSec,
                currentChunkIndex = chunkIndex,
                totalChunks = totalChunks,
                status = TransferStatusV2.TRANSFERRING
            )
            _transfers.value = list
        }
    }

    fun completeTransfer(transferId: String) {
        val list = _transfers.value.toMutableList()
        val index = list.indexOfFirst { it.transferId == transferId }
        if (index >= 0) {
            val current = list[index]
            list[index] = current.copy(
                bytesTransferred = current.fileSize,
                status = TransferStatusV2.COMPLETED,
                completedAtMs = System.currentTimeMillis()
            )
            _transfers.value = list
        }
    }

    fun failTransfer(transferId: String, errorMessage: String) {
        val list = _transfers.value.toMutableList()
        val index = list.indexOfFirst { it.transferId == transferId }
        if (index >= 0) {
            val current = list[index]
            list[index] = current.copy(
                status = TransferStatusV2.FAILED,
                errorMessage = errorMessage
            )
            _transfers.value = list
        }
    }

    fun cancelTransfer(transferId: String) {
        val list = _transfers.value.toMutableList()
        val index = list.indexOfFirst { it.transferId == transferId }
        if (index >= 0) {
            val current = list[index]
            list[index] = current.copy(
                status = TransferStatusV2.CANCELLED
            )
            _transfers.value = list
        }
    }
}
