package com.getsyncdrop.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.ui.theme.*

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
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = DeepInk,
            disabledContainerColor = InputBg,
            disabledContentColor = Placeholder.copy(alpha = 0.6f)
        ),
        elevation = null
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}
