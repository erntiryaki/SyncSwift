package com.getsyncdrop.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getsyncdrop.app.*
import com.getsyncdrop.app.ui.auth.components.AuthPage
import com.getsyncdrop.app.ui.components.BoxlessField
import com.getsyncdrop.app.ui.components.MainPill
import com.getsyncdrop.app.ui.components.PasswordRequirements

@Composable
fun ProfileAuthScreen(
    copy: Copy,
    finished: () -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    AuthPage(
        title = copy.profileTitle,
        detail = copy.profileDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 2
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                BoxlessField(
                    hint = copy.firstName,
                    value = name,
                    update = { name = it }
                )
            }
            Box(Modifier.weight(1f)) {
                BoxlessField(
                    hint = copy.lastName,
                    value = surname,
                    update = { surname = it }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        BoxlessField(
            hint = copy.passwordHint,
            value = password,
            update = { password = it },
            keyboard = KeyboardType.Password,
            secret = true
        )

        PasswordRequirements(password = password)

        Spacer(Modifier.height(14.dp))
        BoxlessField(
            hint = copy.confirmPassword,
            value = confirmation,
            update = { confirmation = it },
            keyboard = KeyboardType.Password,
            secret = true
        )

        Spacer(Modifier.height(28.dp))
        MainPill(
            text = copy.continueText,
            action = finished,
            enabled = name.isNotBlank() && surname.isNotBlank() && isValidPassword(password) && password == confirmation
        )
    }
}
