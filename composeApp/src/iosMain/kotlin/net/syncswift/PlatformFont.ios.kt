package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import net.syncswift.resources.Res
import net.syncswift.resources.plus_jakarta_sans_variable

@Composable
actual fun platformFontFamily(): FontFamily {
    return FontFamily(Font(Res.font.plus_jakarta_sans_variable))
}
