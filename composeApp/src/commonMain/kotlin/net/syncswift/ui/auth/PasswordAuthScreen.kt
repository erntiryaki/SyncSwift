package net.syncswift.ui.auth

import androidx.compose.foundation.background
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
import net.syncswift.*
import net.syncswift.ui.auth.components.*
import net.syncswift.ui.components.CustomCheckbox
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun PasswordAuthScreen(
    copy: Copy,
    onBack: () -> Unit,
    done: (onFinished: () -> Unit) -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var showForgotPasswordSheet by remember { mutableStateOf(false) }
    var showMembershipTermsSheet by remember { mutableStateOf(false) }
    var showUsageTermsSheet by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    AuthPage(
        title = copy.passwordTitle,
        detail = copy.passwordDetail,
        language = language,
        setLanguage = setLanguage,
        showHero = false,
        onBack = onBack
    ) {
        Spacer(Modifier.height(8.dp))

        AuthUnderlineField(
            value = password,
            onValueChange = { password = it },
            placeholder = copy.passwordHint,
            isDark = isDark,
            isPassword = true,
            passwordVisible = passwordVisible,
            leadingIcon = {
                LockIcon(Modifier.size(19.dp), color = AuthColors.textSecondary(isDark))
            },
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EyeIcon(
                        modifier = Modifier.size(18.dp),
                        color = AuthColors.textSecondary(isDark),
                        visible = passwordVisible
                    )
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { rememberMe = !rememberMe }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                CustomCheckbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.rememberMe,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.sp
                )
            }

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

        Spacer(Modifier.height(28.dp))

        AuthPrimaryButton(
            text = copy.signIn,
            onClick = {
                if (!isSubmitting) {
                    isSubmitting = true
                    done { isSubmitting = false }
                }
            },
            enabled = password.isNotBlank() && !isSubmitting,
            isLoading = isSubmitting
        )

        Spacer(Modifier.height(26.dp))

        AuthTermsDisclaimer(
            copy = copy,
            isDark = isDark,
            onOpenMembership = { showMembershipTermsSheet = true },
            onOpenUsage = { showUsageTermsSheet = true }
        )
    }

    // Forgot Password Bottom Sheet (replaces dialogs/popups)
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
                text = copy.forgotPassword,
                color = AuthColors.textPrimary(isDark),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (language == AppLanguage.Turkish) {
                    "Şifrenizi sıfırlamak için lütfen bir önceki ekrana dönerek doğrulama kodu ile giriş yapmayı seçin."
                } else {
                    "To reset your password, please return to the previous screen and choose login via verification code."
                },
                color = AuthColors.textSecondary(isDark),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = "Tamam",
                onClick = { showForgotPasswordSheet = false }
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
