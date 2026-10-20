package net.syncswift.ui.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import net.syncswift.ui.theme.*
import net.syncswift.workspace.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    copy: Copy,
    onUploadFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
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

    Box(modifier = modifier.fillMaxSize().background(Night)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // TOP HEADER: Consistent with other screens
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedWorkspace?.name ?: copy.workspacesTitle,
                            color = Cloud,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.6).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = selectedWorkspace?.description?.ifBlank { copy.workspacesSubtitle } ?: copy.workspacesSubtitle,
                            fontSize = 12.5.sp,
                            color = Mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Members pill
                    Surface(
                        shape = CircleShape,
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder),
                        modifier = Modifier.clickable { showMembersDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UsersVectorIcon(Modifier.size(15.dp), color = Mist)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${selectedWorkspace?.memberCount ?: 1}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cloud
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // New Workspace button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .border(1.dp, InputBorder, CircleShape)
                            .clickable { showCreateWorkspaceDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AddPlusVectorIcon(Modifier.size(16.dp), color = primaryBlue)
                    }
                }

                // Breadcrumb folder indicator if inside folder
                if (currentFolderId != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder),
                        modifier = Modifier.clickable { WorkspaceManager.navigateIntoFolder(null) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            ChevronLeftIcon(Modifier.size(14.dp), color = primaryBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = allFolders.find { it.id == currentFolderId }?.name ?: "Klasör",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryBlue
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }

            // CONTENT: Folders & Files
            if (currentFolders.isEmpty() && currentFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(primaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                FolderVectorIcon(Modifier.size(36.dp), color = primaryBlue)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = copy.emptyFolder,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cloud
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = copy.emptyFolderDesc,
                                fontSize = 13.sp,
                                color = Mist,
                                lineHeight = 19.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Folders
                    items(currentFolders) { folder ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { WorkspaceManager.navigateIntoFolder(folder.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, InputBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(primaryBlue.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FolderVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Cloud
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = copy.newFolder,
                                        fontSize = 11.5.sp,
                                        color = Mist
                                    )
                                }
                                ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                            }
                        }
                    }

                    // Files
                    items(currentFiles) { file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedFileForAction = file },
                            shape = RoundedCornerShape(16.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, InputBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isPdf = file.name.endsWith(".pdf", ignoreCase = true) || file.mimeType.contains("pdf", ignoreCase = true)
                                val isZip = file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".rar", ignoreCase = true) || file.mimeType.contains("zip", ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isPdf) Color(0xFFEF4444).copy(alpha = 0.14f) else (if (isZip) Color(0xFFF59E0B).copy(alpha = 0.14f) else primaryBlue.copy(alpha = 0.14f))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPdf) {
                                        FilePdfIcon(Modifier.size(28.dp))
                                    } else if (isZip) {
                                        FileZipIcon(Modifier.size(28.dp))
                                    } else {
                                        DocumentVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Cloud,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = net.syncswift.transfer.formatBytes(file.size),
                                            fontSize = 11.5.sp,
                                            color = Mist
                                        )
                                        Text(
                                            text = " • " + copy.uploadedBy.replace("%s", file.uploaderName),
                                            fontSize = 11.5.sp,
                                            color = Mist,
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
                                    color = visColor.copy(alpha = 0.14f)
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

        // Floating Action Button
        FloatingActionButton(
            onClick = { showPlusActionMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = primaryBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            AddPlusVectorIcon(Modifier.size(20.dp), color = Color.White)
        }
    }

    // MODAL 1: + ACTION MENU
    if (showPlusActionMenu) {
        ModalBottomSheet(
            onDismissRequest = { showPlusActionMenu = false },
            containerColor = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = copy.filesAndFolders,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cloud
                )
                Spacer(Modifier.height(16.dp))

                // 1. Bu cihazdan yükle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showPlusActionMenu = false
                            onUploadFileClick?.invoke()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SmartphoneVectorIcon(Modifier.size(22.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(copy.uploadFromThisDevice, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Cloud)
                }

                // 2. Diğer cihazımdan getir (Faz 6: Görev Devretme)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showPlusActionMenu = false
                            showTaskDelegationDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DesktopVectorIcon(Modifier.size(22.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(copy.fetchFromOtherDevice, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Cloud)
                        Text(
                            text = copy.delegateTaskSubtitle,
                            fontSize = 11.5.sp,
                            color = Mist
                        )
                    }
                }

                // 3. Yeni klasör
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showPlusActionMenu = false
                            showNewFolderDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        FolderVectorIcon(Modifier.size(22.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(copy.newFolder, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Cloud)
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
            title = { Text(copy.delegateTaskTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Cloud) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(copy.delegateTaskSubtitle, fontSize = 13.sp, color = Mist)
                    Spacer(Modifier.height(14.dp))

                    Text(copy.selectTargetDesktop, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = InputBg,
                        border = BorderStroke(1.dp, InputBorder)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DesktopVectorIcon(Modifier.size(18.dp), color = primaryBlue)
                            Spacer(Modifier.width(10.dp))
                            Text("Eren Windows PC (Ofis)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Cloud)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(copy.allowedFolders, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Documents", "Downloads", "MatMatbaa").forEach { fld ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedFolder == fld) primaryBlue.copy(alpha = 0.15f) else InputBg,
                                border = if (selectedFolder == fld) BorderStroke(1.dp, primaryBlue) else BorderStroke(1.dp, InputBorder),
                                modifier = Modifier.clickable { selectedFolder = fld }
                            ) {
                                Text(
                                    text = fld,
                                    fontSize = 12.sp,
                                    color = if (selectedFolder == fld) primaryBlue else Cloud,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(copy.selectFileFromRemote, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = remoteFileName,
                        onValueChange = { remoteFileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputBg)
                            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = Cloud, fontSize = 13.sp),
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
            title = { Text(copy.newFolder, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Cloud) },
            text = {
                BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputBg)
                        .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    textStyle = TextStyle(color = Cloud, fontSize = 14.sp),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { inner ->
                        if (newFolderName.isEmpty()) Text(copy.folderName, color = Mist, fontSize = 14.sp)
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
            containerColor = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(file.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Cloud, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(net.syncswift.transfer.formatBytes(file.size), fontSize = 12.sp, color = Mist)

                Spacer(Modifier.height(16.dp))
                Text(copy.fileVisibilityTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
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
                        Text(label, fontSize = 14.sp, color = Cloud)
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
                    Text(copy.deleteFile, color = Color.White)
                }
            }
        }
    }

    // MODAL 5: ÜYELER VE DAVET KODU
    if (showMembersDialog) {
        val wsId = selectedWorkspace?.id ?: ""
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = { Text(copy.workspaceMembers, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Cloud) },
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
                    Text(copy.workspaceMembers + " (${members.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
                    Spacer(Modifier.height(8.dp))
                    members.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserIcon(Modifier.size(16.dp), color = primaryBlue)
                            Spacer(Modifier.width(10.dp))
                            Text(m.displayName, fontSize = 13.sp, color = Cloud, modifier = Modifier.weight(1f))
                            Text(
                                text = if (m.role == WorkspaceRole.OWNER) copy.roleOwner else copy.roleMember,
                                fontSize = 11.sp,
                                color = Mist
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
            title = { Text(copy.createWorkspace, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Cloud) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = newWsName,
                        onValueChange = { newWsName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputBg)
                            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        textStyle = TextStyle(color = Cloud, fontSize = 14.sp),
                        cursorBrush = SolidColor(primaryBlue),
                        decorationBox = { inner ->
                            if (newWsName.isEmpty()) Text(copy.workspaceName, color = Mist, fontSize = 14.sp)
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
                            .background(InputBg)
                            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        textStyle = TextStyle(color = Cloud, fontSize = 14.sp),
                        cursorBrush = SolidColor(primaryBlue),
                        decorationBox = { inner ->
                            if (newWsDesc.isEmpty()) Text(copy.workspaceDesc, color = Mist, fontSize = 14.sp)
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
