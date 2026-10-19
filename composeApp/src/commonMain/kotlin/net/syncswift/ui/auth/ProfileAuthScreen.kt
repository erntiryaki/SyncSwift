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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.auth.components.AuthBottomSheet
import net.syncswift.ui.auth.components.AuthColors
import net.syncswift.ui.auth.components.AuthPage
import net.syncswift.ui.auth.components.AuthPrimaryButton
import net.syncswift.ui.auth.components.AuthUnderlineField
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun ProfileAuthScreen(
    copy: Copy,
    onBack: () -> Unit,
    finished: (firstName: String, lastName: String, password: String) -> Unit,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark

    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(validationError) {
        if (validationError != null) {
            isSubmitting = false
        }
    }

    BackHandler { onBack() }

    val min8 = password.length >= 8
    val upper = password.any { it.isUpperCase() }
    val lower = password.any { it.isLowerCase() }
    val special = password.any { !it.isLetterOrDigit() }
    val isPasswordValid = min8 && upper && lower && special
    val isFormValid = name.isNotBlank() && surname.isNotBlank() && isPasswordValid && password == confirmation

    val handleSubmit = {
        if (!isSubmitting) {
            when {
                name.isBlank() || surname.isBlank() -> {
                    validationError = if (language == AppLanguage.Turkish) "Lütfen adınızı ve soyadınızı girin." else "Please enter your first and last name."
                }
                !isPasswordValid -> {
                    validationError = when (language) {
                        AppLanguage.Turkish -> "Şifreniz belirtilen güvenlik kurallarını sağlamalıdır."
                        AppLanguage.German -> "Ihr Passwort muss alle Sicherheitsanforderungen erfüllen."
                        AppLanguage.French -> "Votre mot de passe doit respecter toutes les exigences de sécurité."
                        AppLanguage.Spanish -> "Tu contraseña debe cumplir con todos los requisitos de seguridad."
                        AppLanguage.Italian -> "La tua password deve soddisfare tutti i requisiti di sicurezza."
                        AppLanguage.Portuguese -> "Sua senha deve atender a todos os requisitos de segurança."
                        AppLanguage.Russian -> "Ваш пароль должен соответствовать всем требованиям безопасности."
                        AppLanguage.Arabic -> "يجب أن تستوفي كلمة المرور جميع متطلبات الأمان."
                        else -> "Your password must meet all security requirements."
                    }
                }
                password != confirmation -> {
                    validationError = copy.passwordsDoNotMatch
                }
                else -> {
                    isSubmitting = true
                    finished(name.trim(), surname.trim(), password)
                }
            }
        }
    }

    AuthPage(
        title = copy.profileTitle,
        detail = copy.profileDetail,
        language = language,
        setLanguage = setLanguage,
        showHero = false,
        onBack = onBack
    ) {
        Spacer(Modifier.height(6.dp))

        // First Name & Last Name (Side by Side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.weight(1f)) {
                AuthUnderlineField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = copy.firstName,
                    isDark = isDark,
                    leadingIcon = {
                        UserIcon(Modifier.size(19.dp), color = AuthColors.textSecondary(isDark))
                    }
                )
            }
            Box(Modifier.weight(1f)) {
                AuthUnderlineField(
                    value = surname,
                    onValueChange = { surname = it },
                    placeholder = copy.lastName,
                    isDark = isDark,
                    leadingIcon = {
                        UserIcon(Modifier.size(19.dp), color = AuthColors.textSecondary(isDark))
                    }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Password Field
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

        // Password Requirements Checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfileRequirementItem(label = copy.reqMin8, fulfilled = min8, isDark = isDark)
            ProfileRequirementItem(label = copy.reqUpper, fulfilled = upper, isDark = isDark)
            ProfileRequirementItem(label = copy.reqLower, fulfilled = lower, isDark = isDark)
            ProfileRequirementItem(label = copy.reqSpecial, fulfilled = special, isDark = isDark)
        }

        Spacer(Modifier.height(18.dp))

        // Confirm Password Field
        AuthUnderlineField(
            value = confirmation,
            onValueChange = { confirmation = it },
            placeholder = copy.confirmPassword,
            isDark = isDark,
            isPassword = true,
            passwordVisible = confirmPasswordVisible,
            leadingIcon = {
                LockIcon(Modifier.size(19.dp), color = AuthColors.textSecondary(isDark))
            },
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { confirmPasswordVisible = !confirmPasswordVisible }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EyeIcon(
                        modifier = Modifier.size(18.dp),
                        color = AuthColors.textSecondary(isDark),
                        visible = confirmPasswordVisible
                    )
                }
            }
        )

        if (confirmation.isNotEmpty() && password != confirmation) {
            Text(
                text = copy.passwordsDoNotMatch,
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        Spacer(Modifier.height(30.dp))

        AuthPrimaryButton(
            text = copy.continueText,
            onClick = handleSubmit,
            enabled = isFormValid && !isSubmitting,
            isLoading = isSubmitting
        )
    }

    // Validation Alert Bottom Sheet (replaces dialogs/popups)
    AuthBottomSheet(
        visible = validationError != null,
        onDismiss = { validationError = null }
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
                Text("⚠", fontSize = 26.sp, color = Color(0xFFEF4444))
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when (language) {
                    AppLanguage.Turkish -> "Bilgileri Kontrol Edin"
                    AppLanguage.German -> "Angaben überprüfen"
                    AppLanguage.French -> "Vérifiez vos informations"
                    AppLanguage.Spanish -> "Comprueba tu información"
                    AppLanguage.Italian -> "Controlla le informazioni"
                    AppLanguage.Portuguese -> "Verifique suas informações"
                    AppLanguage.Russian -> "Проверьте данные"
                    AppLanguage.Arabic -> "تحقق من بياناتك"
                    else -> "Check Your Information"
                },
                color = AuthColors.textPrimary(isDark),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = validationError ?: "",
                color = AuthColors.textSecondary(isDark),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(24.dp))

            AuthPrimaryButton(
                text = if (language == AppLanguage.Turkish) "Tamam" else "OK",
                onClick = { validationError = null }
            )
        }
    }
}

@Composable
private fun ProfileRequirementItem(
    label: String,
    fulfilled: Boolean,
    isDark: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(
                    if (fulfilled) Color(0xFF10B981)
                    else (if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (fulfilled) Color(0xFF10B981)
                        else (if (isDark) Color(0x33FFFFFF) else Color(0xFFCBD5E1))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (fulfilled) {
                Text("✓", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = label,
            color = if (fulfilled) (if (isDark) Color(0xFF34D399) else Color(0xFF059669)) else AuthColors.textSecondary(isDark),
            fontSize = 12.sp,
            fontWeight = if (fulfilled) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
