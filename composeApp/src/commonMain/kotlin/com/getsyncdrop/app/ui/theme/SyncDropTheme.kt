package com.getsyncdrop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Balanced, premium flat slate-navy dark palette
val Navy = Color(0xFF1E293B)
val Night = Color(0xFF0F172A)
val Cloud = Color(0xFFF8FAFC)
val Mist = Color(0xFF94A3B8)
val Placeholder = Color(0xFFCBD5E1)
val Blue = Color(0xFF38BDF8)
val DeepInk = Color(0xFF0F172A)
val CardSurface = Color(0xFF1E293B)
val InputBg = Color(0xFF334155)

private val DarkColorScheme = darkColorScheme(
    primary = Blue,
    onPrimary = DeepInk,
    surface = CardSurface,
    onSurface = Cloud,
    background = Night,
    onBackground = Cloud
)

@Composable
fun SyncDropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
