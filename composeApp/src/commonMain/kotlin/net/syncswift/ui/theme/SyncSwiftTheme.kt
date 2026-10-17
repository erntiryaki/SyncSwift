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

// Balanced, premium matte slate-navy dark palette (calm, non-glaring, spacious, rich turquoise)
val DarkSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFF0B101B),
    cardSurface = Color(0xFF131B28),
    inputBg = Color(0xFF1A2436),
    inputBorder = Color(0xFF28364D),
    borderHighlight = Color(0x44334663),
    cloud = Color(0xFFFFFFFF), // Pure bright crisp white
    mist = Color(0xFFD1DCED), // Bright, clear secondary text
    placeholder = Color(0xFF94A3B8),
    blue = Color(0xFF2BC5D3), // Refined premium turquoise
    pillButtonBg = Color(0xFF147D87), // Darker, richer turquoise button background in dark theme
    pillButtonBorder = Color(0xFF1C8D98),
    pillButtonDisabledBg = Color(0xFF122C32),
    pillButtonDisabledText = Color(0xFF4C7B83),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFF0B101B),
    successGreen = Color(0xFF10B981),
    errorRed = Color(0xFFEF4444),
    navy = Color(0xFF1E293B),
    isDark = true
)

// Modern, sleek soft titanium slate light palette (pure solid black text, rich contrast)
val LightSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFFEFF2F6), // Cleaner soft off-white background
    cardSurface = Color(0xFFE2E7EE), // Clean layered soft slate surface
    inputBg = Color(0xFFE2E7EE), // Input container background
    inputBorder = Color(0xFFC5CED9), // Crisp soft slate border
    borderHighlight = Color(0x330E8388),
    cloud = Color(0xFF000000), // Solid deep pitch black for ultra-crisp readability
    mist = Color(0xFF1E293B), // Very dark slate black for secondary text
    placeholder = Color(0xFF334155),
    blue = Color(0xFF086872), // Deep petroleum turquoise
    pillButtonBg = Color(0xFF086872), // Bold button background in light mode
    pillButtonBorder = Color(0xFF054A52),
    pillButtonDisabledBg = Color(0xFFBAC4D4),
    pillButtonDisabledText = Color(0xFF5B697D),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFFEFF2F6),
    successGreen = Color(0xFF047857),
    errorRed = Color(0xFFB91C1C),
    navy = Color(0xFFBAC4D4),
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

    val isLight = themeMode == AppThemeMode.Light
    val font = net.syncswift.platformFontFamily()
    val baseTypography = androidx.compose.material3.Typography()
    val typography = baseTypography.copy(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Black else androidx.compose.ui.text.font.FontWeight.ExtraBold),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Black else androidx.compose.ui.text.font.FontWeight.Bold),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Black else androidx.compose.ui.text.font.FontWeight.ExtraBold),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Black else androidx.compose.ui.text.font.FontWeight.ExtraBold),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Bold),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.SemiBold),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = font, fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold)
    )

    CompositionLocalProvider(
        LocalSyncSwiftColors provides colors,
        LocalThemeMode provides themeMode,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(
            fontFamily = font,
            fontWeight = if (isLight) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

