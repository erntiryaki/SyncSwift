package net.syncswift.ui.clipboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.clipboard.ClipType
import net.syncswift.clipboard.ClipboardHistoryManager
import net.syncswift.clipboard.ClipboardItem
import net.syncswift.clipboard.SmartAction
import net.syncswift.ui.theme.*

@Composable
fun ClipboardHistoryScreen(
    copy: Copy,
    onSendToDevice: (String) -> Unit
) {
    val clips by ClipboardHistoryManager.clips.collectAsState()
    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: Favorites, 2: URLs, 3: Texts
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredClips = remember(clips, searchQuery, selectedFilter) {
        clips.filter { item ->
            val matchesFilter = when (selectedFilter) {
                1 -> item.isFavorite
                2 -> item.type == ClipType.URL
                3 -> item.type == ClipType.TEXT || item.type == ClipType.CODE
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.text.contains(searchQuery, ignoreCase = true) ||
                    (item.label?.contains(searchQuery, ignoreCase = true) == true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = copy.clipboardHistoryTab,
                    color = Cloud,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Template Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Blue.copy(alpha = 0.15f))
                            .clickable { showAddDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    // Clear History Button
                    if (clips.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(InputBg)
                                .clickable { showClearConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            TrashIcon(Modifier.size(16.dp), color = Mist)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // SEARCH BAR
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchVectorIcon(Modifier.size(18.dp), color = Mist)
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = copy.searchClipboardHint,
                                color = Mist.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Cloud, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Blue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            CrossVectorIcon(Modifier.size(12.dp), color = Mist)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // FILTER PILLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    title = copy.allClips,
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 }
                )
                FilterChip(
                    title = copy.favoriteClips,
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 }
                )
                FilterChip(
                    title = copy.urlClips,
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 }
                )
                FilterChip(
                    title = copy.textClips,
                    selected = selectedFilter == 3,
                    onClick = { selectedFilter = 3 }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Free plan retention notice
            if (!isPro) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = InputBg.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                                net.syncswift.subscription.PaywallReason.CLIPBOARD_SYNC
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = copy.clipboardFreeVsPro,
                            color = Mist,
                            fontSize = 12.sp
                        )
                        Text(
                            text = copy.unlockWithPro,
                            color = Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // LIST
            if (filteredClips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(InputBg),
                            contentAlignment = Alignment.Center
                        ) {
                            ClipboardVectorIcon(Modifier.size(22.dp), color = Mist)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = copy.noClipsFound,
                            color = Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(filteredClips, key = { it.id }) { item ->
                        ClipboardItemCard(
                            item = item,
                            copy = copy,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(item.text))
                                showNativeToast(copy.clipboardCopiedSuccess)
                            },
                            onToggleFav = {
                                ClipboardHistoryManager.toggleFavorite(item.id)
                            },
                            onDelete = {
                                ClipboardHistoryManager.deleteClip(item.id)
                            },
                            onSendToDevice = {
                                onSendToDevice(item.text)
                            },
                            onOpenUrl = { url ->
                                try {
                                    uriHandler.openUri(url)
                                } catch (_: Exception) {}
                            },
                            onCallPhone = { phone ->
                                try {
                                    uriHandler.openUri("tel:$phone")
                                } catch (_: Exception) {}
                            },
                            onOpenWhatsApp = { phone ->
                                try {
                                    uriHandler.openUri("https://wa.me/$phone")
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }

    // ADD TEMPLATE MODAL
    if (showAddDialog) {
        AddTemplateDialog(
            copy = copy,
            onDismiss = { showAddDialog = false },
            onAdd = { label, text ->
                ClipboardHistoryManager.addClip(
                    text = text,
                    isFavorite = true,
                    label = label.takeIf { it.isNotBlank() }
                )
                showAddDialog = false
            }
        )
    }

    // CLEAR HISTORY CONFIRM
    if (showClearConfirm) {
        ClearHistoryDialog(
            copy = copy,
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                ClipboardHistoryManager.clearHistory(keepFavorites = true)
                showClearConfirm = false
            }
        )
    }
}

@Composable
private fun FilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PillButtonBg else CardSurface,
        border = BorderStroke(1.dp, if (selected) PillButtonBorder else InputBorder.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            color = if (selected) PillButtonText else Mist,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ClipboardItemCard(
    item: ClipboardItem,
    copy: Copy,
    onCopy: () -> Unit,
    onToggleFav: () -> Unit,
    onDelete: () -> Unit,
    onSendToDevice: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, if (item.isFavorite) Blue.copy(alpha = 0.4f) else InputBorder.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Type badge & Label + Favorite Star + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type Badge
                    val (badgeText, badgeColor) = when (item.type) {
                        ClipType.URL -> "URL" to Blue
                        ClipType.PHONE -> "TEL" to SuccessGreen
                        ClipType.COLOR -> "COLOR" to Color(0xFFF59E0B)
                        ClipType.CODE -> "CODE" to Color(0xFF8B5CF6)
                        else -> "TEXT" to Mist
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.label != null) {
                        Text(
                            text = item.label,
                            color = Cloud,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Favorite Toggle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onToggleFav() },
                        contentAlignment = Alignment.Center
                    ) {
                        StarVectorIcon(
                            Modifier.size(16.dp),
                            color = if (item.isFavorite) Color(0xFFF59E0B) else Mist.copy(alpha = 0.6f),
                            filled = item.isFavorite
                        )
                    }

                    // Delete
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        TrashIcon(Modifier.size(15.dp), color = Mist.copy(alpha = 0.6f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Text Content
            Text(
                text = item.text,
                color = Cloud,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Smart Action specifics
            when (val act = item.smartAction) {
                is SmartAction.ColorAction -> {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(act.colorInfo.r, act.colorInfo.g, act.colorInfo.b))
                                .border(1.dp, InputBorder, RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = "RGB(${act.colorInfo.r}, ${act.colorInfo.g}, ${act.colorInfo.b})",
                            color = Mist,
                            fontSize = 12.sp
                        )
                    }
                }
                is SmartAction.CodeAction -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = copy.linesOfCode.replace("%d", "${act.lineCount}"),
                        color = Mist,
                        fontSize = 11.sp
                    )
                }
                else -> {}
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = InputBg,
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onCopy() }
                ) {
                    Text(
                        text = copy.copyToClipboard,
                        color = Cloud,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Send to Device Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Blue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Blue.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onSendToDevice() }
                ) {
                    Text(
                        text = copy.send,
                        color = Blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Smart Action Button
                when (val act = item.smartAction) {
                    is SmartAction.OpenUrl -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onOpenUrl(act.url) }
                        ) {
                            Text(
                                text = copy.openInBrowser,
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    is SmartAction.PhoneAction -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onCallPhone(act.number) }
                        ) {
                            Text(
                                text = copy.callPhoneNumber,
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onOpenWhatsApp(act.number) }
                        ) {
                            Text(
                                text = copy.openWhatsApp,
                                color = Color(0xFF25D366),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTemplateDialog(
    copy: Copy,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var labelInput by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = copy.addQuickTemplate,
                color = Cloud,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(18.dp))

            // Label field
            Text(
                text = copy.clipboardTagLabel,
                color = Cloud,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                textStyle = TextStyle(color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                singleLine = true,
                cursorBrush = SolidColor(Blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(InputBg)
                    .border(1.2.dp, InputBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (labelInput.isEmpty()) Text(copy.clipboardTagLabel, color = Mist, fontSize = 15.sp)
                    inner()
                }
            )

            Spacer(Modifier.height(16.dp))

            // Text field
            Text(
                text = copy.clipboardContentLabel,
                color = Cloud,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = textInput,
                onValueChange = { textInput = it },
                textStyle = TextStyle(color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InputBg)
                    .border(1.2.dp, InputBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (textInput.isEmpty()) Text(copy.clipboardContentLabel, color = Mist, fontSize = 15.sp)
                    inner()
                }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (textInput.isNotBlank()) onAdd(labelInput, textInput)
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    disabledContainerColor = InputBorder
                )
            ) {
                Text(
                    text = copy.accept,
                    color = if (textInput.isNotBlank()) Color.White else Mist,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = copy.decline,
                    color = Mist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearHistoryDialog(
    copy: Copy,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                        .background(ErrorRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    WarningVectorIcon(Modifier.size(24.dp), color = ErrorRed)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = copy.clearHistory,
                        color = Cloud,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = copy.confirmClearHistory,
                        color = Mist,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text(
                    text = copy.clearHistory,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = copy.decline,
                    color = Mist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
