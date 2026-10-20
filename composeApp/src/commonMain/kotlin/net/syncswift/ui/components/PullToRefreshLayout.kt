package net.syncswift.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.ui.theme.LocalSyncSwiftColors
import net.syncswift.ui.theme.Mist

/**
 * Modern, elegant, boxless (kutusuz) pull-to-refresh component.
 * Features a glowing minimalist circular progress indicator that fades in smoothly
 * with user drag and spins elegantly during network scan.
 */
@Composable
fun ElegantPullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshingText: String? = null,
    canScrollBackward: () -> Boolean,
    content: @Composable () -> Unit
) {
    var pullDistance by remember { mutableStateOf(0f) }
    val maxDragDistance = 180f
    val triggerThreshold = 120f

    val nestedScrollConnection = remember(isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If dragging up and we currently have pull distance, consume it
                if (available.y < 0 && pullDistance > 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // If user is pulling down from top
                if (source == NestedScrollSource.UserInput && available.y > 0 && !canScrollBackward() && !isRefreshing) {
                    pullDistance = (pullDistance + available.y * 0.45f).coerceAtMost(maxDragDistance)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistance >= triggerThreshold && !isRefreshing) {
                    onRefresh()
                }
                pullDistance = 0f
                return Velocity.Zero
            }
        }
    }

    val progress = (pullDistance / triggerThreshold).coerceIn(0f, 1f)
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isRefreshing || progress > 0.15f) 1f else 0f,
        animationSpec = tween(180)
    )

    Column(
        modifier = modifier.nestedScroll(nestedScrollConnection)
    ) {
        // Floating boxless indicator header
        val isVisible = isRefreshing || pullDistance > 15f
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .graphicsLayer { alpha = indicatorAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = LocalSyncSwiftColors.current.blue,
                        strokeWidth = 2.2.dp
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        color = LocalSyncSwiftColors.current.blue.copy(alpha = 0.5f + (progress * 0.5f)),
                        strokeWidth = 2.2.dp
                    )
                }

                if (!refreshingText.isNullOrBlank() && isRefreshing) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = refreshingText,
                        color = Mist,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        content()
    }
}
