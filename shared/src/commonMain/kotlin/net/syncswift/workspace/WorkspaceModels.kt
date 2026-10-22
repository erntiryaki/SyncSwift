package net.syncswift.workspace

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMemberV2(
    val deviceId: String,
    val deviceName: String,
    val osType: String,
    val role: String = "MEMBER", // "OWNER", "EDITOR", "VIEWER"
    val joinedAtMs: Long
)

@Serializable
data class WorkspaceItemV2(
    val id: String,
    val name: String,
    val description: String = "",
    val members: List<WorkspaceMemberV2> = emptyList(),
    val totalFiles: Int = 0,
    val totalSizeBytes: Long = 0L,
    val createdAtMs: Long
)
