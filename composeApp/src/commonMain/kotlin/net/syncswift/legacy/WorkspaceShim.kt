package net.syncswift.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.currentTimeMs

enum class WorkspaceRole { OWNER, MEMBER }

enum class FileVisibility {
    ONLY_ME,
    SELECTED_MEMBERS,
    ALL_MEMBERS
}

enum class DeviceJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class WorkspaceMember(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: WorkspaceRole = WorkspaceRole.MEMBER,
    val joinedAt: Long = currentTimeMs()
)

data class WorkspaceItem(
    val id: String = "ws_primary",
    val name: String = "Kişisel Kasa",
    val description: String = "Şifreli bulut kasası ve cihazlarım",
    val ownerEmail: String = "",
    val role: WorkspaceRole = WorkspaceRole.OWNER,
    val memberCount: Int = 1,
    val createdAt: Long = currentTimeMs()
)

data class WorkspaceFolder(
    val id: String = "",
    val workspaceId: String = "",
    val parentId: String? = null,
    val name: String = "",
    val createdAt: Long = currentTimeMs()
)

data class WorkspaceFile(
    val id: String = "",
    val workspaceId: String = "",
    val folderId: String? = null,
    val name: String = "",
    val size: Long = 0L,
    val mimeType: String = "*/*",
    val uploaderEmail: String = "",
    val uploaderName: String = "",
    val uploadedAt: Long = currentTimeMs(),
    val visibility: FileVisibility = FileVisibility.ALL_MEMBERS,
    val selectedMembers: List<String> = emptyList(),
    val downloadUrl: String? = null
)

data class DeviceJob(
    val id: String = "",
    val targetDeviceId: String = "",
    val targetDeviceName: String = "",
    val workspaceId: String = "",
    val folderPath: String = "",
    val fileName: String = "",
    val status: DeviceJobStatus = DeviceJobStatus.QUEUED,
    val createdAt: Long = currentTimeMs()
)

object WorkspaceManager {
    private val defaultWs = WorkspaceItem()

    private val _workspaces = MutableStateFlow<List<WorkspaceItem>>(listOf(defaultWs))
    val workspaces: StateFlow<List<WorkspaceItem>> = _workspaces.asStateFlow()

    private val _selectedWorkspace = MutableStateFlow<WorkspaceItem?>(defaultWs)
    val selectedWorkspace: StateFlow<WorkspaceItem?> = _selectedWorkspace.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _folders = MutableStateFlow<List<WorkspaceFolder>>(emptyList())
    val folders: StateFlow<List<WorkspaceFolder>> = _folders.asStateFlow()

    private val _files = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val files: StateFlow<List<WorkspaceFile>> = _files.asStateFlow()

    private val _members = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val members: StateFlow<List<WorkspaceMember>> = _members.asStateFlow()

    private val _deviceJobs = MutableStateFlow<List<DeviceJob>>(emptyList())
    val deviceJobs: StateFlow<List<DeviceJob>> = _deviceJobs.asStateFlow()

    fun resetForLogout() {
        _workspaces.value = listOf(defaultWs)
        _selectedWorkspace.value = defaultWs
        _currentFolderId.value = null
        _folders.value = emptyList()
        _files.value = emptyList()
        _members.value = emptyList()
        _deviceJobs.value = emptyList()
    }

    fun syncWorkspacesWithAccount(email: String) {}

    fun selectWorkspace(workspace: WorkspaceItem?) {
        _selectedWorkspace.value = workspace
        _currentFolderId.value = null
    }

    fun setCurrentFolder(folderId: String?) {
        _currentFolderId.value = folderId
    }

    fun navigateIntoFolder(folderId: String?) {
        _currentFolderId.value = folderId
    }

    fun createWorkspace(name: String, description: String = ""): WorkspaceItem {
        val ws = WorkspaceItem(name = name, description = description)
        _workspaces.value = _workspaces.value + ws
        return ws
    }

    fun updateWorkspace(id: String, name: String, description: String = "") {
        _workspaces.value = _workspaces.value.map {
            if (it.id == id) it.copy(name = name, description = description) else it
        }
    }

    fun deleteWorkspace(id: String) {
        _workspaces.value = _workspaces.value.filter { it.id != id }
        if (_selectedWorkspace.value?.id == id) {
            _selectedWorkspace.value = _workspaces.value.firstOrNull()
        }
    }

    fun createFolder(name: String, parentId: String? = null) {
        val wsId = _selectedWorkspace.value?.id ?: return
        val folder = WorkspaceFolder(id = "${currentTimeMs()}", workspaceId = wsId, parentId = parentId, name = name)
        _folders.value = _folders.value + folder
    }

    fun deleteFolder(id: String) {
        _folders.value = _folders.value.filter { it.id != id }
    }

    fun deleteFile(id: String) {
        _files.value = _files.value.filter { it.id != id }
        net.syncswift.storage.PersonalVaultManager.removeFile(id)
    }

    fun updateFileVisibility(fileId: String, visibility: FileVisibility, selectedMembers: List<String> = emptyList()) {
        _files.value = _files.value.map {
            if (it.id == fileId) it.copy(visibility = visibility, selectedMembers = selectedMembers) else it
        }
    }

    fun uploadFile(
        fileBytes: ByteArray = ByteArray(0),
        fileName: String = "",
        fileSize: Long = 0L,
        name: String = fileName,
        size: Long = fileSize,
        mimeType: String = "*/*",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val actualName = if (fileName.isNotBlank()) fileName else name.ifBlank { "Dosya_${currentTimeMs()}" }
        val actualSize = if (fileSize > 0) fileSize else size
        val wsId = _selectedWorkspace.value?.id ?: "ws_primary"
        val newFile = WorkspaceFile(
            id = "file_${currentTimeMs()}",
            workspaceId = wsId,
            folderId = _currentFolderId.value,
            name = actualName,
            size = actualSize,
            mimeType = mimeType,
            uploadedAt = currentTimeMs()
        )
        _files.value = listOf(newFile) + _files.value
        net.syncswift.storage.PersonalVaultManager.addFile(
            net.syncswift.storage.VaultFileItem(
                id = newFile.id,
                fileName = actualName,
                fileSize = actualSize,
                mimeType = mimeType,
                s3Key = "vault/$wsId/${newFile.id}",
                createdAtMs = newFile.uploadedAt
            )
        )
        onSuccess()
    }

    fun refreshWorkspaceMembers(workspaceId: String) {}

    fun addMember(workspaceId: String, email: String, role: WorkspaceRole = WorkspaceRole.MEMBER) {}

    fun removeMember(workspaceId: String, memberId: String) {}

    fun createDeviceJob(
        targetDeviceId: String,
        targetDeviceName: String,
        folderPath: String,
        fileName: String
    ) {}

    fun queueRemoteJob(
        targetDeviceId: String,
        targetDeviceName: String,
        folderPath: String,
        fileName: String
    ) {}

    fun generateInviteCode(workspaceId: String): String = "SYNC-00"

    fun joinWorkspaceByCode(code: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        onResult?.invoke(false, "Geçersiz kod")
        return false
    }
}
