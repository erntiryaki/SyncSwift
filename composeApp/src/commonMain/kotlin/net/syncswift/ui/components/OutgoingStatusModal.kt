package net.syncswift.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.syncswift.Copy
import net.syncswift.connection.OutgoingRequestState
import net.syncswift.connection.OutgoingStatusType
import net.syncswift.connection.OutgoingStep
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Modern, quiet, premium minimalist status modal for outgoing connection and transfer requests.
 * Pure flat aesthetic, zero neon glow, zero radar rings, seamlessly aligned with SyncSwift design tokens.
 */
@Composable
fun OutgoingStatusModal(
    state: OutgoingRequestState,
    copy: Copy,
    onDismiss: () -> Unit
) {
    // Auto-dismiss on SUCCESS after 2 seconds
    LaunchedEffect(state.step) {
        if (state.step == OutgoingStep.SUCCESS) {
            delay(2000)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (state.step == OutgoingStep.SUCCESS || state.step == OutgoingStep.REJECTED_OR_FAILED) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.15f)),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state.step) {
                    OutgoingStep.WAITING_APPROVAL -> {
                        BoxlessWaitingSection(state = state, copy = copy)
                    }
                    OutgoingStep.IN_PROGRESS -> {
                        BoxlessProgressSection(state = state, copy = copy)
                    }
                    OutgoingStep.SUCCESS -> {
                        BoxlessSuccessSection(state = state, copy = copy)
                    }
                    OutgoingStep.REJECTED_OR_FAILED -> {
                        BoxlessFailedSection(state = state, copy = copy)
                    }
                }

                Spacer(Modifier.height(26.dp))

                // Bottom Action Button
                val buttonText = when (state.step) {
                    OutgoingStep.WAITING_APPROVAL -> copy.otpCancelAction
                    OutgoingStep.IN_PROGRESS -> copy.otpCancelAction
                    OutgoingStep.SUCCESS -> copy.continueText
                    OutgoingStep.REJECTED_OR_FAILED -> copy.close
                }

                val buttonBg = when (state.step) {
                    OutgoingStep.WAITING_APPROVAL, OutgoingStep.IN_PROGRESS -> InputBg.copy(alpha = 0.5f)
                    OutgoingStep.SUCCESS, OutgoingStep.REJECTED_OR_FAILED -> PillButtonBg
                }

                val buttonTextColor = when (state.step) {
                    OutgoingStep.WAITING_APPROVAL, OutgoingStep.IN_PROGRESS -> Mist
                    OutgoingStep.SUCCESS, OutgoingStep.REJECTED_OR_FAILED -> Cloud
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() },
                    color = buttonBg,
                    shape = RoundedCornerShape(12.dp),
                    border = if (state.step == OutgoingStep.WAITING_APPROVAL || state.step == OutgoingStep.IN_PROGRESS) {
                        BorderStroke(1.dp, InputBorder.copy(alpha = 0.2f))
                    } else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = buttonText,
                            color = buttonTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pure minimalist waiting section without neon, glow or radar effects.
 * Subtle quiet spinner with crisp typography.
 */
@Composable
private fun BoxlessWaitingSection(state: OutgoingRequestState, copy: Copy) {
    CircularProgressIndicator(
        modifier = Modifier.size(36.dp),
        color = Blue,
        strokeWidth = 2.5.dp
    )

    Spacer(Modifier.height(20.dp))

    Text(
        text = if (state.type == OutgoingStatusType.CONNECTION) copy.waitingForConnection else copy.waitingForTransferApproval,
        color = Cloud,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    // Minimal device indicator
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Blue)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = state.deviceName,
            color = Cloud,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (!state.fileName.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.fileName,
            color = Mist,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(Modifier.height(10.dp))

    Text(
        text = if (state.message.isNotBlank()) state.message else copy.waitingForApprovalDesc,
        color = Mist.copy(alpha = 0.8f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 17.sp
    )
}

/**
 * Pure minimalist transfer progress section with flat slim bar.
 */
@Composable
private fun BoxlessProgressSection(state: OutgoingRequestState, copy: Copy) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(200)
    )

    val primaryColor = Blue
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(primaryColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val w = size.width
            val h = size.height

            drawLine(
                color = primaryColor,
                start = Offset(w * 0.5f, h * 0.75f),
                end = Offset(w * 0.5f, h * 0.25f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            val path = Path().apply {
                moveTo(w * 0.3f, h * 0.45f)
                lineTo(w * 0.5f, h * 0.25f)
                lineTo(w * 0.7f, h * 0.45f)
            }
            drawPath(path, color = primaryColor, style = stroke)
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = copy.sendingFileModalTitle,
        color = Cloud,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        textAlign = TextAlign.Center
    )

    if (!state.fileName.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.fileName,
            color = Mist,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(Modifier.height(20.dp))

    // Flat hairline progress bar
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF1E293B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Blue)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatMinimalBytes(state.transferredBytes)} / ${formatMinimalBytes(state.totalBytes)}",
                color = Mist,
                fontSize = 12.sp
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = Cloud,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Pure minimalist success section with clean checkmark.
 */
@Composable
private fun BoxlessSuccessSection(state: OutgoingRequestState, copy: Copy) {
    val successColor = SuccessGreen
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(successColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            val w = size.width
            val h = size.height

            val checkPath = Path().apply {
                moveTo(w * 0.22f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.72f)
                lineTo(w * 0.78f, h * 0.32f)
            }
            drawPath(checkPath, color = successColor, style = stroke)
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = if (state.type == OutgoingStatusType.CONNECTION) copy.connectionEstablishedTitle else copy.transferCompletedTitle,
        color = Cloud,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = if (state.message.isNotBlank()) state.message else copy.operationCompletedDesc.replace("%s", state.deviceName),
        color = Mist.copy(alpha = 0.8f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 17.sp
    )
}

/**
 * Pure minimalist failure section with clean cross mark.
 */
@Composable
private fun BoxlessFailedSection(state: OutgoingRequestState, copy: Copy) {
    val errorColor = ErrorRed
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(errorColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidth = 2.2.dp.toPx()
            val w = size.width
            val h = size.height

            drawLine(
                color = errorColor,
                start = Offset(w * 0.28f, h * 0.28f),
                end = Offset(w * 0.72f, h * 0.72f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = errorColor,
                start = Offset(w * 0.72f, h * 0.28f),
                end = Offset(w * 0.28f, h * 0.72f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = if (state.type == OutgoingStatusType.CONNECTION) copy.connectionFailedTitle else copy.transferFailedTitle,
        color = Cloud,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = if (state.message.isNotBlank()) state.message else copy.operationRejectedOrTimeout,
        color = Mist.copy(alpha = 0.8f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 17.sp
    )
}

private fun formatMinimalBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "${(round(value * 10.0) / 10.0)} ${units[digitGroups]}"
}
