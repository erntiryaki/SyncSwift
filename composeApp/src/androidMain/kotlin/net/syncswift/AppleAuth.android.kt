package net.syncswift

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAppleAuthLauncher(
    onSuccess: (userData: AppleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    return {
        onError("Apple Sign-In is only available on iOS devices.")
    }
}
