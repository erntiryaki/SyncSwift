package net.syncswift.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.syncswift.*
import net.syncswift.ui.auth.components.AuthPage
import net.syncswift.ui.components.BoxlessField
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.PasswordRequirements

@Composable
fun ProfileAuthScreen(
    copy: Copy,
    onBack: () -> Unit,
    finished: (firstName: String, lastName: String, password: String) -> Unit,
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
        heroPage = 2,
        onBack = onBack
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

        PasswordRequirements(password = password, copy = copy)

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
            action = { finished(name.trim(), surname.trim(), password) },
            enabled = name.isNotBlank() && surname.isNotBlank() && isValidPassword(password) && password == confirmation
        )
    }
}
