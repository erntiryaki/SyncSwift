package net.syncswift

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import net.syncswift.resources.Res
import net.syncswift.resources.logo_syncswift
import net.syncswift.resources.icon_syncswift

@Composable
actual fun BrandLogo(modifier: Modifier) {
    val isDark = net.syncswift.ui.theme.IsDarkTheme
    Image(
        painter = painterResource(Res.drawable.logo_syncswift),
        contentDescription = "SyncSwift",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = if (!isDark) ColorFilter.tint(Color.Black, BlendMode.SrcIn) else null
    )
}

@Composable
actual fun BrandIcon(modifier: Modifier, tint: Color) {
    Image(
        painter = painterResource(Res.drawable.icon_syncswift),
        contentDescription = "SyncSwift Icon",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null
    )
}
