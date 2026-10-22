package net.syncswift.agent

import kotlinx.serialization.Serializable

enum class SyncDirection {
    ONE_WAY_SEND,
    ONE_WAY_RECEIVE,
    TWO_WAY_MIRROR
}

@Serializable
data class SyncRule(
    val id: String,
    val name: String,
    val localPath: String,
    val remoteDeviceId: String,
    val remotePath: String,
    val direction: SyncDirection = SyncDirection.TWO_WAY_MIRROR,
    val isEnabled: Boolean = true,
    val lastSyncTimeMs: Long? = null
)

@Serializable
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedTimeMs: Long
)

@Serializable
data class RemoteDirectoryListing(
    val deviceId: String,
    val requestedPath: String,
    val entries: List<RemoteFileEntry>,
    val timestampMs: Long
)
