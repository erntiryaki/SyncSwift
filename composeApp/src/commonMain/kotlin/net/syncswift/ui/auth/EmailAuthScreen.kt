package net.syncswift.ui.auth

import androidx.compose.foundation.BorderStroke
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
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.OrDivider
import net.syncswift.ui.components.PillField
import net.syncswift.ui.components.SocialPill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import net.syncswift.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog

@Composable
fun EmailAuthScreen(
    copy: Copy,
    email: String,
    updateEmail: (String) -> Unit,
    onSignInWithPassword: (email: String, password: String) -> Unit,
    onSendOtpAndContinue: (email: String) -> Unit,
    onGoogleSignIn: (GoogleUserData) -> Unit,
    onAppleSignIn: (AppleUserData) -> Unit = {},
    onContinueLocally: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    errorMessage: String? = null,
    clearError: () -> Unit = {}
) {
    var isUserRegistered by remember { mutableStateOf(false) }
    var registeredProvider by remember { mutableStateOf<IdentityProvider?>(null) }
    var password by remember { mutableStateOf("") }
    var localWarning by remember { mutableStateOf<String?>(null) }
    var showOtpConfirmDialog by remember { mutableStateOf(false) }
    var pendingEmailToSend by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isAppleLoading by remember { mutableStateOf(false) }
    var isEmailSubmitting by remember { mutableStateOf(false) }
    val isAnyLoading = isGoogleLoading || isAppleLoading || isEmailSubmitting

    val launchNativeGoogleAuth = rememberGoogleAuthLauncher(
        webClientId = SyncSwiftConfig.GOOGLE_WEB_CLIENT_ID,
        onSuccess = { userData ->
            isGoogleLoading = false
            onGoogleSignIn(userData)
        },
        onError = { err ->
            isGoogleLoading = false
            localWarning = err
        }
    )

    val launchNativeAppleAuth = rememberAppleAuthLauncher(
        onSuccess = { userData ->
            isAppleLoading = false
            onAppleSignIn(userData)
        },
        onError = { err ->
            isAppleLoading = false
            localWarning = err
        }
    )

    // Check if the email is registered in Supabase; also check provider
    LaunchedEffect(email) {
        clearError()
        localWarning = null
        val clean = email.trim().lowercase()
        if (clean.contains("@") && clean.contains(".")) {
            val provider = SupabaseNetwork.getUserProvider(clean)
            registeredProvider = provider
            when (provider) {
                IdentityProvider.Google -> {
                    isUserRegistered = false
                }
                IdentityProvider.EmailPassword -> {
                    isUserRegistered = true
                }
                else -> {
                    isUserRegistered = false
                }
            }
        } else {
            isUserRegistered = false
            registeredProvider = null
            localWarning = null
        }
    }

    // OTP Confirmation Modal Dialog (Universal Flat Minimalist SyncSwiftModal)
    if (showOtpConfirmDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showOtpConfirmDialog = false },
            title = copy.otpConfirmTitle,
            titleIcon = { EmailIcon(Modifier.size(20.dp), color = Blue) },
            confirmText = copy.otpConfirmAction,
            confirmColor = PillButtonBg,
            confirmTextColor = PillButtonText,
            onConfirm = {
                showOtpConfirmDialog = false
                onSendOtpAndContinue(pendingEmailToSend)
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showOtpConfirmDialog = false }
        ) {
            Text(
                text = copy.otpConfirmMessage.replace("%s", pendingEmailToSend),
                color = Mist,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }

    AuthPage(
        title = copy.emailTitle,
        detail = copy.emailDetail,
        language = language,
        setLanguage = setLanguage,
        heroPage = 0,
        onBack = onBack
    ) {
        // Email Input Field
        PillField(
            hint = copy.emailHint,
            value = email,
            update = {
                updateEmail(it)
            },
            keyboard = KeyboardType.Email,
            leadingIcon = { EmailIcon(Modifier.size(20.dp), color = Mist) }
        )

        // If registered with Email/Password: reveal password input directly under the email input!
        if (isUserRegistered) {
            Spacer(Modifier.height(14.dp))
            PillField(
                hint = copy.passwordHint,
                value = password,
                update = { password = it },
                keyboard = KeyboardType.Password,
                secret = true,
                leadingIcon = { LockIcon(Modifier.size(20.dp), color = Mist) }
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = copy.forgotPassword,
                    color = Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        // Active Error / Warning Banner (Dynamically translated with current copy)
        val crossProviderWarning = if (registeredProvider == IdentityProvider.Google) copy.crossProviderGoogleToEmail else null
        val displayedError = errorMessage ?: crossProviderWarning ?: localWarning
        if (displayedError != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErrorRed.copy(alpha = 0.10f))
                    .border(BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u26a0  $displayedError",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Main Action Button: "Giriş Yap" if registered with password, else "Devam Et"
        MainPill(
            text = if (isUserRegistered) copy.signIn else copy.continueText,
            isLoading = isEmailSubmitting,
            action = {
                val clean = email.trim().lowercase()
                isEmailSubmitting = true
                scope.launch {
                    try {
                        // Check provider
                        val provider = SupabaseNetwork.getUserProvider(clean)
                        registeredProvider = provider
                        if (provider == IdentityProvider.Google) {
                            return@launch
                        }

                        if (isUserRegistered || provider == IdentityProvider.EmailPassword) {
                            isUserRegistered = true
                            onSignInWithPassword(clean, password)
                        } else {
                            // Double check in case background check hasn't completed yet
                            val exists = SupabaseNetwork.checkUserExists(clean)
                            if (exists) {
                                isUserRegistered = true
                            } else {
                                // New user -> Show confirmation modal before sending OTP
                                pendingEmailToSend = clean
                                showOtpConfirmDialog = true
                            }
                        }
                    } finally {
                        isEmailSubmitting = false
                    }
                }
            },
            enabled = !isAnyLoading && (
                if (isUserRegistered) (email.contains("@") && email.contains(".") && password.isNotBlank())
                else (email.contains("@") && email.contains(".") && registeredProvider != IdentityProvider.Google)
            )
        )

        OrDivider(copy.or)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SocialPill(
                label = if (currentPlatform() == PlatformFlavor.Ios) "Google" else copy.google,
                provider = IdentityProvider.Google,
                light = true,
                isLoading = isGoogleLoading,
                enabled = !isAnyLoading,
                action = {
                    isGoogleLoading = true
                    launchNativeGoogleAuth()
                },
                modifier = Modifier.weight(1f)
            )
            if (currentPlatform() == PlatformFlavor.Ios) {
                SocialPill(
                    label = "Apple",
                    provider = IdentityProvider.Apple,
                    light = false,
                    isLoading = isAppleLoading,
                    enabled = !isAnyLoading,
                    action = {
                        isAppleLoading = true
                        launchNativeAppleAuth()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = copy.continueLocally,
                color = Blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = platformFontFamily(),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onContinueLocally() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
