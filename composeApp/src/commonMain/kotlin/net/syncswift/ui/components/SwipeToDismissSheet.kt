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
import net.syncswift.setSystemBarsDirect
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
                scope.launch {
                    animatable.snapTo(releasedOffset)
                    if (releasedOffset > thresholdPx) {
                        animatable.animateTo(
                            targetValue = releasedOffset + 450f,
                            animationSpec = tween(150, easing = FastOutLinearInEasing)
                        )
                        onDismiss()
                        animatable.snapTo(0f)
                    } else {
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
    thresholdPx: Float = 80f
): SheetDragState {
    val scope = rememberCoroutineScope()

    return remember(onDismiss) {
        SheetDragState(scope, onDismiss, thresholdPx)
    }
}

/**
 * Universal System Bar Controller for Bottom Sheets & Modals:
 * 1. While sheet is active/open: Dims the system notification bar with dark background (Color(0x99000000))
 *    and sets status bar text/icons to WHITE (isDark = true) for clear contrast.
 * 2. When sheet closes/dismisses: ALWAYS immediately restores the status bar background to transparent
 *    and restores icon colors to the active theme (in light theme: BLACK icons).
 */
@Composable
fun SheetSystemBars(
    active: Boolean,
    isDark: Boolean = LocalSyncSwiftColors.current.isDark
) {
    DisposableEffect(active, isDark) {
        if (active) {
            setSystemBarsDirect(isDark = true, statusBarColor = Color(0x99000000))
        }
        onDispose {
            setSystemBarsDirect(isDark = isDark, statusBarColor = Color.Transparent)
        }
    }
}
