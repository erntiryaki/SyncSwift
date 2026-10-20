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
    var showWorkspaceSwitcher by remember { mutableStateOf(false) }
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
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showWorkspaceSwitcher = true }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(primaryBlue.copy(alpha = 0.12f))
                                .border(1.dp, primaryBlue.copy(alpha = 0.28f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ChevronDownIcon(Modifier.size(13.dp), color = primaryBlue)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedWorkspace?.name ?: copy.workspacesTitle,
                                color = Cloud,
                                fontSize = 23.sp,
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

                    Spacer(Modifier.width(8.dp))

                    // Join Workspace with Code button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .border(1.dp, InputBorder, CircleShape)
                            .clickable { showJoinWorkspaceDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        KeyVectorIcon(Modifier.size(16.dp), color = primaryBlue)
                    }

                    Spacer(Modifier.width(8.dp))

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

                // Modern Breadcrumb Folder Back Navigation
                if (currentFolderId != null) {
                    val currentFolderObj = allFolders.find { it.id == currentFolderId }
                    val folderName = currentFolderObj?.name ?: "Klasör"
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, InputBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { WorkspaceManager.navigateIntoFolder(null) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(primaryBlue.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ChevronLeftIcon(Modifier.size(16.dp), color = primaryBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cloud,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "‹ " + (selectedWorkspace?.name ?: copy.workspacesTitle),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryBlue
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(InputBg),
                                contentAlignment = Alignment.Center
                            ) {
                                FolderVectorIcon(Modifier.size(18.dp), color = Mist)
                            }
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
                                text = if (currentFolderId == null) copy.emptyWorkspace else copy.emptyFolder,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cloud
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (currentFolderId == null) copy.emptyWorkspaceDesc else copy.emptyFolderDesc,
                                fontSize = 13.sp,
                                color = Mist,
                                lineHeight = 19.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (currentFolderId == null) {
                                Spacer(Modifier.height(18.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = primaryBlue,
                                    modifier = Modifier.clickable { showJoinWorkspaceDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        KeyVectorIcon(Modifier.size(15.dp), color = Color.White)
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            text = copy.joinWorkspace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
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
        WorkspaceModalBottomSheet(
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

                // 4. Koda göre çalışma alanına katıl
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showPlusActionMenu = false
                            showJoinWorkspaceDialog = true
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
                        KeyVectorIcon(Modifier.size(20.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(copy.joinWorkspace, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Cloud)
                }
            }
        }
    }

    // MODAL 2: GÖREV DEVRETME (Modern Bottom Sheet)
    if (showTaskDelegationDialog) {
        var remoteFileName by remember { mutableStateOf("Baskı_Tasarım.pdf") }
        var selectedFolder by remember { mutableStateOf("MatMatbaa") }

        WorkspaceModalBottomSheet(
            onDismissRequest = { showTaskDelegationDialog = false },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DesktopVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = copy.delegateTaskTitle,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = copy.delegateTaskSubtitle,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(copy.selectTargetDesktop, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cloud)
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = InputBg,
                    border = BorderStroke(1.dp, InputBorder)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopVectorIcon(Modifier.size(20.dp), color = primaryBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Eren Windows PC (Ofis)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Cloud)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(copy.allowedFolders, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cloud)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Documents", "Downloads", "MatMatbaa").forEach { fld ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedFolder == fld) primaryBlue.copy(alpha = 0.16f) else InputBg,
                            border = if (selectedFolder == fld) BorderStroke(1.2.dp, primaryBlue) else BorderStroke(1.dp, InputBorder),
                            modifier = Modifier.clickable { selectedFolder = fld }
                        ) {
                            Text(
                                text = fld,
                                fontSize = 13.sp,
                                fontWeight = if (selectedFolder == fld) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedFolder == fld) primaryBlue else Cloud,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(copy.selectFileFromRemote, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cloud)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = remoteFileName,
                    onValueChange = { remoteFileName = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputBg)
                        .border(1.2.dp, InputBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = TextStyle(color = Cloud, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(primaryBlue)
                )

                Spacer(Modifier.height(24.dp))

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(
                        text = copy.startRemoteUpload,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = { showTaskDelegationDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = copy.otpCancelAction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mist
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // MODAL 3: YENİ KLASÖR OLUŞTURMA (Modern Bottom Sheet)
    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        WorkspaceModalBottomSheet(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        FolderVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = copy.newFolder,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = selectedWorkspace?.name ?: copy.workspacesTitle,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                BasicTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputBg)
                        .border(1.2.dp, if (newFolderName.isNotBlank()) primaryBlue.copy(alpha = 0.5f) else InputBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = TextStyle(
                        color = Cloud,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { inner ->
                        if (newFolderName.isEmpty()) {
                            Text(copy.folderName, color = Mist, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                        }
                        inner()
                    }
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            WorkspaceManager.createFolder(newFolderName)
                            showNewFolderDialog = false
                            showNativeToast(copy.folderCreatedToast)
                        }
                    },
                    enabled = newFolderName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBlue,
                        disabledContainerColor = InputBorder
                    )
                ) {
                    Text(
                        text = copy.continueText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (newFolderName.isNotBlank()) Color.White else Mist
                    )
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = { showNewFolderDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = copy.otpCancelAction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mist
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // MODAL 4: DOSYA AKSİYONLARI VE GÖRÜNÜRLÜK (Faz 5)
    if (selectedFileForAction != null) {
        val file = selectedFileForAction!!
        WorkspaceModalBottomSheet(
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

    // MODAL 5: ÜYELER VE DAVET KODU (Modern Bottom Sheet)
    if (showMembersDialog) {
        val wsId = selectedWorkspace?.id ?: ""
        WorkspaceModalBottomSheet(
            onDismissRequest = { showMembersDialog = false },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        UsersVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = copy.workspaceMembers,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = selectedWorkspace?.name ?: copy.workspacesTitle,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Invite Code generator
                if (generatedInviteCode != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = primaryBlue.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(copy.inviteCode, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = primaryBlue)
                            Spacer(Modifier.height(6.dp))
                            Text(generatedInviteCode!!, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = primaryBlue, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = copy.copyCodeAction,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryBlue,
                                modifier = Modifier
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(generatedInviteCode!!))
                                        showNativeToast(copy.codeCopiedToast)
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            generatedInviteCode = WorkspaceManager.generateInviteCode(wsId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                    ) {
                        Text(
                            text = copy.inviteMember,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = copy.workspaceMembers + " (${members.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cloud
                )
                Spacer(Modifier.height(10.dp))
                members.forEach { m ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = InputBg
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserIcon(Modifier.size(18.dp), color = primaryBlue)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = m.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Cloud,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CardSurface
                            ) {
                                Text(
                                    text = if (m.role == WorkspaceRole.OWNER) copy.roleOwner else copy.roleMember,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (m.role == WorkspaceRole.OWNER) primaryBlue else Mist,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // MODAL: ÇALIŞMA ALANI DEĞİŞTİRİCİ (Workspace Switcher Bottom Sheet)
    if (showWorkspaceSwitcher) {
        WorkspaceModalBottomSheet(
            onDismissRequest = { showWorkspaceSwitcher = false },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronDownIcon(Modifier.size(20.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = copy.workspacesTitle,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = copy.workspacesSubtitle,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Workspaces List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(workspaces, key = { it.id }) { ws ->
                        val isSelected = ws.id == selectedWorkspace?.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) primaryBlue.copy(alpha = 0.14f) else Night,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) primaryBlue.copy(alpha = 0.6f) else InputBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    WorkspaceManager.selectWorkspace(ws)
                                    showWorkspaceSwitcher = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) primaryBlue else primaryBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ws.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = if (isSelected) Color.White else primaryBlue
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ws.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Cloud,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (ws.role == WorkspaceRole.OWNER) copy.roleOwner else copy.roleMember,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (ws.role == WorkspaceRole.OWNER) primaryBlue else Mist
                                        )
                                        Text(text = "•", fontSize = 11.sp, color = Mist)
                                        UsersVectorIcon(Modifier.size(12.dp), color = Mist)
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            text = "${ws.memberCount}",
                                            fontSize = 11.5.sp,
                                            color = Mist
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(primaryBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CheckIcon(Modifier.size(14.dp), color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions: Join with Code or Create New Workspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Night,
                        border = BorderStroke(1.dp, InputBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showWorkspaceSwitcher = false
                                showJoinWorkspaceDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = copy.joinWorkspace,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cloud
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = primaryBlue,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showWorkspaceSwitcher = false
                                showCreateWorkspaceDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AddPlusVectorIcon(Modifier.size(14.dp), color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = copy.createWorkspace,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }

    // MODAL: ÇALIŞMA ALANINA KOD İLE KATILMA (Join Workspace with Code Bottom Sheet)
    if (showJoinWorkspaceDialog) {
        var inviteCodeInput by remember { mutableStateOf("") }
        var joinError by remember { mutableStateOf<String?>(null) }

        WorkspaceModalBottomSheet(
            onDismissRequest = {
                showJoinWorkspaceDialog = false
                joinError = null
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        UsersVectorIcon(Modifier.size(22.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = copy.joinWorkspace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = copy.enterInviteCode,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                BasicTextField(
                    value = inviteCodeInput,
                    onValueChange = {
                        if (it.length <= 6 && it.all { ch -> ch.isDigit() || ch.isLetter() }) {
                            inviteCodeInput = it.uppercase()
                            joinError = null
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Cloud,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Night)
                                .border(1.dp, if (joinError != null) ErrorRed else InputBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (inviteCodeInput.isEmpty()) {
                                Text(
                                    text = "000000",
                                    color = Mist.copy(alpha = 0.4f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (joinError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = joinError ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (inviteCodeInput.length == 6) primaryBlue else primaryBlue.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = inviteCodeInput.length == 6) {
                            val joined = WorkspaceManager.joinWorkspaceByCode(inviteCodeInput.trim())
                            if (joined) {
                                showNativeToast(copy.workspaceJoinedToast)
                                showJoinWorkspaceDialog = false
                                inviteCodeInput = ""
                                joinError = null
                            } else {
                                joinError = "Geçersiz davet kodu (6 hane olmalıdır)"
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = copy.joinWorkspace,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showJoinWorkspaceDialog = false
                            joinError = null
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = copy.otpCancelAction,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // MODAL 6: YENİ ÇALIŞMA ALANI OLUŞTURMA (Modern Bottom Sheet)
    if (showCreateWorkspaceDialog) {
        var newWsName by remember { mutableStateOf("") }
        var newWsDesc by remember { mutableStateOf("") }

        WorkspaceModalBottomSheet(
            onDismissRequest = { showCreateWorkspaceDialog = false },
            containerColor = CardSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AddPlusVectorIcon(Modifier.size(24.dp), color = primaryBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = copy.createWorkspace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Cloud
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = copy.workspacesTitle,
                            fontSize = 12.5.sp,
                            color = Mist
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                BasicTextField(
                    value = newWsName,
                    onValueChange = { newWsName = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputBg)
                        .border(1.2.dp, if (newWsName.isNotBlank()) primaryBlue.copy(alpha = 0.5f) else InputBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = TextStyle(color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { inner ->
                        if (newWsName.isEmpty()) Text(copy.workspaceName, color = Mist, fontSize = 15.sp)
                        inner()
                    }
                )

                Spacer(Modifier.height(12.dp))

                BasicTextField(
                    value = newWsDesc,
                    onValueChange = { newWsDesc = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputBg)
                        .border(1.2.dp, InputBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = TextStyle(color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(primaryBlue),
                    decorationBox = { inner ->
                        if (newWsDesc.isEmpty()) Text(copy.workspaceDesc, color = Mist, fontSize = 15.sp)
                        inner()
                    }
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (newWsName.isNotBlank()) {
                            WorkspaceManager.createWorkspace(newWsName, newWsDesc)
                            showCreateWorkspaceDialog = false
                            showNativeToast(copy.workspaceCreatedToast)
                        }
                    },
                    enabled = newWsName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBlue,
                        disabledContainerColor = InputBorder
                    )
                ) {
                    Text(
                        text = copy.continueText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (newWsName.isNotBlank()) Color.White else Mist
                    )
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = { showCreateWorkspaceDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = copy.otpCancelAction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mist
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceModalBottomSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color = CardSurface,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DisposableEffect(Unit) {
        net.syncswift.ui.components.AdBannerManager.incrementModalCount()
        onDispose {
            net.syncswift.ui.components.AdBannerManager.decrementModalCount()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = containerColor,
        shape = shape,
        content = content
    )
}
