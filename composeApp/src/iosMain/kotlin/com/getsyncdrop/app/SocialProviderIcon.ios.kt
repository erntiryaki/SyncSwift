package com.getsyncdrop.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
actual fun SocialProviderIcon(provider: IdentityProvider, modifier: Modifier, tint: Color) {
    Text(if (provider == IdentityProvider.Google) "G" else "●", modifier = modifier, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
