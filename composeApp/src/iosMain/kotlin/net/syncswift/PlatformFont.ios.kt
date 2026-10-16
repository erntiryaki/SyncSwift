package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import net.syncswift.resources.Res
import net.syncswift.resources.nunito_variable

@Composable
actual fun platformFontFamily(): FontFamily {
    return try {
        FontFamily(Font(Res.font.nunito_variable))
    } catch (e: Throwable) {
        FontFamily.SansSerif
    }
}
