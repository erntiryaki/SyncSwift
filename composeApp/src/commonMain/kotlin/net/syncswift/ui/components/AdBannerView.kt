package net.syncswift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AdBannerManager {
    private val activeModalCount = MutableStateFlow(0)
    private val _isModalOpen = MutableStateFlow(false)
    val isModalOpen: StateFlow<Boolean> = _isModalOpen.asStateFlow()

    fun incrementModalCount() {
        activeModalCount.update { count ->
            val newCount = count + 1
            _isModalOpen.value = newCount > 0
            newCount
        }
    }

    fun decrementModalCount() {
        activeModalCount.update { count ->
            val newCount = (count - 1).coerceAtLeast(0)
            _isModalOpen.value = newCount > 0
            newCount
        }
    }
}

@Composable
expect fun AdBannerView(modifier: Modifier = Modifier)
