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

// Balanced, premium matte slate-navy dark palette matching screenshots
val DarkSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFF0B101B), // Deep navy dark canvas
    cardSurface = Color(0xFF131B28), // Sleek card background
    inputBg = Color(0xFF1A2436),
    inputBorder = Color(0xFF1E293B),
    borderHighlight = Color(0x334D90FE),
    cloud = Color(0xFFFFFFFF), // Pure bright crisp white
    mist = Color(0xFF94A3B8), // Secondary text
    placeholder = Color(0xFF64748B),
    blue = Color(0xFF4D90FE), // Lighter, vibrant, legible and prominent modern blue on dark canvas
    pillButtonBg = Color(0xFF3875D7),
    pillButtonBorder = Color(0xFF2B5EB5),
    pillButtonDisabledBg = Color(0xFF1E293B),
    pillButtonDisabledText = Color(0xFF475569),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFF0B101B),
    successGreen = Color(0xFF10B981),
    errorRed = Color(0xFFEF4444),
    navy = Color(0xFF1E293B),
    isDark = true
)

// Modern, sleek soft off-white & pure white cards light palette matching screenshots
val LightSyncSwiftColors = SyncSwiftColors(
    night = Color(0xFFF8FAFC), // Soft off-white screen canvas
    cardSurface = Color(0xFFFFFFFF), // Pure crisp white card surface
    inputBg = Color(0xFFF1F5F9), // Input and chip background
    inputBorder = Color(0xFFE2E8F0), // Crisp soft slate border
    borderHighlight = Color(0x331D58D8),
    cloud = Color(0xFF0F172A), // Deep solid pitch black for ultra-crisp readability
    mist = Color(0xFF64748B), // Slate secondary text
    placeholder = Color(0xFF94A3B8),
    blue = Color(0xFF1D58D8), // Darker, rich, high-contrast and prominent blue on white canvas
    pillButtonBg = Color(0xFF1D58D8), // Vibrant rich blue button background
    pillButtonBorder = Color(0xFF1544B0),
    pillButtonDisabledBg = Color(0xFFE2E8F0),
    pillButtonDisabledText = Color(0xFF94A3B8),
    pillButtonText = Color(0xFFFFFFFF),
    deepInk = Color(0xFFFFFFFF),
    successGreen = Color(0xFF10B981),
    errorRed = Color(0xFFEF4444),
    navy = Color(0xFFF1F5F9),
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
        displayLarge = baseTypography.displayLarge.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = font, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    )

    CompositionLocalProvider(
        LocalSyncSwiftColors provides colors,
        LocalThemeMode provides themeMode,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(
            fontFamily = font,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

