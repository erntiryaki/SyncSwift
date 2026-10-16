package net.syncswift.ui.account

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.components.MainPill
import net.syncswift.ui.components.PillField
import net.syncswift.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    copy: Copy,
    currentFirstName: String,
    currentLastName: String,
    currentEmail: String,
    provider: IdentityProvider,
    onBack: () -> Unit,
    onProfileUpdated: (newFirstName: String, newLastName: String, newEmail: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var firstNameInput by remember { mutableStateOf(currentFirstName) }
    var lastNameInput by remember { mutableStateOf(currentLastName) }
    var emailInput by remember { mutableStateOf(currentEmail) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Email change OTP dialog states
    var showConfirmEmailDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var generatedOtpCode by remember { mutableStateOf("") }
    var enteredOtpCode by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var otpCountdown by remember { mutableStateOf(300) }

    val cleanEmail = emailInput.trim().lowercase()
    val isEmailChanged = cleanEmail != currentEmail.trim().lowercase()
    val isDataChanged = firstNameInput.trim() != currentFirstName.trim() ||
            lastNameInput.trim() != currentLastName.trim() ||
            isEmailChanged

    val canSave = isDataChanged &&
            firstNameInput.isNotBlank() &&
            isValidEmail(cleanEmail) &&
            !isLoading

    // Countdown timer for OTP
    LaunchedEffect(showOtpDialog) {
        if (showOtpDialog) {
            otpCountdown = 300
            while (otpCountdown > 0) {
                delay(1000)
                otpCountdown--
            }
        }
    }

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
                text = copy.editProfileTitle,
                color = Cloud,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
        }

        Spacer(Modifier.height(28.dp))

        // First Name Field (Kutusuz Boxless Satır)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = copy.firstName,
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            PillField(
                hint = copy.firstName,
                value = firstNameInput,
                update = {
                    firstNameInput = it
                    errorMessage = null
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Last Name Field (Kutusuz Boxless Satır)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = copy.lastName,
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            PillField(
                hint = copy.lastName,
                value = lastNameInput,
                update = {
                    lastNameInput = it
                    errorMessage = null
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Email Field (Kutusuz Boxless Satır)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copy.emailHint,
                    color = Cloud,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isEmailChanged) {
                    Text(
                        text = copy.verificationCodeRequired,
                        color = Blue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PillField(
                hint = copy.emailHint,
                value = emailInput,
                update = {
                    emailInput = it
                    errorMessage = null
                },
                keyboard = KeyboardType.Email
            )
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = errorMessage ?: "",
                color = ErrorRed,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(36.dp))

        // Save Button
        MainPill(
            text = if (isLoading) "..." else copy.saveChanges,
            enabled = canSave,
            action = {
                if (!isValidEmail(cleanEmail)) {
                    errorMessage = copy.emailDetail
                    return@MainPill
                }

                if (isEmailChanged) {
                    // E-posta değişiyor: Önce "kod gidecek" modalını göster
                    showConfirmEmailDialog = true
                } else {
                    // Yalnızca isim-soyisim değişiyor
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val ok = SupabaseNetwork.updateUserProfileData(
                            oldEmail = currentEmail,
                            newEmail = currentEmail,
                            firstName = firstNameInput.trim(),
                            lastName = lastNameInput.trim()
                        )
                        isLoading = false
                        if (ok) {
                            SessionManager.updateUserProfile(firstNameInput.trim(), lastNameInput.trim())
                            showNativeToast(copy.profileUpdatedSuccess)
                            onProfileUpdated(firstNameInput.trim(), lastNameInput.trim(), currentEmail)
                            onBack()
                        } else {
                            errorMessage = copy.deleteAccountError
                        }
                    }
                }
            }
        )

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            Spacer(Modifier.height(16.dp))
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(40.dp))
    }

    // Modal 1: Confirmation that code will be sent to new email (Universal Flat Minimalist SyncSwiftModal)
    if (showConfirmEmailDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = { showConfirmEmailDialog = false },
            title = copy.emailChangeConfirmTitle,
            titleIcon = { EmailIcon(Modifier.size(20.dp), color = Blue) },
            confirmText = copy.otpConfirmAction,
            confirmColor = PillButtonBg,
            confirmTextColor = PillButtonText,
            confirmLoading = isLoading,
            onConfirm = {
                showConfirmEmailDialog = false
                isLoading = true
                errorMessage = null
                scope.launch {
                    val exists = SupabaseNetwork.checkUserExists(cleanEmail)
                    if (exists) {
                        isLoading = false
                        errorMessage = copy.emailAlreadyInUse
                        return@launch
                    }

                    val otp = (100000..999999).random().toString()
                    generatedOtpCode = otp
                    AmazonSesOtpGateway().sendCode(cleanEmail, otp)

                    isLoading = false
                    enteredOtpCode = ""
                    otpError = null
                    showOtpDialog = true
                }
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showConfirmEmailDialog = false }
        ) {
            Text(
                text = copy.emailChangeConfirmDesc.replace("%s", cleanEmail),
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }

    // Modal 2: 6-Digit OTP Verification Dialog (Universal Flat Minimalist SyncSwiftModal)
    if (showOtpDialog) {
        net.syncswift.ui.components.SyncSwiftModal(
            onDismissRequest = {
                if (!isLoading) showOtpDialog = false
            },
            title = copy.otpTitle,
            titleIcon = { KeyVectorIcon(Modifier.size(20.dp), color = Blue) },
            confirmText = copy.otpConfirmAction,
            confirmColor = PillButtonBg,
            confirmTextColor = PillButtonText,
            confirmEnabled = enteredOtpCode.length == 6,
            confirmLoading = isLoading,
            onConfirm = {
                if (otpCountdown <= 0) {
                    otpError = copy.otpExpired
                    return@SyncSwiftModal
                }
                if (enteredOtpCode.trim() != generatedOtpCode.trim()) {
                    otpError = copy.otpInvalid
                    return@SyncSwiftModal
                }

                isLoading = true
                otpError = null
                scope.launch {
                    val ok = SupabaseNetwork.updateUserProfileData(
                        oldEmail = currentEmail,
                        newEmail = cleanEmail,
                        firstName = firstNameInput.trim(),
                        lastName = lastNameInput.trim()
                    )
                    isLoading = false
                    if (ok) {
                        showOtpDialog = false
                        SessionManager.saveSession(cleanEmail, provider, firstNameInput.trim(), lastNameInput.trim())
                        showNativeToast(copy.profileUpdatedSuccess)
                        onProfileUpdated(firstNameInput.trim(), lastNameInput.trim(), cleanEmail)
                        onBack()
                    } else {
                        otpError = copy.deleteAccountError
                    }
                }
            },
            dismissText = copy.otpCancelAction,
            onDismiss = { showOtpDialog = false }
        ) {
            Text(
                text = "${copy.enterOtpToVerifyEmail}\n$cleanEmail",
                color = Mist,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            PillField(
                hint = "000000",
                value = enteredOtpCode,
                update = {
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        enteredOtpCode = it
                        otpError = null
                    }
                },
                keyboard = KeyboardType.Number
            )

            Spacer(Modifier.height(8.dp))

            val minutes = otpCountdown / 60
            val seconds = otpCountdown % 60
            Text(
                text = copy.otpTimeRemaining.replace(
                    "%s",
                    "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                ),
                color = if (otpCountdown < 60) ErrorRed else Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (otpError != null) {
                Text(
                    text = otpError ?: "",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
