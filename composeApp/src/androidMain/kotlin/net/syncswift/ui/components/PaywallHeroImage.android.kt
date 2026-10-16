package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import net.syncswift.R

@Composable
actual fun PaywallHeroImage(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.paywall_hero),
        contentDescription = "SyncSwift Pro",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
