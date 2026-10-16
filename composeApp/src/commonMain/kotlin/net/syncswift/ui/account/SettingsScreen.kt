package net.syncswift.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.PillField
import net.syncswift.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    copy: Copy,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    themeMode: AppThemeMode,
    setThemeMode: (AppThemeMode) -> Unit,
    userEmail: String,
    provider: IdentityProvider,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Password change states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordLoading by remember { mutableStateOf(false) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Account deletion modal states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteEmailInput by remember { mutableStateOf("") }
    var deleteLoading by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val isLocalUser = provider == IdentityProvider.Local || userEmail.isBlank()
    val isEmailAuth = provider == IdentityProvider.EmailPassword && !isLocalUser
    val isPasswordValid = isValidPassword(newPassword)
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val canSubmitPassword = isPasswordValid && passwordsMatch && currentPassword.isNotBlank() && !passwordLoading

    val isDeleteEmailMatch = deleteEmailInput.trim().equals(userEmail.trim(), ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(16.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(InputBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = copy.settingsTitle,
                color = Cloud,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // SECTION 1: Language Selection (Dil Seçimi - KUTUSUZ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLanguageDialog = true }
                .padding(vertical = 18.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBg.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                GlobeVectorIcon(Modifier.size(20.dp), color = Blue)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = copy.languageSelection,
                    color = Cloud,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = copy.languageSelectionDesc,
                    color = Mist,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = language.nativeName,
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                ChevronRightIcon(Modifier.size(15.dp), color = Mist.copy(alpha = 0.55f))
            }
        }

        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

        // SECTION 2: Theme Selection (Tema Seçimi - KUTUSUZ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showThemeDialog = true }
                .padding(vertical = 18.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InputBg.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                if (themeMode == AppThemeMode.Light) {
                    SunVectorIcon(Modifier.size(20.dp), color = Blue)
                } else {
                    MoonVectorIcon(Modifier.size(20.dp), color = Blue)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = copy.themeSelection,
                    color = Cloud,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = copy.themeSelectionDesc,
                    color = Mist,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (themeMode == AppThemeMode.Light) copy.lightTheme else copy.darkTheme,
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                ChevronRightIcon(Modifier.size(15.dp), color = Mist.copy(alpha = 0.55f))
            }
        }

        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))

        Spacer(Modifier.height(32.dp))

        // SECTION 2: Password Management (KUTUSUZ BOXLESS)
        if (isLocalUser) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNativeToast(copy.featureDisabledForLocal) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InputBg.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        KeyVectorIcon(Modifier.size(20.dp), color = Mist.copy(alpha = 0.4f))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = copy.changePassword,
                                color = Cloud.copy(alpha = 0.45f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(InputBg.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = copy.registeredAccountsOnly,
                                    color = Mist.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = copy.featureDisabledForLocal,
                            color = Mist.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else if (isEmailAuth) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(InputBg.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        KeyVectorIcon(Modifier.size(20.dp), color = Blue)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = copy.changePassword,
                            color = Cloud,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = copy.changePasswordDesc,
                            color = Mist,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Success Feedback
                if (passwordSuccess != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckIcon(Modifier.size(16.dp), color = SuccessGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = passwordSuccess ?: "",
                                color = SuccessGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Error Feedback
                if (passwordError != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ErrorRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = passwordError ?: "",
                            color = ErrorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Mevcut Şifre Field
                PillField(
                    hint = copy.currentPasswordHint,
                    value = currentPassword,
                    update = {
                        currentPassword = it
                        passwordSuccess = null
                        passwordError = null
                    },
                    keyboard = KeyboardType.Password,
                    secret = true
                )

                Spacer(Modifier.height(10.dp))

                // New Password Field
                PillField(
                    hint = copy.newPasswordHint,
                    value = newPassword,
                    update = {
                        newPassword = it
                        passwordSuccess = null
                        passwordError = null
                    },
                    keyboard = KeyboardType.Password,
                    secret = true
                )

                Spacer(Modifier.height(10.dp))

                // Confirm New Password Field
                PillField(
                    hint = copy.newPasswordConfirmHint,
                    value = confirmPassword,
                    update = {
                        confirmPassword = it
                        passwordSuccess = null
                        passwordError = null
                    },
                    keyboard = KeyboardType.Password,
                    secret = true
                )

                Spacer(Modifier.height(12.dp))

                // Password Rules Checklist
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputBg.copy(alpha = 0.45f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RuleRow(label = copy.reqMin8, valid = newPassword.length >= 8)
                    RuleRow(label = copy.reqUpper, valid = newPassword.any { it.isUpperCase() })
                    RuleRow(label = copy.reqLower, valid = newPassword.any { it.isLowerCase() })
                    RuleRow(label = copy.reqSpecial, valid = newPassword.any { !it.isLetterOrDigit() })
                }

                Spacer(Modifier.height(18.dp))

                // Update Button
                MainPill(
                    text = if (passwordLoading) "..." else copy.updatePasswordAction,
                    enabled = canSubmitPassword,
                    action = {
                        if (currentPassword.isBlank()) {
                            passwordError = copy.currentPasswordHint
                            return@MainPill
                        }
                        if (!passwordsMatch) {
                            passwordError = copy.passwordsDoNotMatch
                            return@MainPill
                        }
                        if (!isPasswordValid) {
                            passwordError = copy.wrongPassword
                            return@MainPill
                        }
                        passwordLoading = true
                        passwordError = null
                        passwordSuccess = null

                        scope.launch {
                            // Verify current password first with Supabase
                            val isCurrentCorrect = SupabaseNetwork.signInWithPassword(userEmail, currentPassword)
                            if (!isCurrentCorrect) {
                                passwordLoading = false
                                passwordError = copy.incorrectCurrentPassword
                                return@launch
                            }

                            val ok = SupabaseNetwork.updateUserPassword(userEmail, newPassword)
                            passwordLoading = false
                            if (ok) {
                                passwordSuccess = copy.passwordUpdatedSuccess
                                showNativeToast(copy.passwordUpdatedSuccess)
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                            } else {
                                passwordError = copy.deleteAccountError
                            }
                        }
                    }
                )
            }
        } else if (!isLocalUser) {
            // Google authenticated note (kutusuz)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(InputBg.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    ShieldVectorIcon(Modifier.size(20.dp), color = Blue)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = copy.googleManagedNote,
                    color = Mist,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(thickness = 1.dp, color = InputBorder.copy(alpha = 0.22f))
        Spacer(Modifier.height(32.dp))

        // SECTION 3: Danger Zone - Delete Account (KUTUSUZ BOXLESS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isLocalUser) { showNativeToast(copy.featureDisabledForLocal) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLocalUser) InputBg.copy(alpha = 0.35f) else ErrorRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    TrashIcon(Modifier.size(20.dp), color = if (isLocalUser) Mist.copy(alpha = 0.4f) else ErrorRed)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = copy.deleteAccount,
                            color = if (isLocalUser) Cloud.copy(alpha = 0.45f) else ErrorRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                        if (isLocalUser) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(InputBg.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = copy.registeredAccountsOnly,
                                    color = Mist.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isLocalUser) copy.featureDisabledForLocal else copy.deleteAccountDesc,
                        color = Mist.copy(alpha = if (isLocalUser) 0.4f else 1f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isLocalUser) {
                        showNativeToast(copy.featureDisabledForLocal)
                    } else {
                        deleteEmailInput = ""
                        deleteError = null
                        showDeleteDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocalUser) InputBg.copy(alpha = 0.3f) else ErrorRed.copy(alpha = 0.12f),
                    disabledContainerColor = InputBg.copy(alpha = 0.3f),
                    contentColor = if (isLocalUser) Mist.copy(alpha = 0.4f) else ErrorRed,
                    disabledContentColor = Mist.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, if (isLocalUser) InputBorder.copy(alpha = 0.25f) else ErrorRed.copy(alpha = 0.4f)),
                elevation = null
            ) {
                Text(
                    text = copy.deleteAccount,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(60.dp))
    }

    // Language Selection Modal (Universal Flat Minimalist SyncSwiftModal)
    if (showLanguageDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showLanguageDialog = false },
            title = copy.selectLanguage,
            titleIcon = { GlobeVectorIcon(Modifier.size(20.dp), color = Blue) },
            dismissText = copy.otpCancelAction,
            onDismiss = { showLanguageDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppLanguage.entries.forEach { option ->
                    val isSelected = option == language
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PillButtonBg.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable {
                                setLanguage(option)
                                showLanguageDialog = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.nativeName,
                            color = if (isSelected) Cloud else Mist,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            CheckIcon(Modifier.size(16.dp), color = Blue)
                        }
                    }
                }
            }
        }
    }

    // Theme Selection Modal (Universal Flat Minimalist SyncSwiftModal)
    if (showThemeDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showThemeDialog = false },
            title = copy.themeSelection,
            titleIcon = {
                if (themeMode == AppThemeMode.Light) {
                    SunVectorIcon(Modifier.size(20.dp), color = Blue)
                } else {
                    MoonVectorIcon(Modifier.size(20.dp), color = Blue)
                }
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showThemeDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dark Option
                val isDarkSelected = themeMode == AppThemeMode.Dark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDarkSelected) PillButtonBg.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            setThemeMode(AppThemeMode.Dark)
                            showThemeDialog = false
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonVectorIcon(Modifier.size(18.dp), color = if (isDarkSelected) Blue else Mist)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = copy.darkTheme,
                            color = if (isDarkSelected) Cloud else Mist,
                            fontSize = 14.sp,
                            fontWeight = if (isDarkSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (isDarkSelected) {
                        CheckIcon(Modifier.size(16.dp), color = Blue)
                    }
                }

                // Light Option
                val isLightSelected = themeMode == AppThemeMode.Light
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLightSelected) PillButtonBg.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            setThemeMode(AppThemeMode.Light)
                            showThemeDialog = false
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SunVectorIcon(Modifier.size(18.dp), color = if (isLightSelected) Blue else Mist)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = copy.lightTheme,
                            color = if (isLightSelected) Cloud else Mist,
                            fontSize = 14.sp,
                            fontWeight = if (isLightSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (isLightSelected) {
                        CheckIcon(Modifier.size(16.dp), color = Blue)
                    }
                }
            }
        }
    }

    // Modal Confirmation Dialog for Account Deletion (Universal Flat Minimalist SyncSwiftModal)
    if (showDeleteDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = {
                if (!deleteLoading) showDeleteDialog = false
            },
            title = copy.deleteAccountDialogTitle,
            titleIcon = { TrashIcon(Modifier.size(20.dp), color = ErrorRed) },
            confirmText = copy.deleteAccountConfirmAction,
            confirmColor = ErrorRed,
            confirmTextColor = Color.White,
            confirmEnabled = isDeleteEmailMatch,
            confirmLoading = deleteLoading,
            onConfirm = {
                if (!isDeleteEmailMatch) return@SyncSwiftModal
                deleteLoading = true
                deleteError = null
                scope.launch {
                    val success = SupabaseNetwork.deleteUserAccount(userEmail)
                    deleteLoading = false
                    if (success) {
                        showDeleteDialog = false
                        SessionManager.clearSession()
                        showNativeToast(copy.deleteAccountSuccess)
                        onAccountDeleted()
                    } else {
                        deleteError = copy.deleteAccountError
                    }
                }
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showDeleteDialog = false }
        ) {
            Text(
                text = copy.deleteAccountDialogDesc,
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = userEmail,
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(14.dp))

            // Input to type email for confirmation
            PillField(
                hint = copy.emailHint,
                value = deleteEmailInput,
                update = {
                    deleteEmailInput = it
                    deleteError = null
                },
                keyboard = KeyboardType.Email
            )

            if (!isDeleteEmailMatch && deleteEmailInput.isNotBlank()) {
                Text(
                    text = copy.deleteAccountEmailMismatch,
                    color = ErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (deleteError != null) {
                Text(
                    text = deleteError ?: "",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, valid: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (valid) SuccessGreen else Placeholder)
        )
        Text(
            text = label,
            color = if (valid) Cloud else Mist,
            fontSize = 12.sp,
            fontWeight = if (valid) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
