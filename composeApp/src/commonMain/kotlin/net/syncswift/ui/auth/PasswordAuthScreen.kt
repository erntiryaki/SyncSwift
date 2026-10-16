package net.syncswift.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.auth.components.AuthPage
import net.syncswift.ui.components.CustomCheckbox
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.PasswordRequirements
import net.syncswift.ui.components.PillField
import net.syncswift.ui.theme.Blue
import net.syncswift.ui.theme.Mist

@Composable
fun PasswordAuthScreen(
    copy: Copy,
    onBack: () -> Unit,
    done: () -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    AuthPage(
        title = copy.passwordTitle,
        detail = copy.passwordDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 1,
        onBack = onBack
    ) {
        PillField(
            hint = copy.passwordHint,
            value = password,
            update = { password = it },
            keyboard = KeyboardType.Password,
            secret = true,
            leadingIcon = { LockIcon(Modifier.size(20.dp), color = Mist) }
        )

        PasswordRequirements(password = password, copy = copy)

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { rememberMe = !rememberMe }
            ) {
                CustomCheckbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                Spacer(Modifier.width(8.dp))
                Text(copy.rememberMe, color = Mist, fontSize = 13.sp)
            }

            Text(
                text = copy.forgotPassword,
                color = Blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        MainPill(text = copy.signIn, action = done, enabled = isValidPassword(password))
    }
}
