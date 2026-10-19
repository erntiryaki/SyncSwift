package net.syncswift.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SheetDragState(
    val offsetY: Animatable<Float, *>,
    val scope: CoroutineScope,
    val onDismiss: () -> Unit,
    val thresholdPx: Float
) {
    val modifier: Modifier = Modifier
        .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
        .pointerInput(Unit) {
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
                            offsetY.snapTo(0f)
                            onDismiss()
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
}

@Composable
fun rememberSheetDragState(
    onDismiss: () -> Unit,
    thresholdPx: Float = 120f
): SheetDragState {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    // Always guarantee that upon opening or recomposition, the sheet is at 0 (never halfway or cut off!)
    LaunchedEffect(Unit) {
        offsetY.snapTo(0f)
    }

    return remember(onDismiss) {
        SheetDragState(offsetY, scope, onDismiss, thresholdPx)
    }
}
