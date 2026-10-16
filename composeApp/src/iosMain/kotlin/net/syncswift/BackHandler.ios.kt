package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun DoubleBackToExitHandler(exitMessage: String) {
    // Native iOS apps do not use double-tap back to exit
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    DisposableEffect(enabled, onBack) {
        if (enabled) {
            BackDispatcher.register(onBack)
        }
        onDispose {
            if (enabled) {
                BackDispatcher.unregister(onBack)
            }
        }
    }
}
