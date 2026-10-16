package net.syncswift.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.auth.components.AuthPage
import net.syncswift.ui.auth.components.OtpCodeInput
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

enum class OtpErrorState { None, Expired, Invalid }

@Composable
fun OtpAuthScreen(
    copy: Copy,
    email: String,
    expectedCode: String,
    onResendCode: () -> Unit,
    onBack: () -> Unit,
    verified: () -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var errorState by remember { mutableStateOf(OtpErrorState.None) }
    var secondsLeft by remember { mutableStateOf(300) }

    val errorMessage = when (errorState) {
        OtpErrorState.Expired -> copy.otpExpiredError
        OtpErrorState.Invalid -> copy.otpInvalid
        OtpErrorState.None -> null
    }

    // Live 1-second countdown ticker
    LaunchedEffect(email, expectedCode) {
        val now = currentTimeMs()
        val active = SessionManager.getActiveOtp(email, now)
        if (active != null) {
            secondsLeft = active.remainingSeconds(now)
        } else {
            secondsLeft = 300
        }
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val formattedTime = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    AuthPage(
        title = copy.otpTitle,
        detail = copy.otpDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 1,
        onBack = onBack
    ) {
        // Target recipient email chip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBg)
                    .border(
                        BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmailIcon(Modifier.size(16.dp), color = Blue)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = email,
                    color = Cloud,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OtpCodeInput(code = code, onCodeChange = {
            if (it.length <= 6) {
                code = it.filter(Char::isDigit)
                errorState = OtpErrorState.None
            }
        })

        Spacer(Modifier.height(20.dp))

        // Timer & Resend Button Container
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (secondsLeft > 0) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(InputBg.copy(alpha = 0.6f))
                        .border(
                            BorderStroke(1.dp, InputBorder.copy(alpha = 0.3f)),
                            CircleShape
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱ $formattedTime",
                        color = Cloud,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = copy.otpTimeRemaining.replace("%s", "").trim(),
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = copy.otpExpired,
                    color = Mist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = copy.resendCode,
                color = if (secondsLeft == 0) Blue else Mist.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = secondsLeft == 0) {
                        onResendCode()
                        secondsLeft = 300
                        code = ""
                        errorState = OtpErrorState.None
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ErrorRed.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠  ${errorMessage!!}",
                        color = ErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        MainPill(
            text = copy.continueText,
            action = {
                if (secondsLeft == 0) {
                    errorState = OtpErrorState.Expired
                } else if (code.trim() == expectedCode.trim()) {
                    verified()
                } else {
                    errorState = OtpErrorState.Invalid
                }
            },
            enabled = code.length == 6
        )
    }
}
