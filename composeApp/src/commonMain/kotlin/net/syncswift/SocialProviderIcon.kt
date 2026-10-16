package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
expect fun SocialProviderIcon(provider: IdentityProvider, modifier: Modifier, tint: Color)
