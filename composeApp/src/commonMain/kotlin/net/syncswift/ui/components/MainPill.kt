package net.syncswift.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.platformFontFamily
import net.syncswift.ui.theme.*

@Composable
fun MainPill(
    text: String,
    action: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = action,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(27.dp),
        border = BorderStroke(0.5.dp, PillButtonBorder.copy(alpha = 0.5f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = PillButtonBg,
            contentColor = PillButtonText,
            disabledContainerColor = PillButtonDisabledBg,
            disabledContentColor = PillButtonDisabledText
        ),
        elevation = null
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = platformFontFamily()
        )
    }
}
