package com.getsyncdrop.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.AppLanguage
import com.getsyncdrop.app.Copy
import com.getsyncdrop.app.ui.auth.components.AuthPage
import com.getsyncdrop.app.ui.auth.components.OtpCodeInput
import com.getsyncdrop.app.ui.components.MainPill
import com.getsyncdrop.app.ui.theme.Blue

@Composable
fun OtpAuthScreen(
    copy: Copy,
    email: String,
    verified: () -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AuthPage(
        title = copy.otpTitle,
        detail = copy.otpDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 1
    ) {
        OtpCodeInput(code = code, onCodeChange = { if (it.length <= 6) code = it.filter(Char::isDigit) })

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = copy.resendCode,
                color = Blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { }
            )
        }

        Spacer(Modifier.height(24.dp))
        MainPill(text = copy.continueText, action = verified, enabled = code.length == 6)
    }
}
