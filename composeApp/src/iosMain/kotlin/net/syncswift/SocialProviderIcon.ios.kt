package net.syncswift

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import net.syncswift.resources.Res
import net.syncswift.resources.apple_mark
import net.syncswift.resources.google_mark

import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
actual fun SocialProviderIcon(provider: IdentityProvider, modifier: Modifier, tint: Color) {
    try {
        val resource = if (provider == IdentityProvider.Google) Res.drawable.google_mark else Res.drawable.apple_mark
        Image(
            painter = painterResource(resource),
            contentDescription = provider.name,
            modifier = modifier,
            colorFilter = if (provider == IdentityProvider.Apple) ColorFilter.tint(tint) else null
        )
    } catch (_: Throwable) {
        Text(
            text = if (provider == IdentityProvider.Google) "G" else "",
            modifier = modifier,
            color = tint,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
