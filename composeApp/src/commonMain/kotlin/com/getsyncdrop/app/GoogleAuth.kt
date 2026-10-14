package com.getsyncdrop.app

import androidx.compose.runtime.Composable

@Composable
expect fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (idToken: String) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit
