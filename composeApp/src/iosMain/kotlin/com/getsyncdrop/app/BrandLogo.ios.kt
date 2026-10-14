package com.getsyncdrop.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
actual fun BrandLogo(modifier: Modifier) {
    Text("SyncDrop", modifier = modifier, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
}

@Composable
actual fun BrandIcon(modifier: Modifier, tint: Color) {
    SyncArrowIcon(modifier = modifier, color = tint)
}
