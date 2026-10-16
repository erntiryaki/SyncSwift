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
import net.syncswift.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun OtpCodeInput(
    code: String,
    onCodeChange: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val char = code.getOrNull(index)?.toString() ?: ""
                        val isFocused = code.length == index || (code.length == 6 && index == 5)
                        val isFilled = char.isNotEmpty()

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(44.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(InputBg)
                                .border(
                                    BorderStroke(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = when {
                                            isFocused -> Blue
                                            isFilled -> Blue.copy(alpha = 0.45f)
                                            else -> InputBorder.copy(alpha = 0.35f)
                                        }
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            if (isFilled) {
                                Text(
                                    text = char,
                                    color = Cloud,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            } else if (isFocused) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(Blue)
                                )
                            } else {
                                Text(
                                    text = "•",
                                    color = Mist.copy(alpha = 0.3f),
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
