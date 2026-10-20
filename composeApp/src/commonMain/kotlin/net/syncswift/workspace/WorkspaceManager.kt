package net.syncswift.workspace

import kotlinx.coroutines.*
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val localWorkspaceInvites = mutableMapOf<String, WorkspaceItem>()

    init {
        loadSavedState()
        if (_workspaces.value.isEmpty()) {
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
            saveState()
        } else {
            val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
            val myName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())
            _members.value = listOf(
                WorkspaceMember(id = "mem_1", email = email, displayName = myName, role = WorkspaceRole.OWNER)
            )
        }
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 32) {
                        val hex = ch.code.toString(16).padStart(4, '0')
                        sb.append("\\u$hex")
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun parseJsonObjects(json: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val objRegex = Regex("""\{([^{}]+)\}""")
        val fieldRegex = Regex(""""([^"]+)"\s*:\s*("(?:[^"\\]|\\.)*"|[^,}\s]+)""")

        for (match in objRegex.findAll(json)) {
            val map = mutableMapOf<String, String>()
            for (field in fieldRegex.findAll(match.groupValues[1])) {
                val key = field.groupValues[1]
                var rawVal = field.groupValues[2].trim()
                if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                    rawVal = rawVal.substring(1, rawVal.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                }
                map[key] = rawVal
            }
            if (map.isNotEmpty()) {
                results.add(map)
            }
        }
        return results
    }

    private fun mapToWorkspaceItem(map: Map<String, String>): WorkspaceItem? {
        val id = map["id"] ?: return null
        val name = map["name"] ?: return null
        val desc = map["desc"] ?: map["description"] ?: ""
        val ownerEmail = map["ownerEmail"] ?: "user@syncswift.net"
        val roleStr = map["role"] ?: "OWNER"
        val role = if (roleStr.equals("MEMBER", ignoreCase = true)) WorkspaceRole.MEMBER else WorkspaceRole.OWNER
        val memberCount = map["memberCount"]?.toIntOrNull() ?: 1
        val createdAt = map["createdAt"]?.toLongOrNull() ?: currentTimeMs()
        return WorkspaceItem(id, name, desc, ownerEmail, role, memberCount, createdAt)
    }

    private fun mapToWorkspaceFolder(map: Map<String, String>): WorkspaceFolder? {
        val id = map["id"] ?: return null
        val workspaceId = map["workspaceId"] ?: return null
        val parentId = map["parentId"]?.takeIf { it.isNotBlank() && it != "null" }
        val name = map["name"] ?: return null
        val createdAt = map["createdAt"]?.toLongOrNull() ?: currentTimeMs()
        return WorkspaceFolder(id, workspaceId, parentId, name, createdAt)
    }

    private fun mapToWorkspaceFile(map: Map<String, String>): WorkspaceFile? {
        val id = map["id"] ?: return null
        val workspaceId = map["workspaceId"] ?: return null
        val folderId = map["folderId"]?.takeIf { it.isNotBlank() && it != "null" }
        val name = map["name"] ?: return null
        val size = map["size"]?.toLongOrNull() ?: 0L
        val mimeType = map["mimeType"] ?: "*/*"
        val uploaderEmail = map["uploaderEmail"] ?: ""
        val uploaderName = map["uploaderName"] ?: ""
        val uploadedAt = map["uploadedAt"]?.toLongOrNull() ?: currentTimeMs()
        val visStr = map["visibility"] ?: "ALL_MEMBERS"
        val visibility = try { FileVisibility.valueOf(visStr) } catch (_: Exception) { FileVisibility.ALL_MEMBERS }
        val selectedMembers = map["selectedMembers"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val downloadUrl = map["downloadUrl"]?.takeIf { it.isNotBlank() && it != "null" }
        return WorkspaceFile(
            id = id,
            workspaceId = workspaceId,
            folderId = folderId,
            name = name,
            size = size,
            mimeType = mimeType,
            uploaderEmail = uploaderEmail,
            uploaderName = uploaderName,
            uploadedAt = uploadedAt,
            visibility = visibility,
            selectedMembers = selectedMembers,
            downloadUrl = downloadUrl
        )
    }

    private fun saveState() {
        try {
            // Save Workspaces
            val wsList = _workspaces.value
            val wsSb = StringBuilder("[")
            wsList.forEachIndexed { idx, ws ->
                wsSb.append("""{"id":"${escapeJson(ws.id)}","name":"${escapeJson(ws.name)}","desc":"${escapeJson(ws.description)}","ownerEmail":"${escapeJson(ws.ownerEmail)}","role":"${ws.role.name}","memberCount":${ws.memberCount},"createdAt":${ws.createdAt}}""")
                if (idx < wsList.size - 1) wsSb.append(",")
            }
            wsSb.append("]")
            SessionManager.saveWorkspacesJson(wsSb.toString())

            // Save Folders
            val fldList = _folders.value
            val fldSb = StringBuilder("[")
            fldList.forEachIndexed { idx, fld ->
                val pId = fld.parentId?.let { "\"${escapeJson(it)}\"" } ?: "null"
                fldSb.append("""{"id":"${escapeJson(fld.id)}","workspaceId":"${escapeJson(fld.workspaceId)}","parentId":$pId,"name":"${escapeJson(fld.name)}","createdAt":${fld.createdAt}}""")
                if (idx < fldList.size - 1) fldSb.append(",")
            }
            fldSb.append("]")
            SessionManager.saveWorkspaceFoldersJson(fldSb.toString())

            // Save Files
            val fileList = _files.value
            val fileSb = StringBuilder("[")
            fileList.forEachIndexed { idx, f ->
                val fldId = f.folderId?.let { "\"${escapeJson(it)}\"" } ?: "null"
                val selMem = escapeJson(f.selectedMembers.joinToString(","))
                val dUrl = f.downloadUrl?.let { "\"${escapeJson(it)}\"" } ?: "null"
                fileSb.append("""{"id":"${escapeJson(f.id)}","workspaceId":"${escapeJson(f.workspaceId)}","folderId":$fldId,"name":"${escapeJson(f.name)}","size":${f.size},"mimeType":"${escapeJson(f.mimeType)}","uploaderEmail":"${escapeJson(f.uploaderEmail)}","uploaderName":"${escapeJson(f.uploaderName)}","uploadedAt":${f.uploadedAt},"visibility":"${f.visibility.name}","selectedMembers":"$selMem","downloadUrl":$dUrl}""")
                if (idx < fileList.size - 1) fileSb.append(",")
            }
            fileSb.append("]")
            SessionManager.saveWorkspaceFilesJson(fileSb.toString())
        } catch (_: Exception) {}
    }

    private fun loadSavedState() {
        try {
            val wsJson = SessionManager.getSavedWorkspacesJson()
            if (!wsJson.isNullOrBlank()) {
                val loaded = parseJsonObjects(wsJson).mapNotNull { mapToWorkspaceItem(it) }
                if (loaded.isNotEmpty()) {
                    _workspaces.value = loaded
                    _selectedWorkspace.value = loaded.firstOrNull()
                }
            }

            val fldJson = SessionManager.getSavedWorkspaceFoldersJson()
            if (!fldJson.isNullOrBlank()) {
                val loaded = parseJsonObjects(fldJson).mapNotNull { mapToWorkspaceFolder(it) }
                _folders.value = loaded
            }

            val filesJson = SessionManager.getSavedWorkspaceFilesJson()
            if (!filesJson.isNullOrBlank()) {
                val loaded = parseJsonObjects(filesJson).mapNotNull { mapToWorkspaceFile(it) }
                _files.value = loaded
            }
        } catch (_: Exception) {}
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
        saveState()
        return ws
    }

    suspend fun joinWorkspaceByCode(code: String): Boolean {
        val clean = code.trim().replace(" ", "")
        if (clean.length != 6) return false
        val myEmail = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
        val myName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())

        // 1. Check local session cache (same device or local test)
        val local = localWorkspaceInvites[clean]
        if (local != null) {
            val updatedCount = local.memberCount + 1
            val joined = WorkspaceItem(
                id = local.id,
                name = local.name, // The owner's actual workspace name!
                description = local.description.ifBlank { "Davet kodu ile katıldınız" },
                ownerEmail = local.ownerEmail,
                role = WorkspaceRole.MEMBER,
                memberCount = updatedCount
            )
            val withoutExisting = _workspaces.value.filter { it.id != joined.id }
            _workspaces.value = listOf(joined) + withoutExisting
            _selectedWorkspace.value = joined
            saveState()
            return true
        }

        // 2. Query Supabase backend for the invite
        try {
            val remote = net.syncswift.SupabaseNetwork.queryWorkspaceInvite(clean)
            if (remote != null) {
                val wsName = remote["name"]?.ifBlank { null } ?: "Çalışma Alanı"
                val wsId = remote["workspaceId"]?.ifBlank { null } ?: "ws_code_$clean"
                val wsDesc = remote["desc"] ?: ""
                val ownerEmail = remote["ownerEmail"]?.ifBlank { null } ?: "owner@syncswift.net"
                val memberCount = (remote["memberCount"]?.toIntOrNull() ?: 1) + 1

                val joined = WorkspaceItem(
                    id = wsId,
                    name = wsName, // The owner's real workspace name!
                    description = wsDesc.ifBlank { "Davet kodu ile katıldınız" },
                    ownerEmail = ownerEmail,
                    role = WorkspaceRole.MEMBER,
                    memberCount = memberCount
                )

                // Notify owner via Supabase so owner's member count and list updates
                scope.launch {
                    try {
                        net.syncswift.SupabaseNetwork.notifyWorkspaceJoined(
                            code = clean,
                            workspaceId = wsId,
                            joinerEmail = myEmail,
                            joinerName = myName
                        )
                    } catch (_: Exception) {}
                }

                val withoutExisting = _workspaces.value.filter { it.id != joined.id }
                _workspaces.value = listOf(joined) + withoutExisting
                _selectedWorkspace.value = joined
                saveState()
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun resetForLogout() {
        _folders.value = emptyList()
        _files.value = emptyList()
        _members.value = emptyList()
        _deviceJobs.value = emptyList()
        val defaultWs = WorkspaceItem(
            id = "ws_primary",
            name = "Kişisel Kasa",
            description = "Şifreli bulut kasası ve cihazlarım",
            ownerEmail = "local@syncswift.net",
            role = WorkspaceRole.OWNER,
            memberCount = 1
        )
        _workspaces.value = listOf(defaultWs)
        _selectedWorkspace.value = defaultWs
        _currentFolderId.value = null
        saveState()
    }

    fun syncWorkspacesWithAccount(newEmail: String) {
        if (newEmail.isBlank()) return
        val updated = _workspaces.value.map { ws ->
            if (ws.ownerEmail == "local@syncswift.net" || ws.ownerEmail == "user@syncswift.net" || ws.ownerEmail.isBlank()) {
                ws.copy(ownerEmail = newEmail)
            } else ws
        }
        _workspaces.value = updated
        _selectedWorkspace.value = updated.find { it.id == _selectedWorkspace.value?.id } ?: updated.firstOrNull()
        saveState()
    }

    fun generateInviteCode(workspaceId: String): String {
        val ws = _workspaces.value.find { it.id == workspaceId } ?: _selectedWorkspace.value
        val code = Random.nextInt(100000, 999999).toString()
        if (ws != null) {
            localWorkspaceInvites[code] = ws
            scope.launch {
                try {
                    net.syncswift.SupabaseNetwork.publishWorkspaceInvite(
                        code = code,
                        workspaceId = ws.id,
                        workspaceName = ws.name,
                        workspaceDesc = ws.description,
                        ownerEmail = ws.ownerEmail,
                        memberCount = ws.memberCount
                    )
                } catch (_: Exception) {}
            }
        }
        return code
    }

    fun refreshWorkspaceMembers(workspaceId: String) {
        if (workspaceId.isBlank()) return
        scope.launch {
            try {
                val remoteMembers = net.syncswift.SupabaseNetwork.fetchWorkspaceMembers(workspaceId)
                if (remoteMembers.isNotEmpty()) {
                    val currentList = _members.value.toMutableList()
                    var added = false
                    remoteMembers.forEach { rm ->
                        val email = rm["email"] ?: ""
                        val name = rm["displayName"] ?: email
                        if (email.isNotBlank() && currentList.none { it.email == email }) {
                            currentList.add(
                                WorkspaceMember(
                                    id = "mem_${email.hashCode()}",
                                    email = email,
                                    displayName = name,
                                    role = WorkspaceRole.MEMBER
                                )
                            )
                            added = true
                        }
                    }
                    if (added) {
                        _members.value = currentList
                        val newCount = currentList.size
                        _workspaces.value = _workspaces.value.map { ws ->
                            if (ws.id == workspaceId) ws.copy(memberCount = maxOf(ws.memberCount, newCount)) else ws
                        }
                        if (_selectedWorkspace.value?.id == workspaceId) {
                            _selectedWorkspace.value = _selectedWorkspace.value?.copy(memberCount = maxOf(_selectedWorkspace.value?.memberCount ?: 1, newCount))
                        }
                        saveState()
                    }
                }
            } catch (_: Exception) {}
        }
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
        saveState()
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
        saveState()
    }

    fun updateFileVisibility(fileId: String, visibility: FileVisibility, selectedMembers: List<String> = emptyList()) {
        _files.value = _files.value.map {
            if (it.id == fileId) it.copy(visibility = visibility, selectedMembers = selectedMembers) else it
        }
        saveState()
    }

    fun deleteFile(fileId: String) {
        _files.value = _files.value.filter { it.id != fileId }
        saveState()
    }

    fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        _files.value = _files.value.filter { it.folderId != folderId }
        saveState()
    }

    fun updateWorkspace(workspaceId: String, name: String, description: String = "") {
        _workspaces.value = _workspaces.value.map { ws ->
            if (ws.id == workspaceId) {
                ws.copy(name = name.trim(), description = description.trim())
            } else ws
        }
        if (_selectedWorkspace.value?.id == workspaceId) {
            _selectedWorkspace.value = _workspaces.value.find { it.id == workspaceId }
        }
        saveState()
    }

    fun deleteWorkspace(workspaceId: String) {
        _workspaces.value = _workspaces.value.filter { it.id != workspaceId }
        _folders.value = _folders.value.filter { it.workspaceId != workspaceId }
        _files.value = _files.value.filter { it.workspaceId != workspaceId }
        if (_workspaces.value.isEmpty()) {
            val email = SessionManager.getLoggedInEmail() ?: "user@syncswift.net"
            val defaultWs = WorkspaceItem(
                id = "ws_${currentTimeMs()}",
                name = "Kişisel Kasa",
                description = "Şifreli bulut kasası ve cihazlarım",
                ownerEmail = email,
                role = WorkspaceRole.OWNER,
                memberCount = 1
            )
            _workspaces.value = listOf(defaultWs)
            _selectedWorkspace.value = defaultWs
        } else if (_selectedWorkspace.value?.id == workspaceId) {
            _selectedWorkspace.value = _workspaces.value.firstOrNull()
        }
        saveState()
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
