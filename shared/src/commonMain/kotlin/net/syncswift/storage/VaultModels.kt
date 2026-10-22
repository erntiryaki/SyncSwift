package net.syncswift.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class VaultFileItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val isStarred: Boolean = false,
    val createdAtMs: Long = 0L
) {
    val formattedSize: String
        get() {
            val mb = fileSize.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                val rounded = ((mb * 10).toLong()) / 10.0
                "${rounded} MB"
            } else {
                "${fileSize / 1024} KB"
            }
        }
}

object PersonalVaultManager {
    private val _vaultFiles = MutableStateFlow<List<VaultFileItem>>(emptyList())
    val vaultFiles: StateFlow<List<VaultFileItem>> = _vaultFiles.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun updateFiles(files: List<VaultFileItem>) {
        _vaultFiles.value = files
    }

    fun addFile(file: VaultFileItem) {
        val list = _vaultFiles.value.toMutableList()
        list.add(0, file)
        _vaultFiles.value = list
    }

    fun removeFile(id: String) {
        val list = _vaultFiles.value.toMutableList()
        list.removeAll { it.id == id }
        _vaultFiles.value = list
    }

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }
}
