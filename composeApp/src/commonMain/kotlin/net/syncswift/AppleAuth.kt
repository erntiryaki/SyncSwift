package net.syncswift

import androidx.compose.runtime.Composable

data class AppleUserData(
    val idToken: String,
    val email: String,
    val userIdentifier: String = "",
    val givenName: String? = null,
    val familyName: String? = null
)

@Composable
expect fun rememberAppleAuthLauncher(
    onSuccess: (userData: AppleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit
