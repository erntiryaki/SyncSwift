package com.getsyncdrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.EyeIcon
import com.getsyncdrop.app.platformFontFamily
import com.getsyncdrop.app.ui.theme.*

@Composable
fun PillField(
    hint: String,
    value: String,
    update: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(InputBg)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(12.dp))
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = Placeholder,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = update,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Cloud,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = platformFontFamily()
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (secret && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
                )
            }

            if (secret && value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { passwordVisible = !passwordVisible }
                ) {
                    EyeIcon(Modifier.size(20.dp), color = Mist, visible = passwordVisible)
                }
            }
        }
    }
}

@Composable
fun BoxlessField(
    hint: String,
    value: String,
    update: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = Placeholder,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = update,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Cloud,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = platformFontFamily()
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                        visualTransformation = if (secret && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
                    )
                }

                if (secret && value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { passwordVisible = !passwordVisible }
                    ) {
                        EyeIcon(Modifier.size(20.dp), color = Mist, visible = passwordVisible)
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Mist.copy(alpha = 0.3f))
    }
}
