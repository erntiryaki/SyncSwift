package com.getsyncdrop.app.ui.auth.components

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
import com.getsyncdrop.app.ui.theme.*

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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val char = code.getOrNull(index)?.toString() ?: ""
                        val isFocused = code.length == index || (code.length == 6 && index == 5)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.width(42.dp)
                        ) {
                            Text(
                                text = char,
                                color = if (isFocused) Blue else Cloud,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(
                                thickness = if (isFocused) 2.5.dp else 1.5.dp,
                                color = if (isFocused) Blue else Mist.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        )
    }
}
