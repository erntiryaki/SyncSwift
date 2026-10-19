package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import net.syncswift.R

import androidx.compose.ui.Alignment

@Composable
actual fun AuthHeroImage(isDark: Boolean, modifier: Modifier) {
    val resId = if (isDark) R.drawable.auth_hero_dark else R.drawable.auth_hero_light
    Image(
        painter = painterResource(resId),
        contentDescription = "SyncSwift Seamless Cross-Device Sync",
        modifier = modifier,
        alignment = Alignment.TopCenter,
        contentScale = ContentScale.FillWidth
    )
}
