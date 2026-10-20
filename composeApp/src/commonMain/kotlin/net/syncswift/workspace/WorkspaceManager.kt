package net.syncswift.workspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager
import net.syncswift.currentTimeMs
import net.syncswift.discovery.cleanDeviceDisplayName
import kotlin.random.Random

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
    val id: String,
    val email: String,
    val displayName: String,
    val role: WorkspaceRole = WorkspaceRole.MEMBER,
    val joinedAt: Long = currentTimeMs()
)

data class WorkspaceItem(
    val id: String,
    val name: String,
    val description: String = "",
    val ownerEmail: String,
    val role: WorkspaceRole = WorkspaceRole.MEMBER,
    val memberCount: Int = 1,
    val createdAt: Long = currentTimeMs()
)

data class WorkspaceFolder(
    val id: String,
    val workspaceId: String,
    val parentId: String? = null,
    val name: String,
    val createdAt: Long = currentTimeMs()
)

data class WorkspaceFile(
    val id: String,
    val workspaceId: String,
    val folderId: String? = null,
    val name: String,
    val size: Long,
    val mimeType: String = "*/*",
    val uploaderEmail: String,
    val uploaderName: String,
    val uploadedAt: Long = currentTimeMs(),
    val visibility: FileVisibility = FileVisibility.ALL_MEMBERS,
    val selectedMembers: List<String> = emptyList(),
    val downloadUrl: String? = null
)

data class DeviceJob(
    val id: String,
    val targetDeviceId: String,
    val targetDeviceName: String,
    val workspaceId: String,
    val folderPath: String,
    val fileName: String,
    val status: DeviceJobStatus = DeviceJobStatus.QUEUED,
    val createdAt: Long = currentTimeMs()
)

object WorkspaceManager {
    private val _workspaces = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceItem>> = _workspaces.asStateFlow()

    private val _selectedWorkspace = MutableStateFlow<WorkspaceItem?>(null)
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

    init {
        // Initialize sample/default workspace if user has none
        val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
        val myName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())
        val defaultWs = WorkspaceItem(
            id = "ws_primary",
            name = "Kişisel Kasa",
            description = "Şifreli bulut kasası ve cihazlarım",
            ownerEmail = email,
            role = WorkspaceRole.OWNER,
            memberCount = 1
        )
        _workspaces.value = listOf(defaultWs)
        _selectedWorkspace.value = defaultWs
        _members.value = listOf(
            WorkspaceMember(id = "mem_1", email = email, displayName = myName, role = WorkspaceRole.OWNER)
        )
    }

    fun selectWorkspace(workspace: WorkspaceItem) {
        _selectedWorkspace.value = workspace
        _currentFolderId.value = null
    }

    fun createWorkspace(name: String, description: String = ""): WorkspaceItem {
        val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
        val ws = WorkspaceItem(
            id = "ws_${currentTimeMs()}_${Random.nextInt(100, 999)}",
            name = name,
            description = description,
            ownerEmail = email,
            role = WorkspaceRole.OWNER,
            memberCount = 1
        )
        _workspaces.value = listOf(ws) + _workspaces.value
        _selectedWorkspace.value = ws
        return ws
    }

    fun joinWorkspaceByCode(code: String): Boolean {
        if (code.length != 6) return false
        val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
        val joined = WorkspaceItem(
            id = "ws_code_$code",
            name = "Ekip Alanı ($code)",
            description = "Davet kodu ile katıldınız",
            ownerEmail = "team_owner@syncswift.net",
            role = WorkspaceRole.MEMBER,
            memberCount = 2
        )
        _workspaces.value = _workspaces.value + joined
        _selectedWorkspace.value = joined
        return true
    }

    fun generateInviteCode(workspaceId: String): String {
        return Random.nextInt(100000, 999999).toString()
    }

    fun createFolder(name: String) {
        val wsId = _selectedWorkspace.value?.id ?: return
        val folder = WorkspaceFolder(
            id = "fld_${currentTimeMs()}",
            workspaceId = wsId,
            parentId = _currentFolderId.value,
            name = name
        )
        _folders.value = _folders.value + folder
    }

    fun navigateIntoFolder(folderId: String?) {
        _currentFolderId.value = folderId
    }

    fun uploadFile(
        name: String,
        size: Long,
        mimeType: String = "*/*",
        visibility: FileVisibility = FileVisibility.ALL_MEMBERS,
        selectedMembers: List<String> = emptyList()
    ) {
        val wsId = _selectedWorkspace.value?.id ?: return
        val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
        val myName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())
        val file = WorkspaceFile(
            id = "file_${currentTimeMs()}",
            workspaceId = wsId,
            folderId = _currentFolderId.value,
            name = name,
            size = size,
            mimeType = mimeType,
            uploaderEmail = email,
            uploaderName = myName,
            visibility = visibility,
            selectedMembers = selectedMembers
        )
        _files.value = listOf(file) + _files.value
    }

    fun updateFileVisibility(fileId: String, visibility: FileVisibility, selectedMembers: List<String> = emptyList()) {
        _files.value = _files.value.map {
            if (it.id == fileId) it.copy(visibility = visibility, selectedMembers = selectedMembers) else it
        }
    }

    fun deleteFile(fileId: String) {
        _files.value = _files.value.filter { it.id != fileId }
    }

    fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        _files.value = _files.value.filter { it.folderId != folderId }
    }

    fun deleteWorkspace(workspaceId: String) {
        _workspaces.value = _workspaces.value.filter { it.id != workspaceId }
        if (_selectedWorkspace.value?.id == workspaceId) {
            _selectedWorkspace.value = _workspaces.value.firstOrNull()
        }
    }

    /** Dispatches a secure remote command to user's desktop to fetch allowed file into workspace */
    fun queueRemoteJob(targetDeviceId: String, targetDeviceName: String, folderPath: String, fileName: String): DeviceJob {
        val wsId = _selectedWorkspace.value?.id ?: "ws_primary"
        val job = DeviceJob(
            id = "job_${currentTimeMs()}",
            targetDeviceId = targetDeviceId,
            targetDeviceName = targetDeviceName,
            workspaceId = wsId,
            folderPath = folderPath,
            fileName = fileName,
            status = DeviceJobStatus.QUEUED
        )
        _deviceJobs.value = listOf(job) + _deviceJobs.value
        return job
    }
}
