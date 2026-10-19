package net.syncswift.ui.auth.components

import androidx.compose.ui.graphics.Color

object AuthColors {
    // Koyu Tema: ana arka plan #121829, ikincil koyu ton #1E293B, ana buton mavisi #2563EB, vurgu açık mavi #60A5FA, açık gri metin #94A3B8, tamamen beyaz #FFFFFF.
    val DarkBackground = Color(0xFF121829)
    val DarkSecondary = Color(0xFF1E293B)
    val DarkMainButton = Color(0xFF2563EB)
    val DarkAccentBlue = Color(0xFF60A5FA)
    val DarkTextSecondary = Color(0xFF94A3B8)
    val DarkTextPrimary = Color(0xFFFFFFFF)

    // Açık Tema: ana arka plan #F4F6F9 (yumuşak off-white), ikincil arka plan #E9EDF4, ana metin #121829, ikincil metin #64748B, ana buton #2563EB, ince çizgiler #DDE3EA.
    val LightBackground = Color(0xFFF4F6F9)
    val LightSecondary = Color(0xFFE9EDF4)
    val LightTextPrimary = Color(0xFF121829)
    val LightTextSecondary = Color(0xFF64748B)
    val LightMainButton = Color(0xFF2563EB)
    val LightBorder = Color(0xFFDDE3EA)

    val button: Color = DarkMainButton

    fun background(isDark: Boolean): Color = if (isDark) DarkBackground else LightBackground
    fun secondary(isDark: Boolean): Color = if (isDark) DarkSecondary else LightSecondary
    fun textPrimary(isDark: Boolean): Color = if (isDark) DarkTextPrimary else LightTextPrimary
    fun textSecondary(isDark: Boolean): Color = if (isDark) DarkTextSecondary else LightTextSecondary
    fun button(isDark: Boolean = true): Color = DarkMainButton
    fun accent(isDark: Boolean): Color = if (isDark) DarkAccentBlue else DarkMainButton
    fun border(isDark: Boolean): Color = if (isDark) DarkSecondary else LightBorder
}
