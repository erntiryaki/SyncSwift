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
import kotlinx.coroutines.launch
import net.syncswift.*
import net.syncswift.biometric.BiometricAuthManager
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.PillField
import net.syncswift.ui.theme.*

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

    val isDark = LocalSyncSwiftColors.current.isDark
    val softBlue = LocalSyncSwiftColors.current.blue
    val iconColor = if (isDark) softBlue else Color(0xFF1E293B)

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Biometric state
    var biometricEnabled by remember { mutableStateOf(BiometricAuthManager.isEnabled()) }

    // Password change states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordLoading by remember { mutableStateOf(false) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Account deletion modal states
    var deleteEmailInput by remember { mutableStateOf("") }
    var deleteLoading by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val isLocalUser = provider == IdentityProvider.Local || userEmail.isBlank()
    val isEmailAuth = provider == IdentityProvider.EmailPassword && !isLocalUser
    val isPasswordValid = isValidPassword(newPassword)
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val canSubmitPassword = isPasswordValid && passwordsMatch && currentPassword.isNotBlank() && !passwordLoading

    val isDeleteEmailMatch = deleteEmailInput.trim().equals(userEmail.trim(), ignoreCase = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(Modifier.height(14.dp))

                // Top Header Bar matching AccountScreen style
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
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 1. Language Selection (Birebir AccountMenuItemRow)
                AccountMenuItemRow(
                    icon = { GlobeVectorIcon(Modifier.size(22.dp), color = iconColor) },
                    title = copy.languageSelection,
                    subtitle = copy.languageSelectionDesc,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = language.nativeName,
                                color = softBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                        }
                    },
                    onClick = { showLanguageDialog = true }
                )

                // 2. Theme Selection (Birebir AccountMenuItemRow)
                val currentThemeLabel = when (themeMode) {
                    AppThemeMode.System -> copy.systemTheme
                    AppThemeMode.Light -> copy.lightTheme
                    AppThemeMode.Dark -> copy.darkTheme
                }
                AccountMenuItemRow(
                    icon = {
                        when (themeMode) {
                            AppThemeMode.System -> SystemThemeVectorIcon(Modifier.size(22.dp), color = iconColor)
                            AppThemeMode.Light -> SunVectorIcon(Modifier.size(22.dp), color = iconColor)
                            AppThemeMode.Dark -> MoonVectorIcon(Modifier.size(22.dp), color = iconColor)
                        }
                    },
                    title = copy.themeSelection,
                    subtitle = copy.themeSelectionDesc,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentThemeLabel,
                                color = softBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                        }
                    },
                    onClick = { showThemeDialog = true }
                )

                // 3. Biometric Security App Lock (Birebir AccountMenuItemRow with Switch)
                AccountMenuItemRow(
                    icon = { FingerprintIcon(Modifier.size(22.dp), color = iconColor) },
                    title = copy.biometricLock,
                    subtitle = copy.biometricLockDesc,
                    trailing = {
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { targetState ->
                                if (targetState) {
                                    if (!BiometricAuthManager.isSupported()) {
                                        showNativeToast(copy.biometricNotSupported)
                                    } else {
                                        BiometricAuthManager.authenticate(
                                            title = copy.biometricPromptTitle,
                                            subtitle = copy.biometricPromptSubtitle
                                        ) { success ->
                                            if (success) {
                                                biometricEnabled = true
                                                BiometricAuthManager.setEnabled(true)
                                            } else {
                                                biometricEnabled = false
                                            }
                                        }
                                    }
                                } else {
                                    biometricEnabled = false
                                    BiometricAuthManager.setEnabled(false)
                                    BiometricAuthManager.unlockApp()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = softBlue,
                                uncheckedThumbColor = Mist,
                                uncheckedTrackColor = InputBg
                            )
                        )
                    },
                    onClick = {
                        val targetState = !biometricEnabled
                        if (targetState) {
                            if (!BiometricAuthManager.isSupported()) {
                                showNativeToast(copy.biometricNotSupported)
                            } else {
                                BiometricAuthManager.authenticate(
                                    title = copy.biometricPromptTitle,
                                    subtitle = copy.biometricPromptSubtitle
                                ) { success ->
                                    if (success) {
                                        biometricEnabled = true
                                        BiometricAuthManager.setEnabled(true)
                                    } else {
                                        biometricEnabled = false
                                    }
                                }
                            }
                        } else {
                            biometricEnabled = false
                            BiometricAuthManager.setEnabled(false)
                            BiometricAuthManager.unlockApp()
                        }
                    }
                )

                // 4. Change Password (Birebir AccountMenuItemRow)
                if (isLocalUser) {
                    AccountMenuItemRow(
                        icon = { KeyVectorIcon(Modifier.size(22.dp), color = Mist.copy(alpha = 0.4f)) },
                        title = copy.changePassword,
                        subtitle = copy.featureDisabledForLocal,
                        titleColor = Mist.copy(alpha = 0.45f),
                        subtitleColor = Mist.copy(alpha = 0.35f),
                        trailing = { ChevronRightIcon(Modifier.size(16.dp), color = Mist.copy(alpha = 0.4f)) },
                        onClick = { showNativeToast(copy.featureDisabledForLocal) }
                    )
                } else if (isEmailAuth) {
                    AccountMenuItemRow(
                        icon = { KeyVectorIcon(Modifier.size(22.dp), color = iconColor) },
                        title = copy.changePassword,
                        subtitle = copy.changePasswordDesc,
                        onClick = {
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            passwordError = null
                            passwordSuccess = null
                            showPasswordDialog = true
                        }
                    )
                } else {
                    val isApple = provider == IdentityProvider.Apple
                    val noteText = if (isApple) copy.appleManagedNote else copy.googleManagedNote
                    AccountMenuItemRow(
                        icon = {
                            if (isApple) {
                                SocialProviderIcon(IdentityProvider.Apple, Modifier.size(22.dp), tint = iconColor)
                            } else {
                                ShieldVectorIcon(Modifier.size(22.dp), color = iconColor)
                            }
                        },
                        title = copy.changePassword,
                        subtitle = noteText,
                        onClick = { showNativeToast(noteText) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 5. Danger Zone - Delete Account (Birebir AccountScreen bottom action style)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (isLocalUser) {
                                showNativeToast(copy.featureDisabledForLocal)
                            } else {
                                deleteEmailInput = ""
                                deleteError = null
                                showDeleteDialog = true
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLocalUser) InputBg.copy(alpha = 0.35f) else if (isDark) Color(0xFF261214) else Color(0xFFFEF2F2),
                    border = BorderStroke(
                        1.dp,
                        if (isLocalUser) InputBorder.copy(alpha = 0.25f)
                        else if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFECACA)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrashIcon(
                            Modifier.size(22.dp),
                            color = if (isLocalUser) Mist.copy(alpha = 0.4f) else ErrorRed
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = copy.deleteAccount,
                                color = if (isLocalUser) Mist.copy(alpha = 0.45f) else ErrorRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isLocalUser) copy.featureDisabledForLocal else copy.deleteAccountDesc,
                                color = Mist,
                                fontSize = 12.sp
                            )
                        }
                        ChevronRightIcon(
                            Modifier.size(16.dp),
                            color = if (isLocalUser) Mist.copy(alpha = 0.4f) else ErrorRed
                        )
                    }
                }

                Spacer(Modifier.height(30.dp))
            }

            val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
            if (!isProUser) {
                net.syncswift.ui.components.AdBannerView(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Language Selection Bottom Sheet
        if (showLanguageDialog) {
            net.syncswift.ui.components.SyncSwiftBottomSheet(
                visible = showLanguageDialog,
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

        // Theme Selection Bottom Sheet (Supports System, Dark, Light with 9 Languages)
        if (showThemeDialog) {
            net.syncswift.ui.components.SyncSwiftBottomSheet(
                visible = showThemeDialog,
                onDismissRequest = { showThemeDialog = false },
                title = copy.themeSelection,
                titleIcon = {
                    when (themeMode) {
                        AppThemeMode.System -> SystemThemeVectorIcon(Modifier.size(20.dp), color = Blue)
                        AppThemeMode.Light -> SunVectorIcon(Modifier.size(20.dp), color = Blue)
                        AppThemeMode.Dark -> MoonVectorIcon(Modifier.size(20.dp), color = Blue)
                    }
                },
                dismissText = copy.otpCancelAction,
                onDismiss = { showThemeDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. System Theme Option
                    val isSystemSelected = themeMode == AppThemeMode.System
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSystemSelected) PillButtonBg.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable {
                                setThemeMode(AppThemeMode.System)
                                showThemeDialog = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SystemThemeVectorIcon(Modifier.size(18.dp), color = if (isSystemSelected) Blue else Mist)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = copy.systemTheme,
                                color = if (isSystemSelected) Cloud else Mist,
                                fontSize = 14.sp,
                                fontWeight = if (isSystemSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        if (isSystemSelected) {
                            CheckIcon(Modifier.size(16.dp), color = Blue)
                        }
                    }

                    // 2. Dark Option
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

                    // 3. Light Option
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

        // Change Password Bottom Sheet
        if (showPasswordDialog) {
            net.syncswift.ui.components.SyncSwiftBottomSheet(
                visible = showPasswordDialog,
                onDismissRequest = {
                    if (!passwordLoading) showPasswordDialog = false
                },
                title = copy.changePassword,
                titleIcon = { KeyVectorIcon(Modifier.size(20.dp), color = Blue) },
                dismissText = copy.otpCancelAction,
                onDismiss = { showPasswordDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = copy.changePasswordDesc,
                        color = Mist,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(16.dp))

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
            }
        }

        // Account Deletion Bottom Sheet
        if (showDeleteDialog) {
            net.syncswift.ui.components.SyncSwiftBottomSheet(
                visible = showDeleteDialog,
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
                    if (!isDeleteEmailMatch) return@SyncSwiftBottomSheet
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
}

@Composable
private fun AccountMenuItemRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    titleColor: Color = Cloud,
    subtitleColor: Color = Mist,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(Modifier.width(8.dp))

        if (trailing != null) {
            trailing()
        } else {
            ChevronRightIcon(Modifier.size(16.dp), color = Mist)
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
