package com.getsyncdrop.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.*
import com.getsyncdrop.app.ui.auth.components.AuthPage
import com.getsyncdrop.app.ui.components.MainPill
import com.getsyncdrop.app.ui.components.OrDivider
import com.getsyncdrop.app.ui.components.PillField
import com.getsyncdrop.app.ui.components.SocialPill
import com.getsyncdrop.app.ui.theme.Blue
import com.getsyncdrop.app.ui.theme.Mist

@Composable
fun EmailAuthScreen(
    copy: Copy,
    email: String,
    updateEmail: (String) -> Unit,
    next: () -> Unit,
    social: () -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    val launchNativeGoogleAuth = rememberGoogleAuthLauncher(
        webClientId = SyncDropConfig.GOOGLE_WEB_CLIENT_ID,
        onSuccess = { idToken ->
            social()
        },
        onError = { err ->
            social()
        }
    )

    AuthPage(
        title = copy.emailTitle,
        detail = copy.emailDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 0
    ) {
        PillField(
            hint = copy.emailHint,
            value = email,
            update = updateEmail,
            keyboard = KeyboardType.Email,
            leadingIcon = { EmailIcon(Modifier.size(20.dp), color = Mist) }
        )
        Spacer(Modifier.height(16.dp))
        MainPill(
            text = copy.continueText,
            action = next,
            enabled = email.contains("@") && email.contains(".")
        )
        OrDivider(copy.or)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SocialPill(
                label = copy.google,
                provider = IdentityProvider.Google,
                light = true,
                action = launchNativeGoogleAuth,
                modifier = Modifier.weight(1f)
            )
            if (currentPlatform() == PlatformFlavor.Ios) {
                SocialPill(
                    label = copy.apple,
                    provider = IdentityProvider.Apple,
                    light = false,
                    action = social,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = copy.continueLocally,
                color = Blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { social() }
            )
        }
    }
}
