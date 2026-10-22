package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

@Composable
actual fun platformFontFamily(): FontFamily = FontFamily(Font(R.font.plus_jakarta_sans_variable))
