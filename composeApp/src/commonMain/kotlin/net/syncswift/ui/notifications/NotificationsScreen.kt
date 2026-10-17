package net.syncswift.ui.notifications

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.notification.NotificationHistoryManager
import net.syncswift.notification.NotificationItem
import net.syncswift.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun NotificationsScreen(
    copy: Copy,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val notifications by NotificationHistoryManager.notifications.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        NotificationHistoryManager.markAllRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(InputBg.copy(alpha = 0.8f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
                }
                Text(
                    text = copy.notificationsTitle,
                    color = Cloud,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
            }

            if (notifications.isNotEmpty()) {
                Text(
                    text = copy.clearAllNotifications,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            NotificationHistoryManager.clearAll()
                            showNativeToast(copy.clearAllNotifications)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

        if (notifications.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(InputBg.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        NotificationBellIcon(Modifier.size(34.dp), color = Mist.copy(alpha = 0.5f))
                    }
                    Text(
                        text = copy.noNotificationsYet,
                        color = Mist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Notifications List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = notifications,
                    key = { it.id }
                ) { item ->
                    SwipeableNotificationCard(
                        item = item,
                        copy = copy,
                        onDelete = {
                            NotificationHistoryManager.deleteNotification(item.id)
                            showNativeToast(copy.notificationDeleted)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeableNotificationCard(
    item: NotificationItem,
    copy: Copy,
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetXAnim = remember { Animatable(0f) }
    var cardWidthPx by remember { mutableStateOf(1000f) }
    val pendingPair by net.syncswift.connection.ConnectionManager.pendingPairRequest.collectAsState()
    val pendingTransfer by net.syncswift.connection.ConnectionManager.pendingTransferRequest.collectAsState()

    val handleItemClick = {
        when (item.type.lowercase()) {
            "pair" -> {
                if (pendingPair == null || (item.senderName.isNotBlank() && pendingPair?.senderName != item.senderName)) {
                    showNativeToast(copy.requestCancelledBySender)
                } else {
                    showNativeToast(copy.waitingForRecipientApproval)
                }
            }
            "file" -> {
                if (pendingTransfer == null) {
                    showNativeToast(copy.transferCancelledBySender)
                } else {
                    showNativeToast(copy.waitingForRecipientApproval)
                }
            }
            "clipboard" -> {
                showNativeToast(copy.clipboardCancelledBySender)
            }
            else -> {
                showNativeToast(item.body.ifBlank { item.title })
            }
        }
    }

    val iconColor = when (item.type.lowercase()) {
        "clipboard" -> Blue
        "file" -> SuccessGreen
        "pair" -> Color(0xFF8B5CF6)
        else -> Cloud
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Red background revealed on left swipe
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(ErrorRed)
                .padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            TrashIcon(Modifier.size(20.dp), color = Color.White)
        }

        // Foreground Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(100f) }
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .alpha(((cardWidthPx + offsetXAnim.value) / cardWidthPx).coerceIn(0.2f, 1f))
                .clickable { handleItemClick() }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = -(cardWidthPx * 0.48f)
                            coroutineScope.launch {
                                if (offsetXAnim.value <= threshold) {
                                    // Swiped halfway or more: animate off-screen and delete
                                    offsetXAnim.animateTo(
                                        targetValue = -cardWidthPx,
                                        animationSpec = tween(durationMillis = 180)
                                    )
                                    onDelete()
                                } else {
                                    // Not reached halfway: smoothly bounce back
                                    offsetXAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // Only allow dragging to the left (negative offset)
                            coroutineScope.launch {
                                val current = offsetXAnim.value
                                if (dragAmount < 0 || current < 0) {
                                    val newOffset = (current + dragAmount).coerceIn(-cardWidthPx, 0f)
                                    offsetXAnim.snapTo(newOffset)
                                }
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.type.lowercase()) {
                        "clipboard" -> ClipboardVectorIcon(Modifier.size(18.dp), color = iconColor)
                        "file" -> UploadIcon(Modifier.size(18.dp), color = iconColor)
                        "pair" -> DesktopVectorIcon(Modifier.size(18.dp), color = iconColor)
                        else -> NotificationBellIcon(Modifier.size(18.dp), color = iconColor)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = formatRelativeTime(item.timestampMs, copy),
                            color = Mist.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (item.body.isNotBlank()) {
                        Text(
                            text = item.body,
                            color = Mist,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2
                        )
                    }

                    if (item.senderName.isNotBlank() && item.senderName != item.title) {
                        Text(
                            text = item.senderName,
                            color = Blue.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestampMs: Long, copy: Copy): String {
    val now = currentTimeMs()
    val diffSec = (now - timestampMs) / 1000
    return when {
        diffSec < 60 -> copy.timeJustNow
        diffSec < 3600 -> copy.timeMinutesAgo.replace("%d", "${diffSec / 60}")
        diffSec < 86400 -> copy.timeHoursAgo.replace("%d", "${diffSec / 3600}")
        else -> copy.timeDaysAgo.replace("%d", "${diffSec / 86400}")
    }
}
