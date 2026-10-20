package net.syncswift.ui.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.theme.LocalSyncSwiftColors
import net.syncswift.workspace.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    copy: Copy,
    onUploadFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val bg = if (isDark) Color(0xFF090D14) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF131B28) else Color(0xFFFFFFFF)
    val inputBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val borderColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryBlue = LocalSyncSwiftColors.current.blue
    val clipboardManager = LocalClipboardManager.current

    val workspaces by WorkspaceManager.workspaces.collectAsState()
    val selectedWorkspace by WorkspaceManager.selectedWorkspace.collectAsState()
    val currentFolderId by WorkspaceManager.currentFolderId.collectAsState()
    val allFolders by WorkspaceManager.folders.collectAsState()
    val allFiles by WorkspaceManager.files.collectAsState()
    val members by WorkspaceManager.members.collectAsState()
    val deviceJobs by WorkspaceManager.deviceJobs.collectAsState()

    // Modals
    var showCreateWorkspaceDialog by remember { mutableStateOf(false) }
    var showJoinWorkspaceDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showPlusActionMenu by remember { mutableStateOf(false) }
    var showTaskDelegationDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<WorkspaceFile?>(null) }
    var generatedInviteCode by remember { mutableStateOf<String?>(null) }

    // Filter items in current workspace & folder
    val currentFolders = remember(allFolders, selectedWorkspace, currentFolderId) {
        val wsId = selectedWorkspace?.id ?: ""
        allFolders.filter { it.workspaceId == wsId && it.parentId == currentFolderId }
    }
    val currentFiles = remember(allFiles, selectedWorkspace, currentFolderId) {
        val wsId = selectedWorkspace?.id ?: ""
        allFiles.filter { it.workspaceId == wsId && it.folderId == currentFolderId }
    }

    Scaffold(
        containerColor = bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPlusActionMenu = true },
                containerColor = primaryBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // TOP HEADER: Workspace title & Member avatars
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardBg,
                border = BorderStroke(0.5.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedWorkspace?.name ?: copy.workspacesTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = selectedWorkspace?.description?.ifBlank { copy.workspacesSubtitle } ?: copy.workspacesSubtitle,
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                maxLines = 1
                            )
                        }

                        // Members pill & invite
                        Surface(
                            shape = CircleShape,
                            color = inputBg,
                            modifier = Modifier.clickable { showMembersDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👥", fontSize = 12.sp)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "${selectedWorkspace?.memberCount ?: 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A)
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Switcher button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(inputBg)
                                .clickable { showCreateWorkspaceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("＋", fontSize = 18.sp, color = primaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Breadcrumb folder indicator if inside folder
                    if (currentFolderId != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(inputBg)
                                .clickable { WorkspaceManager.navigateIntoFolder(null) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("←", fontSize = 14.sp, color = primaryBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = allFolders.find { it.id == currentFolderId }?.name ?: "Klasör",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryBlue
                            )
                        }
                    }
                }
            }

            // CONTENT: Folders & Files
            if (currentFolders.isEmpty() && currentFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📁", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = copy.emptyFolder,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = copy.emptyFolderDesc,
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Folders
                    items(currentFolders) { folder ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { WorkspaceManager.navigateIntoFolder(folder.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = copy.newFolder,
                                        fontSize = 11.sp,
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                }
                                Text("›", fontSize = 20.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
                            }
                        }
                    }

                    // Files
                    items(currentFiles) { file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFileForAction = file },
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📄", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = net.syncswift.transfer.formatBytes(file.size),
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                        )
                                        Text(
                                            text = " • " + copy.uploadedBy.replace("%s", file.uploaderName),
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Visibility Badge (Faz 5: Dosya Bazlı Görünürlük)
                                val (visLabel, visColor) = when (file.visibility) {
                                    FileVisibility.ONLY_ME -> copy.visibilityOnlyMe to Color(0xFFEF4444)
                                    FileVisibility.SELECTED_MEMBERS -> copy.visibilitySelected to Color(0xFFF59E0B)
                                    FileVisibility.ALL_MEMBERS -> copy.visibilityAllMembers to Color(0xFF10B981)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = visColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = visLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = visColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MODAL 1: + ACTION MENU
    if (showPlusActionMenu) {
        ModalBottomSheet(
            onDismissRequest = { showPlusActionMenu = false },
            containerColor = cardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = copy.filesAndFolders,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
                Spacer(Modifier.height(16.dp))

                // 1. Bu cihazdan yükle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusActionMenu = false
                            onUploadFileClick?.invoke()
                            // Sample file upload trigger
                            WorkspaceManager.uploadFile("Proje_Raporu.pdf", 2450000L, "application/pdf")
                            showNativeToast(copy.fileUploadedToast)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📱", fontSize = 22.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(copy.uploadFromThisDevice, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // 2. Diğer cihazımdan getir (Faz 6: Görev Devretme)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusActionMenu = false
                            showTaskDelegationDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💻", fontSize = 22.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(copy.fetchFromOtherDevice, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = copy.delegateTaskSubtitle,
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }

                // 3. Yeni klasör
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusActionMenu = false
                            showNewFolderDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁", fontSize = 22.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(copy.newFolder, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // MODAL 2: GÖREV DEVRETME (Faz 6 - Uzaktan Güvenli Komut)
    if (showTaskDelegationDialog) {
        var remoteFileName by remember { mutableStateOf("Baskı_Tasarım.pdf") }
        var selectedFolder by remember { mutableStateOf("MatMatbaa") }

        AlertDialog(
            onDismissRequest = { showTaskDelegationDialog = false },
            title = { Text(copy.delegateTaskTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(copy.delegateTaskSubtitle, fontSize = 13.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    Spacer(Modifier.height(14.dp))

                    Text(copy.selectTargetDesktop, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = inputBg
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🖥️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Eren Windows PC (Ofis)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(copy.allowedFolders, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Documents", "Downloads", "MatMatbaa").forEach { fld ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedFolder == fld) primaryBlue.copy(alpha = 0.15f) else inputBg,
                                border = if (selectedFolder == fld) BorderStroke(1.dp, primaryBlue) else null,
                                modifier = Modifier.clickable { selectedFolder = fld }
                            ) {
                                Text(
                                    text = fld,
                                    fontSize = 12.sp,
                                    color = if (selectedFolder == fld) primaryBlue else (if (isDark) Color.White else Color(0xFF0F172A)),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(copy.selectFileFromRemote, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = remoteFileName,
                        onValueChange = { remoteFileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .padding(10.dp),
                        textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 13.sp),
                        cursorBrush = SolidColor(primaryBlue)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        WorkspaceManager.queueRemoteJob(
                            targetDeviceId = "pc_eren_1",
                            targetDeviceName = "Eren Windows PC",
                            folderPath = selectedFolder,
                            fileName = remoteFileName
                        )
                        showTaskDelegationDialog = false
                        showNativeToast(copy.taskQueued)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(copy.startRemoteUpload)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTaskDelegationDialog = false }) {
                    Text(copy.otpCancelAction)
                }
            }
        )
    }

    // MODAL 3: YENİ KLASÖR OLUŞTURMA
    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(copy.newFolder, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(inputBg)
                        .padding(12.dp),
                    textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 14.sp),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { inner ->
                        if (newFolderName.isEmpty()) Text(copy.folderName, color = Color.Gray, fontSize = 14.sp)
                        inner()
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            WorkspaceManager.createFolder(newFolderName)
                            showNewFolderDialog = false
                            showNativeToast(copy.folderCreatedToast)
                        }
                    },
                    enabled = newFolderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(copy.continueText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text(copy.otpCancelAction) }
            }
        )
    }

    // MODAL 4: DOSYA AKSİYONLARI VE GÖRÜNÜRLÜK (Faz 5)
    if (selectedFileForAction != null) {
        val file = selectedFileForAction!!
        ModalBottomSheet(
            onDismissRequest = { selectedFileForAction = null },
            containerColor = cardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(file.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(net.syncswift.transfer.formatBytes(file.size), fontSize = 12.sp, color = Color.Gray)

                Spacer(Modifier.height(16.dp))
                Text(copy.fileVisibilityTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                // 3 Visibility Choices
                listOf(
                    FileVisibility.ONLY_ME to copy.visibilityOnlyMe,
                    FileVisibility.SELECTED_MEMBERS to copy.visibilitySelected,
                    FileVisibility.ALL_MEMBERS to copy.visibilityAllMembers
                ).forEach { (vis, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                WorkspaceManager.updateFileVisibility(file.id, vis)
                                selectedFileForAction = file.copy(visibility = vis)
                                showNativeToast(copy.visibilityUpdatedToast)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = file.visibility == vis, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        WorkspaceManager.deleteFile(file.id)
                        selectedFileForAction = null
                        showNativeToast(copy.fileDeletedToast)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(copy.deleteFile)
                }
            }
        }
    }

    // MODAL 5: ÜYELER VE DAVET KODU
    if (showMembersDialog) {
        val wsId = selectedWorkspace?.id ?: ""
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = { Text(copy.workspaceMembers, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Invite Code generator
                    if (generatedInviteCode != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = primaryBlue.copy(alpha = 0.12f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(copy.inviteCode, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = primaryBlue)
                                Text(generatedInviteCode!!, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = primaryBlue)
                                Text(copy.copyCodeAction, fontSize = 12.sp, color = primaryBlue, modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(generatedInviteCode!!))
                                    showNativeToast(copy.codeCopiedToast)
                                })
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                generatedInviteCode = WorkspaceManager.generateInviteCode(wsId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                        ) {
                            Text(copy.inviteMember)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(copy.workspaceMembers + " (${members.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    members.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(m.displayName, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                text = if (m.role == WorkspaceRole.OWNER) copy.roleOwner else copy.roleMember,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembersDialog = false }) { Text(copy.understandAction) }
            }
        )
    }

    // MODAL 6: YENİ ÇALIŞMA ALANI OLUŞTURMA
    if (showCreateWorkspaceDialog) {
        var newWsName by remember { mutableStateOf("") }
        var newWsDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateWorkspaceDialog = false },
            title = { Text(copy.createWorkspace, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = newWsName,
                        onValueChange = { newWsName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .padding(12.dp),
                        textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 14.sp),
                        cursorBrush = SolidColor(primaryBlue),
                        decorationBox = { inner ->
                            if (newWsName.isEmpty()) Text(copy.workspaceName, color = Color.Gray, fontSize = 14.sp)
                            inner()
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = newWsDesc,
                        onValueChange = { newWsDesc = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .padding(12.dp),
                        textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF0F172A), fontSize = 14.sp),
                        cursorBrush = SolidColor(primaryBlue),
                        decorationBox = { inner ->
                            if (newWsDesc.isEmpty()) Text(copy.workspaceDesc, color = Color.Gray, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWsName.isNotBlank()) {
                            WorkspaceManager.createWorkspace(newWsName, newWsDesc)
                            showCreateWorkspaceDialog = false
                            showNativeToast(copy.workspaceCreatedToast)
                        }
                    },
                    enabled = newWsName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(copy.continueText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateWorkspaceDialog = false }) { Text(copy.otpCancelAction) }
            }
        )
    }
}
