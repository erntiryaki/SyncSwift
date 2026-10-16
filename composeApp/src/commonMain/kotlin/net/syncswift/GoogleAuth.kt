package net.syncswift

import androidx.compose.runtime.Composable

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String? = null,
    val givenName: String? = null,
    val familyName: String? = null
)

@Composable
expect fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (userData: GoogleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit
