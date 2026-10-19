package net.syncswift.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.ui.auth.components.*
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun EmailAuthScreen(
    copy: Copy,
    email: String,
    updateEmail: (String) -> Unit,
    onSignInWithPassword: (email: String, password: String, onFinished: () -> Unit) -> Unit,
    onSendOtpAndContinue: (email: String, isPasswordReset: Boolean) -> Unit,
    onGoogleSignIn: (GoogleUserData) -> Unit,
    onAppleSignIn: (AppleUserData) -> Unit = {},
    onContinueLocally: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    errorMessage: String? = null,
    clearError: () -> Unit = {}
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val scope = rememberCoroutineScope()
    val isAndroid = remember { currentPlatform() == PlatformFlavor.Android }

    var isPasswordStep by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isAppleLoading by remember { mutableStateOf(false) }

    var showOtpConfirmSheet by remember { mutableStateOf(false) }
    var pendingEmailToSend by remember { mutableStateOf("") }

    var showForgotPasswordSheet by remember { mutableStateOf(false) }
    var showMembershipTermsSheet by remember { mutableStateOf(false) }
    var showUsageTermsSheet by remember { mutableStateOf(false) }

    var inlineError by remember { mutableStateOf<String?>(null) }
    val activeError = inlineError ?: errorMessage

    // Automatically reset all loading spinners when an error arrives or changes
    LaunchedEffect(errorMessage, inlineError) {
        if (errorMessage != null || inlineError != null) {
            isSubmitting = false
            isGoogleLoading = false
            isAppleLoading = false
        }
    }

    // Social Login Launchers
    val launchNativeGoogleAuth = rememberGoogleAuthLauncher(
        webClientId = SyncSwiftConfig.GOOGLE_WEB_CLIENT_ID,
        onSuccess = { userData ->
            isGoogleLoading = false
            onGoogleSignIn(userData)
        },
        onError = { _ ->
            isGoogleLoading = false
        }
    )

    val launchNativeAppleAuth = rememberAppleAuthLauncher(
        onSuccess = { userData ->
            isAppleLoading = false
            onAppleSignIn(userData)
        },
        onError = { _ ->
            isAppleLoading = false
        }
    )

    val isAnyLoading = isSubmitting || isGoogleLoading || isAppleLoading

    // Submit Email handler
    val submitEmail = {
        if (!isAnyLoading && email.contains("@") && email.contains(".")) {
            clearError()
            inlineError = null
            isSubmitting = true
            scope.launch {
                try {
                    val trimmed = email.trim().lowercase()
                    val exists = SupabaseNetwork.checkUserExists(trimmed)
                    isSubmitting = false
                    if (exists) {
                        isPasswordStep = true
                    } else {
                        pendingEmailToSend = trimmed
                        showOtpConfirmSheet = true
                    }
                } catch (e: Exception) {
                    isSubmitting = false
                    pendingEmailToSend = email.trim().lowercase()
                    showOtpConfirmSheet = true
                }
            }
        }
    }

    // Submit Password handler
    val submitPassword = {
        if (!isAnyLoading && password.isNotBlank()) {
            clearError()
            inlineError = null
            isSubmitting = true
            onSignInWithPassword(email.trim().lowercase(), password) {
                isSubmitting = false
            }
        }
    }

    BackHandler(enabled = isPasswordStep) {
        isPasswordStep = false
        password = ""
        inlineError = null
        clearError()
    }

    if (onBack != null && !isPasswordStep) {
        BackHandler(enabled = true) {
            onBack()
        }
    }

    AuthPage(
        title = if (isPasswordStep) copy.enterPasswordTitle else copy.authHeroHeadline,
        detail = if (isPasswordStep) "" else copy.authHeroSubtitle,
        subtitleSlot = if (isPasswordStep) {
            {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = email,
                        color = AuthColors.textSecondary(isDark),
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = copy.editEmail,
                        color = AuthColors.button,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                isPasswordStep = false
                                password = ""
                                inlineError = null
                                clearError()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        } else null,
        language = language,
        setLanguage = setLanguage,
        showHero = true,
        onBack = if (isPasswordStep) {
            {
                isPasswordStep = false
                password = ""
                inlineError = null
                clearError()
            }
        } else onBack
    ) {
        if (!isPasswordStep) {
            // ==========================================
            // SCREEN 1: EMAIL STEP
            // ==========================================
            AuthUnderlineField(
                value = email,
                onValueChange = {
                    updateEmail(it)
                    if (activeError != null) {
                        inlineError = null
                        clearError()
                    }
                },
                placeholder = copy.emailHint,
                isDark = isDark,
                leadingIcon = {
                    EmailIcon(Modifier.size(20.dp), color = AuthColors.textSecondary(isDark))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { submitEmail() })
            )

            if (activeError != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeError,
                        color = Color(0xFFEF4444),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Primary Button: "Devam Et" with arrow
            AuthPrimaryButton(
                text = copy.continueText,
                onClick = { submitEmail() },
                enabled = !isAnyLoading && email.contains("@") && email.contains("."),
                isLoading = isSubmitting
            )

            Spacer(Modifier.height(20.dp))

            // "veya" Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AuthColors.border(isDark))
                )
                Text(
                    text = copy.or,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AuthColors.border(isDark))
                )
            }

            Spacer(Modifier.height(20.dp))

            // Google Button (Wide on Android with "Google ile devam et", Circular on iOS)
            if (isAndroid) {
                AuthGoogleWideButton(
                    text = copy.google,
                    onClick = {
                        isGoogleLoading = true
                        launchNativeGoogleAuth()
                    },
                    isDark = isDark,
                    enabled = !isAnyLoading,
                    isLoading = isGoogleLoading
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AuthColors.secondary(isDark))
                            .border(
                                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else AuthColors.border(isDark)),
                                CircleShape
                            )
                            .clickable(enabled = !isAnyLoading) {
                                isGoogleLoading = true
                                launchNativeGoogleAuth()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SocialProviderIcon(IdentityProvider.Google, Modifier.size(22.dp), Color.Unspecified)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // "Local Kullanıcı olarak devam et"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = copy.continueLocally,
                    color = AuthColors.button,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isAnyLoading) { onContinueLocally() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Terms Disclaimer at the bottom
            AuthTermsDisclaimer(
                copy = copy,
                isDark = isDark,
                onOpenMembership = { showMembershipTermsSheet = true },
                onOpenUsage = { showUsageTermsSheet = true }
            )
        } else {
            // ==========================================
            // SCREEN 2: PASSWORD STEP
            // ==========================================
            AuthUnderlineField(
                value = password,
                onValueChange = { password = it },
                placeholder = copy.passwordHint,
                isDark = isDark,
                isPassword = true,
                passwordVisible = passwordVisible,
                leadingIcon = {
                    LockIcon(Modifier.size(20.dp), color = AuthColors.textSecondary(isDark))
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(4.dp)
                    ) {
                        EyeIcon(
                            modifier = Modifier.size(20.dp),
                            color = if (passwordVisible) AuthColors.accent(isDark) else AuthColors.textSecondary(isDark),
                            visible = passwordVisible
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitPassword() })
            )

            // "Şifremi unuttum?" Link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = copy.forgotPassword,
                    color = AuthColors.button,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showForgotPasswordSheet = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (activeError != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeError,
                        color = Color(0xFFEF4444),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Button: "Giriş Yap" with arrow
            AuthPrimaryButton(
                text = copy.signIn,
                onClick = { submitPassword() },
                enabled = !isAnyLoading && password.isNotBlank(),
                isLoading = isSubmitting
            )

            Spacer(Modifier.height(20.dp))

            // "veya" Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AuthColors.border(isDark))
                )
                Text(
                    text = copy.or,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AuthColors.border(isDark))
                )
            }

            Spacer(Modifier.height(20.dp))

            // Social Buttons: On Android, wide Google button only (NO Apple button on Android). On iOS, side-by-side Google & Apple.
            if (isAndroid) {
                AuthGoogleWideButton(
                    text = copy.google,
                    onClick = {
                        isGoogleLoading = true
                        launchNativeGoogleAuth()
                    },
                    isDark = isDark,
                    enabled = !isAnyLoading,
                    isLoading = isGoogleLoading
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    // Google
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AuthColors.secondary(isDark))
                            .border(
                                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else AuthColors.border(isDark)),
                                CircleShape
                            )
                            .clickable(enabled = !isAnyLoading) {
                                isGoogleLoading = true
                                launchNativeGoogleAuth()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SocialProviderIcon(IdentityProvider.Google, Modifier.size(22.dp), Color.Unspecified)
                    }

                    // Apple
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AuthColors.secondary(isDark))
                            .border(
                                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else AuthColors.border(isDark)),
                                CircleShape
                            )
                            .clickable(enabled = !isAnyLoading) {
                                isAppleLoading = true
                                launchNativeAppleAuth()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SocialProviderIcon(
                            IdentityProvider.Apple,
                            Modifier.size(22.dp),
                            if (isDark) Color.White else Color(0xFF121829)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // "Local Kullanıcı olarak devam et"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = copy.continueLocally,
                    color = AuthColors.button,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isAnyLoading) { onContinueLocally() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Terms Disclaimer at the bottom
            AuthTermsDisclaimer(
                copy = copy,
                isDark = isDark,
                onOpenMembership = { showMembershipTermsSheet = true },
                onOpenUsage = { showUsageTermsSheet = true }
            )
        }
    }

    // OTP Confirmation Bottom Sheet (replacing centered modal dialog!)
    AuthBottomSheet(
        visible = showOtpConfirmSheet,
        title = copy.otpConfirmTitle,
        isDark = isDark,
        onDismiss = { showOtpConfirmSheet = false }
    ) {
        Text(
            text = copy.otpConfirmMessage.replace("%s", pendingEmailToSend),
            color = AuthColors.textSecondary(isDark),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(22.dp))

        AuthPrimaryButton(
            text = copy.otpConfirmAction,
            onClick = {
                showOtpConfirmSheet = false
                onSendOtpAndContinue(pendingEmailToSend, false)
            }
        )

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable { showOtpConfirmSheet = false }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = copy.otpCancelAction,
                color = AuthColors.textSecondary(isDark),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Forgot Password Bottom Sheet
    AuthBottomSheet(
        visible = showForgotPasswordSheet,
        title = copy.forgotPassword,
        isDark = isDark,
        onDismiss = { showForgotPasswordSheet = false }
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
                LockIcon(Modifier.size(24.dp), color = AuthColors.button)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (language == AppLanguage.Turkish) {
                    "Şifrenizi sıfırlamak için e-posta adresinize bir doğrulama kodu gönderebiliriz."
                } else {
                    "We can send a verification code to your email address to reset your password."
                },
                color = AuthColors.textSecondary(isDark),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = if (language == AppLanguage.Turkish) "Doğrulama Kodu Gönder" else "Send Verification Code",
                onClick = {
                    showForgotPasswordSheet = false
                    pendingEmailToSend = email.trim().lowercase()
                    onSendOtpAndContinue(pendingEmailToSend, true)
                }
            )
        }
    }

    // Membership Terms Bottom Sheet
    AuthBottomSheet(
        visible = showMembershipTermsSheet,
        title = copy.termsOfServiceTitle,
        isDark = isDark,
        onDismiss = { showMembershipTermsSheet = false }
    ) {
        TermsSheetContent(
            sections = TermsAgreements.getMembershipTerms(language),
            isDark = isDark
        )

        Spacer(Modifier.height(16.dp))

        AuthPrimaryButton(
            text = "Tamam",
            onClick = { showMembershipTermsSheet = false }
        )
    }

    // Privacy & Usage Terms Bottom Sheet
    AuthBottomSheet(
        visible = showUsageTermsSheet,
        title = copy.privacyPolicyTitle,
        isDark = isDark,
        onDismiss = { showUsageTermsSheet = false }
    ) {
        TermsSheetContent(
            sections = TermsAgreements.getUsageTerms(language),
            isDark = isDark
        )

        Spacer(Modifier.height(16.dp))

        AuthPrimaryButton(
            text = "Tamam",
            onClick = { showUsageTermsSheet = false }
        )
    }
}
