package com.getsyncdrop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
actual fun BrandLogo(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_syncdrop),
        contentDescription = "SyncDrop",
        modifier = modifier.height(64.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
actual fun BrandIcon(modifier: Modifier, tint: Color) {
    Image(
        painter = painterResource(R.drawable.icon_syncdrop),
        contentDescription = "SyncDrop Icon",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
