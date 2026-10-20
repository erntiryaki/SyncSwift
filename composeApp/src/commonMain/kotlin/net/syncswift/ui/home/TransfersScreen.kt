package net.syncswift.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.transfer.TransferEngine
import net.syncswift.transfer.TransferItem
import net.syncswift.transfer.TransferStatus
import net.syncswift.ui.theme.*

@Composable
fun TransfersScreen(
    copy: Copy
) {
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: Received, 2: Sent
    val scrollState = rememberScrollState()
    val allTransfers by TransferEngine.transfers.collectAsState()

    val filteredList = remember(allTransfers, selectedFilter) {
        when (selectedFilter) {
            1 -> allTransfers.filter { !it.isSent }
            2 -> allTransfers.filter { it.isSent }
            else -> allTransfers
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.transfersTab,
                color = Cloud,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Filter Pills (Horizontally scrollable to ensure no text wraps or clips on any screen width)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                title = copy.viewAll,
                selected = selectedFilter == 0,
                onClick = { selectedFilter = 0 }
            )
            FilterPill(
                title = copy.transfersReceived,
                selected = selectedFilter == 1,
                onClick = { selectedFilter = 1 }
            )
            FilterPill(
                title = copy.transfersSent,
                selected = selectedFilter == 2,
                onClick = { selectedFilter = 2 }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Transfers List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(InputBg),
                        contentAlignment = Alignment.Center
                    ) {
                        TransferVectorIcon(Modifier.size(24.dp), color = Mist)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = copy.noTransfersYet,
                        color = Mist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredList.forEach { item ->
                    TransferItemDetailCard(item, copy)
                }
            }
        }

            Spacer(Modifier.height(24.dp))
        }

    }
}

@Composable
private fun FilterPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) PillButtonBg else CardSurface,
        border = BorderStroke(1.dp, if (selected) PillButtonBorder else InputBorder.copy(alpha = 0.4f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            color = if (selected) PillButtonText else Mist,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun TransferItemDetailCard(
    item: TransferItem,
    copy: Copy
) {
    val badge = when {
        item.mimeType.contains("pdf", ignoreCase = true) -> "PDF"
        item.mimeType.contains("image", ignoreCase = true) || item.fileName.endsWith(".jpg") || item.fileName.endsWith(".png") -> "IMG"
        item.mimeType.contains("video", ignoreCase = true) || item.fileName.endsWith(".mp4") || item.fileName.endsWith(".mov") -> "VID"
        item.mimeType.contains("zip", ignoreCase = true) -> "ZIP"
        item.mimeType == "text/plain" -> "TEXT"
        else -> "FILE"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFailed = item.status == TransferStatus.FAILED || item.status == TransferStatus.CANCELLED
                val statusColor = if (isFailed) ErrorRed else SuccessGreen

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isSent) {
                        OutgoingArrowIcon(Modifier.size(20.dp), color = statusColor)
                    } else {
                        IncomingArrowIcon(Modifier.size(20.dp), color = statusColor)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        color = Cloud,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    val peer = if (item.peerName.isNotBlank()) net.syncswift.discovery.cleanDeviceDisplayName(item.peerName) else if (item.isSent) copy.transfersSent else copy.transfersReceived
                    val statusText = when (item.status) {
                        TransferStatus.COMPLETED -> "${item.formattedSize} · $peer"
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> "${item.formattedSize} · ${copy.transferFailedStatus}"
                        TransferStatus.IN_PROGRESS -> "${item.formattedSize} · ${(item.progress * 100).toInt()}%"
                        else -> item.formattedSize
                    }
                    Text(
                        text = statusText,
                        color = if (isFailed) ErrorRed else Mist,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Status Indicator: Animated Check for Success, Cross for Failure, Spinning Progress for In Progress
                when (item.status) {
                    TransferStatus.IN_PROGRESS -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Blue,
                            strokeWidth = 2.5.dp
                        )
                    }
                    TransferStatus.COMPLETED -> {
                        AnimatedCheckCircleIcon(
                            modifier = Modifier.size(32.dp),
                            circleColor = SuccessGreen,
                            checkColor = Color.White
                        )
                    }
                    TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ErrorRed.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CrossVectorIcon(Modifier.size(16.dp), color = ErrorRed)
                        }
                    }
                    else -> {}
                }
            }

            if (item.status == TransferStatus.IN_PROGRESS) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Blue,
                    trackColor = InputBg
                )
            }

            val isClipboard = item.mimeType == "text/plain" ||
                    item.fileName.startsWith(copy.clipboard) ||
                    item.fileName.startsWith("Pano") ||
                    item.fileName.startsWith("Clipboard")

            if (item.status == TransferStatus.COMPLETED && !item.isSent && !isClipboard) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Dosya Konumunu Gör
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable {
                                net.syncswift.showFileLocation(item.localFilePath, item.fileName)
                            },
                        shape = RoundedCornerShape(19.dp),
                        color = InputBg,
                        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FolderOutlineIcon(Modifier.size(16.dp), color = Mist)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = copy.showFileLocationAction,
                                color = Cloud,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Aç
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable {
                                net.syncswift.openFile(item.localFilePath, item.fileName, item.mimeType)
                            },
                        shape = RoundedCornerShape(19.dp),
                        color = PillButtonBg,
                        border = BorderStroke(1.dp, PillButtonBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OpenExternalIcon(Modifier.size(15.dp), color = PillButtonText)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = copy.openFileAction,
                                color = PillButtonText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

