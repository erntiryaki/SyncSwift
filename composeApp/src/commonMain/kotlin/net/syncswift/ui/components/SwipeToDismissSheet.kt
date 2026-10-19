package net.syncswift.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    fun reset() {
        isDragging = false
        dragOffsetY = 0f
        scope.launch {
            try {
                animatable.snapTo(0f)
            } catch (_: Exception) {}
        }
    }

    val dragHandleModifier: Modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                isDragging = true
                dragOffsetY = 0f
            },
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0f || dragOffsetY > 0f) {
                    change.consume()
                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                }
            },
            onDragEnd = {
                val releasedOffset = dragOffsetY
                if (releasedOffset > thresholdPx) {
                    scope.launch {
                        try {
                            animatable.snapTo(releasedOffset)
                            isDragging = false
                            // Glide smoothly down offscreen (160ms, butter-smooth)
                            animatable.animateTo(
                                targetValue = 1000f,
                                animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing)
                            )
                        } catch (_: Exception) {
                        } finally {
                            onDismiss()
                            dragOffsetY = 0f
                            animatable.snapTo(0f)
                        }
                    }
                } else {
                    scope.launch {
                        try {
                            animatable.snapTo(releasedOffset)
                            isDragging = false
                            dragOffsetY = 0f
                            animatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        } catch (_: Exception) {
                            dragOffsetY = 0f
                            animatable.snapTo(0f)
                        }
                    }
                }
            },
            onDragCancel = {
                val releasedOffset = dragOffsetY
                scope.launch {
                    try {
                        animatable.snapTo(releasedOffset)
                        isDragging = false
                        dragOffsetY = 0f
                        animatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    } catch (_: Exception) {
                        dragOffsetY = 0f
                        animatable.snapTo(0f)
                    }
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
    visible: Boolean = true,
    onDismiss: () -> Unit,
    thresholdPx: Float = 60f
): SheetDragState {
    val scope = rememberCoroutineScope()
    val state = remember(onDismiss) {
        SheetDragState(scope, onDismiss, thresholdPx)
    }

    // Reset offsets on every open/close cycle so the sheet NEVER shrinks or accumulates offset
    LaunchedEffect(visible) {
        state.reset()
    }

    DisposableEffect(Unit) {
        state.reset()
        onDispose {
            state.reset()
        }
    }

    return state
}
