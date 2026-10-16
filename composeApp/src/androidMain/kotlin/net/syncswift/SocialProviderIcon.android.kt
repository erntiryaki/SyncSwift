package net.syncswift

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

@Composable
actual fun SocialProviderIcon(provider: IdentityProvider, modifier: Modifier, tint: Color) {
    val resource = if (provider == IdentityProvider.Google) R.drawable.google_mark else R.drawable.apple_mark
    Image(
        painter = painterResource(resource),
        contentDescription = provider.name,
        modifier = modifier,
        colorFilter = if (provider == IdentityProvider.Apple) ColorFilter.tint(tint) else null
    )
}
