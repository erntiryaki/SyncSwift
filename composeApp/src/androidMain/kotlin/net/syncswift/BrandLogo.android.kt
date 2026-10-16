package net.syncswift

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import net.syncswift.ui.theme.SyncSwiftTheme

@Composable
actual fun BrandLogo(modifier: Modifier) {
    val isDark = net.syncswift.ui.theme.IsDarkTheme
    Image(
        painter = painterResource(R.drawable.logo_syncswift),
        contentDescription = "SyncSwift",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = if (!isDark) ColorFilter.tint(Color.Black, BlendMode.SrcIn) else null
    )
}

@Composable
actual fun BrandIcon(modifier: Modifier, tint: Color) {
    Image(
        painter = painterResource(R.drawable.icon_syncswift),
        contentDescription = "SyncSwift Icon",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
