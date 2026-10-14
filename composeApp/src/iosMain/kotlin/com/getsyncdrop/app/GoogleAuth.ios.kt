package com.getsyncdrop.app

import androidx.compose.runtime.Composable

@Composable
actual fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (idToken: String) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    return {
        onError("Google Sign-In on iOS requires Native OAuth flow")
    }
}
