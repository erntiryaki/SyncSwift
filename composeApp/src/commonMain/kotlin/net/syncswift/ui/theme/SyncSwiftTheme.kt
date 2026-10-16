package net.syncswift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    Dark,
    Light
}

class SyncSwiftColors(
    val night: Color,
    val cardSurface: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val borderHighlight: Color,
    val cloud: Color,
    val mist: Color,
    val placeholder: Color,
    val blue: Color,
    val pillButtonBg: Color,
    val pillButtonBorder: Color,
    val pillButtonDisabledBg: Color,
    val pillButtonDisabledText: Color,
    val pillButtonText: Color,
    val deepInk: Color,
    val successGreen: Color,
    val errorRed: Color,
    val navy: Color,
    val isDark: Boolean
)

// Balanced, premium matte slate-navy dark palette (calm, non-glaring, spacious, highly visible turquoise)
val DarkSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFF0B101B),
    cardSurface = Color(0xFF131B28),
    inputBg = Color(0xFF1A2436),
    inputBorder = Color(0xFF28364D),
    borderHighlight = Color(0x44334663),
    cloud = Color(0xFFF8FAFC),
    mist = Color(0xFFA6B4C9),
    placeholder = Color(0xFF64748B),
    blue = Color(0xFF38D5E2), // Highly visible, vivid, premium turquoise
    pillButtonBg = Color(0xFF249DA8), // Lighter, more vivid turquoise in dark theme
    pillButtonBorder = Color(0xFF3AC4D2),
    pillButtonDisabledBg = Color(0xFF122C32),
    pillButtonDisabledText = Color(0xFF4C7B83),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFF0B101B),
    successGreen = Color(0xFF10B981),
    errorRed = Color(0xFFEF4444),
    navy = Color(0xFF1E293B),
    isDark = true
)

// Modern, sleek cool-neutral matte titanium slate light palette (comfortable, eye-friendly, NOT harsh white)
val LightSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFFC4CBD6), // Calmer matte titanium slate background (subdued, comfortable, not white)
    cardSurface = Color(0xFFB2BAC7), // Distinct soft slate surface
    inputBg = Color(0xFFB2BAC7), // Input container background
    inputBorder = Color(0xFF909CAC), // Crisp slate border
    borderHighlight = Color(0x330E8388),
    cloud = Color(0xFF0A101D), // Deep rich charcoal slate for crystal-clear readability
    mist = Color(0xFF334155), // Elegant slate secondary text
    placeholder = Color(0xFF64748B),
    blue = Color(0xFF086872), // Deep petroleum turquoise
    pillButtonBg = Color(0xFF086872), // Darker, bold button background in light mode
    pillButtonBorder = Color(0xFF054A52),
    pillButtonDisabledBg = Color(0xFFA2ADC0),
    pillButtonDisabledText = Color(0xFF5B697D),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFFC4CBD6),
    successGreen = Color(0xFF047857),
    errorRed = Color(0xFFB91C1C),
    navy = Color(0xFFA2ADC0),
    isDark = false
)

val LocalSyncSwiftColors = staticCompositionLocalOf { DarkSyncSwiftColors }
val LocalThemeMode = staticCompositionLocalOf { AppThemeMode.Dark }

// Dynamic Composable accessors for seamless backward-compatibility across all UI files
val Night: Color @Composable get() = LocalSyncSwiftColors.current.night
val CardSurface: Color @Composable get() = LocalSyncSwiftColors.current.cardSurface
val InputBg: Color @Composable get() = LocalSyncSwiftColors.current.inputBg
val InputBorder: Color @Composable get() = LocalSyncSwiftColors.current.inputBorder
val BorderHighlight: Color @Composable get() = LocalSyncSwiftColors.current.borderHighlight
val Cloud: Color @Composable get() = LocalSyncSwiftColors.current.cloud
val Mist: Color @Composable get() = LocalSyncSwiftColors.current.mist
val Placeholder: Color @Composable get() = LocalSyncSwiftColors.current.placeholder
val Blue: Color @Composable get() = LocalSyncSwiftColors.current.blue
val PillButtonBg: Color @Composable get() = LocalSyncSwiftColors.current.pillButtonBg
val PillButtonBorder: Color @Composable get() = LocalSyncSwiftColors.current.pillButtonBorder
val PillButtonDisabledBg: Color @Composable get() = LocalSyncSwiftColors.current.pillButtonDisabledBg
val PillButtonDisabledText: Color @Composable get() = LocalSyncSwiftColors.current.pillButtonDisabledText
val PillButtonText: Color @Composable get() = LocalSyncSwiftColors.current.pillButtonText
val DeepInk: Color @Composable get() = LocalSyncSwiftColors.current.deepInk
val SuccessGreen: Color @Composable get() = LocalSyncSwiftColors.current.successGreen
val ErrorRed: Color @Composable get() = LocalSyncSwiftColors.current.errorRed
val Navy: Color @Composable get() = LocalSyncSwiftColors.current.navy
val IsDarkTheme: Boolean @Composable get() = LocalSyncSwiftColors.current.isDark

@Composable
fun SyncSwiftTheme(
    themeMode: AppThemeMode = AppThemeMode.Dark,
    content: @Composable () -> Unit
) {
    val colors = if (themeMode == AppThemeMode.Light) LightSyncSwiftColors else DarkSyncSwiftColors
    net.syncswift.UpdateSystemBars(isDark = colors.isDark)
    val colorScheme = if (themeMode == AppThemeMode.Light) {
        lightColorScheme(
            primary = colors.blue,
            onPrimary = Color.White,
            surface = colors.cardSurface,
            onSurface = colors.cloud,
            background = colors.night,
            onBackground = colors.cloud
        )
    } else {
        darkColorScheme(
            primary = colors.blue,
            onPrimary = colors.deepInk,
            surface = colors.cardSurface,
            onSurface = colors.cloud,
            background = colors.night,
            onBackground = colors.cloud
        )
    }

    CompositionLocalProvider(
        LocalSyncSwiftColors provides colors,
        LocalThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

