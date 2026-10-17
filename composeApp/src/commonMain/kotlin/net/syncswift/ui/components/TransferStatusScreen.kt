package net.syncswift.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.syncswift.Copy
import net.syncswift.CrossVectorIcon
import net.syncswift.LaptopVectorIcon
import net.syncswift.PhoneVectorIcon
import net.syncswift.TransferVectorIcon
import net.syncswift.connection.OutgoingRequestState
import net.syncswift.connection.OutgoingStatusType
import net.syncswift.connection.OutgoingStep
import net.syncswift.ui.theme.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Full-page, minimalist, modern Transfer Status Screen (NO MODAL).
 * Fits the SyncSwift brand design language:
 * - Animated expanding checkmark on SUCCESS
 * - Animated cross on REJECTED / FAILED
 * - Sleek progress indicators, file metadata, and full localized translations.
 */
@Composable
fun TransferStatusScreen(
    state: OutgoingRequestState,
    copy: Copy,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = onDismiss
) {
    // Auto-dismiss on SUCCESS after 2.5 seconds
    LaunchedEffect(state.step) {
        if (state.step == OutgoingStep.SUCCESS) {
            delay(2500)
            onDismiss()
        }
    }

    val isFile = state.type == OutgoingStatusType.FILE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Screen Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFile) copy.fileTransferScreenTitle else copy.connectionScreenTitle,
                    color = Cloud,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )

                // Close Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable {
                            if (state.step == OutgoingStep.WAITING_APPROVAL || state.step == OutgoingStep.IN_PROGRESS) {
                                onCancel()
                            } else {
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CrossVectorIcon(Modifier.size(16.dp), color = Mist)
                }
            }

            // CENTER CONTENT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (state.step) {
                    OutgoingStep.WAITING_APPROVAL -> {
                        TransferWaitingSection(state = state, copy = copy)
                    }
                    OutgoingStep.IN_PROGRESS -> {
                        TransferInProgressSection(state = state, copy = copy)
                    }
                    OutgoingStep.SUCCESS -> {
                        TransferSuccessSection(state = state, copy = copy)
                    }
                    OutgoingStep.REJECTED_OR_FAILED -> {
                        TransferFailedSection(state = state, copy = copy)
                    }
                }
            }

            // BOTTOM ACTION BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                when (state.step) {
                    OutgoingStep.WAITING_APPROVAL, OutgoingStep.IN_PROGRESS -> {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InputBg,
                                contentColor = Cloud
                            ),
                            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = copy.cancelAction,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    OutgoingStep.SUCCESS -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = copy.doneAction,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutgoingStep.REJECTED_OR_FAILED -> {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InputBg,
                                contentColor = Cloud
                            ),
                            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = copy.closeAction,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Waiting for approval state: pulsing visual ring + device & file info
 */
@Composable
private fun TransferWaitingSection(
    state: OutgoingRequestState,
    copy: Copy
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val isMobile = remember(state.deviceName) {
        val dev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
            net.syncswift.discovery.cleanDeviceDisplayName(it.name).equals(net.syncswift.discovery.cleanDeviceDisplayName(state.deviceName), ignoreCase = true)
        }
        val os = dev?.osType ?: ""
        val name = state.deviceName.lowercase()
        os.equals("Android", ignoreCase = true) || os.equals("iOS", ignoreCase = true) ||
        name.contains("iphone") || name.contains("android") || name.contains("galaxy") ||
        name.contains("pixel") || name.contains("redmi") || name.contains("xiaomi") ||
        name.contains("huawei") || name.contains("samsung") || name.contains("oppo") ||
        name.contains("vivo") || name.contains("phone") || name.contains("ipad") ||
        name.contains("tablet")
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(Blue.copy(alpha = 0.12f))
            .border(BorderStroke(1.5.dp, Blue.copy(alpha = 0.4f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (state.type == OutgoingStatusType.FILE) {
            TransferVectorIcon(Modifier.size(54.dp), color = Blue)
        } else {
            if (isMobile) {
                PhoneVectorIcon(Modifier.size(54.dp), color = Blue)
            } else {
                LaptopVectorIcon(Modifier.size(54.dp), color = Blue)
            }
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text = if (state.type == OutgoingStatusType.FILE) copy.waitingForTransferApproval else copy.waitingForConnection,
        color = Cloud,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = copy.waitingForRecipientApproval,
        color = Mist,
        fontSize = 14.5.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    // Device Badge
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = InputBg.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMobile) {
                PhoneVectorIcon(Modifier.size(16.dp), color = Mist)
            } else {
                LaptopVectorIcon(Modifier.size(16.dp), color = Mist)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.deviceName,
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (state.fileName != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${state.fileName} (${formatBytes(state.totalBytes)})",
            color = Mist,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Transfer in progress state: percentage, circular progress, transfer rate
 */
@Composable
private fun TransferInProgressSection(
    state: OutgoingRequestState,
    copy: Copy
) {
    val progress = state.progress.coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt()

    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = Blue,
            trackColor = InputBg,
            strokeWidth = 9.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "$percentage%",
                color = Cloud,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = copy.sendingFileModalTitle,
        color = Cloud,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = copy.transferringFile,
        color = Mist,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp)
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "${formatBytes(state.transferredBytes)} / ${formatBytes(state.totalBytes)}",
        color = Blue,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold
    )

    if (state.fileName != null) {
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = InputBg.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.2f)),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferVectorIcon(Modifier.size(16.dp), color = Mist)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.fileName,
                    color = Cloud,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Success State: Animated expanding checkmark (efektli tik işareti)
 */
@Composable
private fun TransferSuccessSection(
    state: OutgoingRequestState,
    copy: Copy
) {
    var triggerAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        triggerAnim = true
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (triggerAnim) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val checkProgress by animateFloatAsState(
        targetValue = if (triggerAnim) 1f else 0f,
        animationSpec = tween(500, delayMillis = 150, easing = FastOutSlowInEasing)
    )

    // EFEKTLİ TİK İŞARETİ CONTAINER
    Box(
        modifier = Modifier
            .size(130.dp)
            .scale(scaleAnim)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(SuccessGreen.copy(alpha = 0.25f), SuccessGreen.copy(alpha = 0.08f), Color.Transparent)
                )
            )
            .border(BorderStroke(2.dp, SuccessGreen.copy(alpha = 0.6f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(SuccessGreen),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(38.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.74f)
                    lineTo(size.width * 0.82f, size.height * 0.28f)
                }
                drawPath(
                    path = p,
                    color = Color.White,
                    style = Stroke(
                        width = 4.dp.toPx() * checkProgress,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text = if (state.type == OutgoingStatusType.FILE) copy.transferCompleted else copy.connectionEstablishedTitle,
        color = Cloud,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = copy.operationCompletedDesc.replaceFirst("%s", state.deviceName),
        color = Mist,
        fontSize = 14.5.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * Failed or Rejected State: Animated cross (efektli çarpı işareti)
 */
@Composable
private fun TransferFailedSection(
    state: OutgoingRequestState,
    copy: Copy
) {
    var triggerAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        triggerAnim = true
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (triggerAnim) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val crossProgress by animateFloatAsState(
        targetValue = if (triggerAnim) 1f else 0f,
        animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing)
    )

    // EFEKTLİ ÇARPI İŞARETİ CONTAINER
    Box(
        modifier = Modifier
            .size(130.dp)
            .scale(scaleAnim)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(ErrorRed.copy(alpha = 0.25f), ErrorRed.copy(alpha = 0.08f), Color.Transparent)
                )
            )
            .border(BorderStroke(2.dp, ErrorRed.copy(alpha = 0.6f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(ErrorRed),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(34.dp)) {
                val strokeW = 4.dp.toPx() * crossProgress
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.25f, size.height * 0.25f),
                    end = Offset(size.width * 0.75f, size.height * 0.75f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.75f, size.height * 0.25f),
                    end = Offset(size.width * 0.25f, size.height * 0.75f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text = if (state.type == OutgoingStatusType.FILE) copy.transferFailed else copy.connectionFailedTitle,
        color = Cloud,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = state.message.ifBlank { copy.operationRejectedOrTimeout },
        color = Mist,
        fontSize = 14.5.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    val rounded = round(value * 10) / 10.0
    return "$rounded ${units[digitGroups]}"
}
