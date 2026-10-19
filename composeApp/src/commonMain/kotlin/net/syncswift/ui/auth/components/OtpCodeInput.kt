package net.syncswift.ui.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import net.syncswift.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun OtpCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val char = code.getOrNull(index)?.toString() ?: ""
                        val isFocused = code.length == index || (code.length == 6 && index == 5)
                        val isFilled = char.isNotEmpty()

                        val borderColor = when {
                            isFocused -> AuthColors.button
                            isFilled -> AuthColors.button.copy(alpha = 0.55f)
                            else -> if (isDark) Color(0x2EFFFFFF) else Color(0xFFE2E8F0)
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuthColors.secondary(isDark))
                                .border(
                                    BorderStroke(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = borderColor
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            if (isFilled) {
                                Text(
                                    text = char,
                                    color = AuthColors.textPrimary(isDark),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isFocused) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(AuthColors.button)
                                )
                            } else {
                                Text(
                                    text = "•",
                                    color = AuthColors.textSecondary(isDark).copy(alpha = 0.35f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
