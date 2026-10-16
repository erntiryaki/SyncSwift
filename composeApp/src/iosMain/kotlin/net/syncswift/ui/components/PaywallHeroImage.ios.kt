package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import net.syncswift.resources.Res
import net.syncswift.resources.paywall_hero

@Composable
actual fun PaywallHeroImage(modifier: Modifier) {
    Image(
        painter = painterResource(Res.drawable.paywall_hero),
        contentDescription = "SyncSwift Pro",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
