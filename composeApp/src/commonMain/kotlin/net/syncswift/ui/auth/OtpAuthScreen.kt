package net.syncswift.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.syncswift.*
import net.syncswift.ui.auth.components.AuthBottomSheet
import net.syncswift.ui.auth.components.AuthColors
import net.syncswift.ui.auth.components.AuthPage
import net.syncswift.ui.auth.components.AuthPrimaryButton
import net.syncswift.ui.auth.components.OtpCodeInput
import net.syncswift.ui.theme.LocalSyncSwiftColors

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
    val isDark = LocalSyncSwiftColors.current.isDark
    var code by remember { mutableStateOf("") }
    var errorState by remember { mutableStateOf(OtpErrorState.None) }
    var secondsLeft by remember { mutableStateOf(300) }
    var showResentSheet by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    BackHandler { onBack() }

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

    val handleVerify = {
        if (!isVerifying) {
            if (secondsLeft == 0) {
                errorState = OtpErrorState.Expired
            } else if (code.trim() == expectedCode.trim()) {
                isVerifying = true
                verified()
            } else {
                errorState = OtpErrorState.Invalid
            }
        }
    }

    AuthPage(
        title = copy.otpTitle,
        language = language,
        setLanguage = setLanguage,
        showHero = false,
        onBack = onBack,
        subtitleSlot = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = copy.otpDetail,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
                // Recipient Email with edit option
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = email,
                        color = AuthColors.textPrimary(isDark),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = copy.editEmail,
                        color = AuthColors.button,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Spacer(Modifier.height(18.dp))

        // OTP 6-Digit input boxes
        OtpCodeInput(
            code = code,
            onCodeChange = {
                if (it.length <= 6) {
                    code = it.filter(Char::isDigit)
                    errorState = OtpErrorState.None
                    if (code.length == 6) {
                        // Auto-check on full code entry
                        if (code.trim() == expectedCode.trim() && secondsLeft > 0) {
                            if (!isVerifying) {
                                isVerifying = true
                                verified()
                            }
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(28.dp))

        // Timer & Resend Button Container
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (secondsLeft > 0) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AuthColors.secondary(isDark))
                        .border(
                            BorderStroke(1.dp, if (isDark) Color(0x2EFFFFFF) else Color(0xFFE2E8F0)),
                            CircleShape
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⏱ $formattedTime",
                        color = AuthColors.textPrimary(isDark),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = copy.otpTimeRemaining.replace("%s", "").trim(),
                        color = AuthColors.textSecondary(isDark),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = copy.otpExpired,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = copy.resendCode,
                color = if (secondsLeft == 0) AuthColors.button else AuthColors.textSecondary(isDark).copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = secondsLeft == 0) {
                        onResendCode()
                        secondsLeft = 300
                        code = ""
                        errorState = OtpErrorState.None
                        showResentSheet = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        AuthPrimaryButton(
            text = copy.continueText,
            onClick = handleVerify,
            enabled = code.length == 6 && !isVerifying,
            isLoading = isVerifying
        )
    }

    // Bottom Sheet for Invalid / Expired OTP errors (replacing popup/dialog)
    AuthBottomSheet(
        visible = errorState != OtpErrorState.None,
        onDismiss = { errorState = OtpErrorState.None }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                WarningVectorIcon(Modifier.size(26.dp), color = Color(0xFFEF4444))
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (errorState == OtpErrorState.Expired) copy.otpExpired else copy.otpInvalid,
                color = AuthColors.textPrimary(isDark),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (errorState == OtpErrorState.Expired) copy.otpExpiredError else copy.otpInvalid,
                color = AuthColors.textSecondary(isDark),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            if (errorState == OtpErrorState.Expired) {
                AuthPrimaryButton(
                    text = copy.resendCode,
                    onClick = {
                        errorState = OtpErrorState.None
                        onResendCode()
                        secondsLeft = 300
                        code = ""
                    }
                )
            } else {
                AuthPrimaryButton(
                    text = copy.continueText,
                    onClick = { errorState = OtpErrorState.None }
                )
            }
        }
    }

    // Bottom Sheet for Resent OTP confirmation
    AuthBottomSheet(
        visible = showResentSheet,
        onDismiss = { showResentSheet = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AuthColors.button.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                EmailIcon(Modifier.size(24.dp), color = AuthColors.button)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = copy.resendCode,
                color = AuthColors.textPrimary(isDark),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "$email adresine yeni bir doğrulama kodu gönderildi.",
                color = AuthColors.textSecondary(isDark),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = "Tamam",
                onClick = { showResentSheet = false }
            )
        }
    }
}
