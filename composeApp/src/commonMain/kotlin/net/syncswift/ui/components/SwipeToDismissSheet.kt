package net.syncswift.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.syncswift.ui.theme.LocalSyncSwiftColors

class SheetDragState(
    val scope: CoroutineScope,
    val onDismiss: () -> Unit,
    val thresholdPx: Float
) {
    val animatable = Animatable(0f)
    var isDragging by mutableStateOf(false)
    var dragOffsetY by mutableFloatStateOf(0f)

    val currentOffset: Float
        get() = if (isDragging) dragOffsetY else animatable.value

    /**
     * Butter-smooth, zero-latency drag gesture detector.
     * Updates direct primitive float without per-frame coroutine dispatches.
     */
    val dragHandleModifier: Modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                isDragging = true
                dragOffsetY = animatable.value
            },
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0f || dragOffsetY > 0f) {
                    change.consume()
                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                }
            },
            onDragEnd = {
                val releasedOffset = dragOffsetY
                isDragging = false
                if (releasedOffset > thresholdPx) {
                    onDismiss()
                } else {
                    scope.launch {
                        animatable.snapTo(releasedOffset)
                        dragOffsetY = 0f
                        animatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                val releasedOffset = dragOffsetY
                isDragging = false
                scope.launch {
                    animatable.snapTo(releasedOffset)
                    dragOffsetY = 0f
                    animatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        )
    }

    /**
     * GPU Hardware Layer Translation - Zero layout recomputations, butter-smooth 120 FPS!
     */
    val sheetOffsetModifier: Modifier = Modifier.graphicsLayer {
        translationY = currentOffset
    }

    val modifier: Modifier = sheetOffsetModifier.then(dragHandleModifier)
}

@Composable
fun rememberSheetDragState(
    onDismiss: () -> Unit,
    thresholdPx: Float = 60f
): SheetDragState {
    val scope = rememberCoroutineScope()

    return remember(onDismiss) {
        SheetDragState(scope, onDismiss, thresholdPx)
    }
}
