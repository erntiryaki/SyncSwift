package net.syncswift

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp

object BackDispatcher {
    val handlers = mutableStateListOf<() -> Unit>()

    fun register(handler: () -> Unit) {
        handlers.remove(handler)
        handlers.add(handler)
    }

    fun unregister(handler: () -> Unit) {
        handlers.remove(handler)
    }

    fun canGoBack(): Boolean = handlers.isNotEmpty()

    fun handleBack(): Boolean {
        if (handlers.isNotEmpty()) {
            val last = handlers.last()
            last.invoke()
            return true
        }
        return false
    }
}

fun Modifier.edgeSwipeToBack(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier = composed {
    var startX by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    pointerInput(enabled, onBack) {
        if (!enabled) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                startX = offset.x
                totalDragX = 0f
            },
            onDragEnd = {
                val edgeThresholdPx = 55.dp.toPx()
                val minSwipeDistancePx = 70.dp.toPx()
                if (startX <= edgeThresholdPx && totalDragX >= minSwipeDistancePx) {
                    onBack()
                }
                totalDragX = 0f
            },
            onDragCancel = {
                totalDragX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                if (startX <= 55.dp.toPx() && dragAmount > 0) {
                    totalDragX += dragAmount
                    change.consume()
                }
            }
        )
    }
}

@Composable
expect fun DoubleBackToExitHandler(exitMessage: String)

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
