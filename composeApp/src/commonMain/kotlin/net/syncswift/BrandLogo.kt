package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
expect fun BrandLogo(modifier: Modifier = Modifier)

@Composable
expect fun BrandIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified)
