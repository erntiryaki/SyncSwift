package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import net.syncswift.resources.Res
import net.syncswift.resources.auth_hero_dark
import net.syncswift.resources.auth_hero_light

import androidx.compose.ui.Alignment

@Composable
actual fun AuthHeroImage(isDark: Boolean, modifier: Modifier) {
    val res = if (isDark) Res.drawable.auth_hero_dark else Res.drawable.auth_hero_light
    Image(
        painter = painterResource(res),
        contentDescription = "SyncSwift Seamless Cross-Device Sync",
        modifier = modifier,
        alignment = Alignment.TopCenter,
        contentScale = ContentScale.FillWidth
    )
}
