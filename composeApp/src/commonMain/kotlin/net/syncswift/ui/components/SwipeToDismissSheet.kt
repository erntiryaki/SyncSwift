package net.syncswift.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.syncswift.setSystemBarsDirect
import net.syncswift.ui.theme.LocalSyncSwiftColors
import kotlin.math.roundToInt

class SheetDragState(
    val offsetY: Animatable<Float, *>,
    val scope: CoroutineScope,
    val onDismiss: () -> Unit,
    val thresholdPx: Float
) {
    /**
     * Drag detector modifier to attach to sheet headers, drag handles or top areas.
     * Prevents gesture conflicts with inner vertical scrolls while giving immediate, natural swipe-down to dismiss.
     */
    val dragHandleModifier: Modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0 || offsetY.value > 0) {
                    change.consume()
                    scope.launch {
                        offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                    }
                }
            },
            onDragEnd = {
                if (offsetY.value > thresholdPx) {
                    scope.launch {
                        onDismiss()
                        offsetY.snapTo(0f)
                    }
                } else {
                    scope.launch {
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        )
    }

    /**
     * Offset modifier to attach to the bottom sheet Surface or container.
     */
    val sheetOffsetModifier: Modifier = Modifier.offset {
        IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0))
    }

    /**
     * Combined modifier for simple sheets without nested scrolling.
     */
    val modifier: Modifier = sheetOffsetModifier.then(dragHandleModifier)
}

@Composable
fun rememberSheetDragState(
    onDismiss: () -> Unit,
    thresholdPx: Float = 90f
): SheetDragState {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        offsetY.snapTo(0f)
    }

    return remember(onDismiss) {
        SheetDragState(offsetY, scope, onDismiss, thresholdPx)
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
