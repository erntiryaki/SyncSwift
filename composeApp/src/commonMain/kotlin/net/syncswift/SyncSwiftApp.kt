package net.syncswift

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import net.syncswift.ui.auth.*
import net.syncswift.ui.home.HomeScreen
import net.syncswift.ui.onboarding.OnboardingScreen
import net.syncswift.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthAlertState { None, WrongPassword, CrossProviderEmailToGoogle }

@Composable
fun SyncSwiftApp(onboardingSeen: Boolean = false, onOnboardingFinished: () -> Unit = {}) {
    // Check if user is already logged in persistently!
    val alreadyLoggedIn = remember { SessionManager.isLoggedIn() }
    val initialEmail = remember { SessionManager.getLoggedInEmail() ?: "" }
    val initialProvider = remember { SessionManager.getLoggedInProvider() ?: IdentityProvider.EmailPassword }

    var screen by remember(onboardingSeen, alreadyLoggedIn) {
        mutableStateOf(
            if (alreadyLoggedIn) AuthStep.Home
            else if (onboardingSeen) AuthStep.Email
            else AuthStep.Onboarding
        )
    }

    val initialLanguage = remember {
        val saved = SessionManager.getSavedLanguage()
        if (saved != null) {
            AppLanguage.entries.find { it.code == saved } ?: AppLanguage.English
        } else {
            AppLanguage.English
        }
    }
    val initialTheme = remember {
        val saved = SessionManager.getSavedThemeMode()
        if (saved != null) {
            try { AppThemeMode.valueOf(saved) } catch (e: Exception) { AppThemeMode.Dark }
        } else {
            AppThemeMode.Dark
        }
    }

    var language by remember { mutableStateOf(initialLanguage) }
    var themeMode by remember { mutableStateOf(initialTheme) }

    fun updateLanguage(newLang: AppLanguage) {
        language = newLang
        SessionManager.saveLanguage(newLang.code)
    }

    fun updateThemeMode(newTheme: AppThemeMode) {
        themeMode = newTheme
        SessionManager.saveThemeMode(newTheme.name)
    }
    var email by remember { mutableStateOf(initialEmail) }
    var authDraftEmail by remember { mutableStateOf("") }
    var authProvider by remember { mutableStateOf(initialProvider) }
    var firstName by remember { mutableStateOf(SessionManager.getUserFirstName()) }
    var lastName by remember { mutableStateOf(SessionManager.getUserLastName()) }
    var mainTab by remember { mutableStateOf(net.syncswift.ui.components.MainNavTab.Home) }
    var inSettings by remember { mutableStateOf(false) }
    var inEditProfile by remember { mutableStateOf(false) }
    var inHelpSupport by remember { mutableStateOf(false) }
    var inConnectedDevices by remember { mutableStateOf(false) }

    var authAlert by remember { mutableStateOf(AuthAlertState.None) }
    // Empty by default — filled when OTP is actually sent; prevents guessing before send
    var currentOtpCode by remember { mutableStateOf("") }
    val authPolicy = remember { AuthPolicy() }
    val scope = rememberCoroutineScope()

    val copy = copyFor(language)
    val alertMessage = when (authAlert) {
        AuthAlertState.WrongPassword -> copy.wrongPassword
        AuthAlertState.CrossProviderEmailToGoogle -> copy.crossProviderEmailToGoogle
        AuthAlertState.None -> null
    }
    val direction = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val discoveredDevices by net.syncswift.discovery.DeviceDiscoveryManager.devices.collectAsState()
    val pendingPairRequest by net.syncswift.connection.ConnectionManager.pendingPairRequest.collectAsState()
    val pendingTransferRequest by net.syncswift.connection.ConnectionManager.pendingTransferRequest.collectAsState()

    var targetedDevice by remember { mutableStateOf<net.syncswift.discovery.DiscoveredDevice?>(null) }
    var pendingFileToSend by remember { mutableStateOf<Triple<String, ByteArray, String>?>(null) }
    var showDeviceSelectModal by remember { mutableStateOf(false) }

    val pickFile = net.syncswift.transfer.rememberFilePicker { fileName, bytes, mimeType ->
        if (!net.syncswift.subscription.SubscriptionManager.canTransferFileSize(bytes.size.toLong())) {
            net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                net.syncswift.subscription.PaywallReason.FILE_SIZE_LIMIT
            )
            return@rememberFilePicker
        }
        if (targetedDevice != null) {
            val target = targetedDevice!!
            targetedDevice = null
            net.syncswift.transfer.TransferEngine.sendFile(
                targetHost = target.host,
                targetPort = target.port,
                targetDeviceName = target.name,
                fileName = fileName,
                fileBytes = bytes,
                mimeType = mimeType
            )
        } else if (discoveredDevices.isEmpty()) {
            showNativeToast(copy.noDevicesOnNetwork)
        } else {
            pendingFileToSend = Triple(fileName, bytes, mimeType)
            showDeviceSelectModal = true
        }
    }

    LaunchedEffect(Unit) {
        net.syncswift.subscription.SubscriptionManager.init()
    }

    // Fetch profile data from Supabase if not yet cached locally
    LaunchedEffect(email, screen, authProvider) {
        if (screen == AuthStep.Home && authProvider != IdentityProvider.Local && email.isNotBlank() && (firstName.isBlank() || lastName.isBlank())) {
            val prof = SupabaseNetwork.fetchUserProfile(email)
            if (prof != null) {
                if (prof.firstName.isNotBlank()) firstName = prof.firstName
                if (prof.lastName.isNotBlank()) lastName = prof.lastName
                SessionManager.updateUserProfile(firstName, lastName)
            }
        }
    }

    // Handle double-back press to exit app on root screens
    val isRootAuthScreen = (screen == AuthStep.Email && authProvider != IdentityProvider.Local) || screen == AuthStep.Onboarding
    val isRootHomeScreen = screen == AuthStep.Home && mainTab == net.syncswift.ui.components.MainNavTab.Home
    if (!inSettings && !inEditProfile && !inHelpSupport && !inConnectedDevices && (isRootAuthScreen || isRootHomeScreen)) {
        DoubleBackToExitHandler(exitMessage = copy.exitToast)
    }

    // System Back Gesture / Button Navigation
    if (inSettings) {
        BackHandler { inSettings = false }
    } else if (inEditProfile) {
        BackHandler { inEditProfile = false }
    } else if (inHelpSupport) {
        BackHandler { inHelpSupport = false }
    } else if (inConnectedDevices) {
        BackHandler { inConnectedDevices = false }
    } else if (screen == AuthStep.Home && mainTab != net.syncswift.ui.components.MainNavTab.Home) {
        BackHandler { mainTab = net.syncswift.ui.components.MainNavTab.Home }
    } else if (screen == AuthStep.Email && authProvider == IdentityProvider.Local) {
        BackHandler { screen = AuthStep.Home }
    } else if (screen == AuthStep.Password || screen == AuthStep.Otp) {
        BackHandler { screen = AuthStep.Email }
    } else if (screen == AuthStep.Profile) {
        BackHandler { screen = AuthStep.Otp }
    }

    // Commit User to AuthPolicy, SharedPreferences & Database ONLY when reaching Home Screen!
    fun commitUserRegistration(userEmail: String, provider: IdentityProvider, fName: String = "", lName: String = "", password: String = "") {
        authPolicy.registerAccount(userEmail, provider)
        authProvider = provider
        email = userEmail
        if (fName.isNotBlank()) firstName = fName
        if (lName.isNotBlank()) lastName = lName
        SessionManager.saveSession(userEmail, provider, firstName, lastName)
        if (password.isNotBlank() || (fName.isNotBlank() && lName.isNotBlank())) {
            scope.launch {
                SupabaseNetwork.registerUserInSupabase(
                    email = userEmail,
                    firstName = firstName,
                    lastName = lastName,
                    provider = provider,
                    password = password
                )
            }
        }
    }

    // Send Brevo OTP Email (Reuse active valid code if within 5 mins, else generate new)
    fun triggerOrReuseOtp(targetEmail: String) {
        val now = currentTimeMs()
        val active = SessionManager.getActiveOtp(targetEmail, now)
        val codeToSend = if (active != null) {
            active.code
        } else {
            val newCode = (100000..999999).random().toString()
            SessionManager.saveActiveOtp(targetEmail, newCode, now)
            newCode
        }
        currentOtpCode = codeToSend
        scope.launch {
            AmazonSesOtpGateway().sendCode(targetEmail, codeToSend)
        }
    }

    val textAlign = if (language.isRtl) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
        SyncSwiftTheme(themeMode = themeMode) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = platformFontFamily(), textAlign = textAlign)) {
                Surface(Modifier.fillMaxSize(), color = Night) {
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            AuthStep.Onboarding -> OnboardingScreen(
                                copy = copy,
                                language = language,
                                setLanguage = ::updateLanguage,
                                continueToAuth = {
                                    screen = AuthStep.Email
                                    onOnboardingFinished()
                                }
                            )
                            AuthStep.Email -> EmailAuthScreen(
                                copy = copy,
                                email = authDraftEmail,
                                updateEmail = { authDraftEmail = it },
                                onSignInWithPassword = { userEmail, userPassword ->
                                    scope.launch {
                                        val ok = SupabaseNetwork.signInWithPassword(userEmail, userPassword)
                                        if (ok) {
                                            val prof = SupabaseNetwork.fetchUserProfile(userEmail)
                                            val f = prof?.firstName ?: ""
                                            val l = prof?.lastName ?: ""
                                            commitUserRegistration(userEmail, IdentityProvider.EmailPassword, f, l)
                                            authDraftEmail = ""
                                            screen = AuthStep.Home
                                        } else {
                                            authAlert = AuthAlertState.WrongPassword
                                        }
                                    }
                                },
                                onSendOtpAndContinue = { userEmail ->
                                    authDraftEmail = userEmail
                                    authProvider = IdentityProvider.EmailPassword
                                    triggerOrReuseOtp(userEmail)
                                    screen = AuthStep.Otp
                                },
                                onGoogleSignIn = { googleUser ->
                                    val targetEmail = googleUser.email.trim().lowercase()
                                    val gFirstName = googleUser.givenName ?: googleUser.displayName?.split(" ")?.firstOrNull() ?: ""
                                    val gLastName = googleUser.familyName ?: googleUser.displayName?.split(" ")?.drop(1)?.joinToString(" ") ?: ""
                                    scope.launch {
                                        val existingProvider = SupabaseNetwork.getUserProvider(targetEmail) ?: authPolicy.getRegisteredProvider(targetEmail)
                                        if (existingProvider == IdentityProvider.EmailPassword) {
                                            authAlert = AuthAlertState.CrossProviderEmailToGoogle
                                        } else {
                                            authDraftEmail = ""
                                            authPolicy.registerAccount(targetEmail, IdentityProvider.Google)
                                            commitUserRegistration(targetEmail, IdentityProvider.Google, gFirstName, gLastName)
                                            screen = AuthStep.Home
                                        }
                                    }
                                },
                                onContinueLocally = {
                                    authProvider = IdentityProvider.Local
                                    email = ""
                                    firstName = ""
                                    lastName = ""
                                    authDraftEmail = ""
                                    SessionManager.saveLocalSession()
                                    screen = AuthStep.Home
                                },
                                onBack = if (authProvider == IdentityProvider.Local) {
                                    { screen = AuthStep.Home }
                                } else null,
                                language = language,
                                setLanguage = ::updateLanguage,
                                errorMessage = alertMessage,
                                clearError = { authAlert = AuthAlertState.None }
                            )
                            AuthStep.Password -> PasswordAuthScreen(
                                copy = copy,
                                onBack = { screen = AuthStep.Email },
                                done = {
                                    scope.launch {
                                        val prof = SupabaseNetwork.fetchUserProfile(authDraftEmail)
                                        val f = prof?.firstName ?: ""
                                        val l = prof?.lastName ?: ""
                                        commitUserRegistration(authDraftEmail, IdentityProvider.EmailPassword, f, l)
                                        authDraftEmail = ""
                                        screen = AuthStep.Home
                                    }
                                },
                                language = language,
                                setLanguage = ::updateLanguage
                            )
                            AuthStep.Otp -> OtpAuthScreen(
                                copy = copy,
                                email = authDraftEmail,
                                expectedCode = currentOtpCode,
                                onResendCode = {
                                    val now = currentTimeMs()
                                    val newCode = (100000..999999).random().toString()
                                    currentOtpCode = newCode
                                    SessionManager.saveActiveOtp(authDraftEmail, newCode, now)
                                    scope.launch {
                                        AmazonSesOtpGateway().sendCode(authDraftEmail, newCode)
                                    }
                                },
                                onBack = { screen = AuthStep.Email },
                                verified = { screen = AuthStep.Profile },
                                language = language,
                                setLanguage = ::updateLanguage
                            )
                            AuthStep.Profile -> ProfileAuthScreen(
                                copy = copy,
                                onBack = { screen = AuthStep.Otp },
                                finished = { pFirstName, pLastName, password ->
                                    // User completed profile -> Commit account with firstName, lastName & password
                                    commitUserRegistration(authDraftEmail, IdentityProvider.EmailPassword, pFirstName, pLastName, password)
                                    authDraftEmail = ""
                                    screen = AuthStep.Home
                                },
                                language = language,
                                setLanguage = ::updateLanguage
                            )
                            AuthStep.Home -> {
                                if (inSettings) {
                                    net.syncswift.ui.account.SettingsScreen(
                                        copy = copy,
                                        language = language,
                                        setLanguage = ::updateLanguage,
                                        themeMode = themeMode,
                                        setThemeMode = ::updateThemeMode,
                                        userEmail = email,
                                        provider = authProvider,
                                        onBack = { inSettings = false },
                                        onAccountDeleted = {
                                            inSettings = false
                                            email = ""
                                            firstName = ""
                                            lastName = ""
                                            authDraftEmail = ""
                                            authProvider = IdentityProvider.EmailPassword
                                            SessionManager.clearSession()
                                            mainTab = net.syncswift.ui.components.MainNavTab.Home
                                            screen = AuthStep.Email
                                        }
                                    )
                                } else if (inEditProfile) {
                                    net.syncswift.ui.account.EditProfileScreen(
                                        copy = copy,
                                        currentFirstName = firstName,
                                        currentLastName = lastName,
                                        currentEmail = email,
                                        provider = authProvider,
                                        onBack = { inEditProfile = false },
                                        onProfileUpdated = { newFirst, newLast, newEmail ->
                                            firstName = newFirst
                                            lastName = newLast
                                            email = newEmail
                                            SessionManager.saveSession(newEmail, authProvider, newFirst, newLast)
                                        }
                                    )
                                } else if (inHelpSupport) {
                                    net.syncswift.ui.account.HelpSupportScreen(
                                        copy = copy,
                                        onBack = { inHelpSupport = false }
                                    )
                                } else if (inConnectedDevices) {
                                    net.syncswift.ui.account.ConnectedDevicesScreen(
                                        copy = copy,
                                        onBack = { inConnectedDevices = false }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize()) {
                                        when (mainTab) {
                                            net.syncswift.ui.components.MainNavTab.Home -> {
                                                HomeScreen(
                                                    copy = copy,
                                                    onSendClick = {
                                                        targetedDevice = null
                                                        pickFile()
                                                    },
                                                    onClipboardClick = {
                                                        if (!net.syncswift.subscription.SubscriptionManager.canSyncClipboard()) {
                                                            net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                                                                net.syncswift.subscription.PaywallReason.CLIPBOARD_SYNC
                                                            )
                                                            return@HomeScreen
                                                        }
                                                        val clipText = clipboardManager.getText()?.text ?: ""
                                                        if (clipText.isBlank()) {
                                                            showNativeToast(copy.noTextInClipboard)
                                                        } else if (discoveredDevices.isEmpty()) {
                                                            showNativeToast(copy.noDevicesOnNetwork)
                                                        } else {
                                                            discoveredDevices.forEach { dev ->
                                                                net.syncswift.transfer.TransferEngine.sendClipboard(
                                                                    targetHost = dev.host,
                                                                    targetPort = dev.port,
                                                                    targetDeviceName = dev.name,
                                                                    text = clipText
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onDeviceClick = { dev ->
                                                        targetedDevice = dev
                                                        pickFile()
                                                    },
                                                    onViewAllTransfers = {
                                                        mainTab = net.syncswift.ui.components.MainNavTab.Transfers
                                                    }
                                                )
                                            }
                                            net.syncswift.ui.components.MainNavTab.Transfers -> {
                                                net.syncswift.ui.home.TransfersScreen(
                                                    copy = copy
                                                )
                                            }
                                            net.syncswift.ui.components.MainNavTab.Account -> {
                                                net.syncswift.ui.account.AccountScreen(
                                                    copy = copy,
                                                    userEmail = email,
                                                    firstName = firstName,
                                                    lastName = lastName,
                                                    provider = authProvider,
                                                    onOpenEditProfile = { inEditProfile = true },
                                                    onOpenSettings = { inSettings = true },
                                                    onOpenHelpSupport = { inHelpSupport = true },
                                                    onLogout = {
                                                        email = ""
                                                        firstName = ""
                                                        lastName = ""
                                                        authDraftEmail = ""
                                                        authProvider = IdentityProvider.EmailPassword
                                                        SessionManager.clearSession()
                                                        mainTab = net.syncswift.ui.components.MainNavTab.Home
                                                        screen = AuthStep.Email
                                                    },
                                                    onSignInRequested = {
                                                        authDraftEmail = ""
                                                        screen = AuthStep.Email
                                                    },
                                                    onOpenConnectedDevices = {
                                                        inConnectedDevices = true
                                                    }
                                                )
                                            }
                                        }

                                        // Bottom-docked Ad Banner & Navigation Bar (Ads shown above nav bar for free users)
                                        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                        ) {
                                            if (!isProUser) {
                                                net.syncswift.ui.components.AdBannerView(
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            net.syncswift.ui.components.BottomNavBar(
                                                selectedTab = mainTab,
                                                onTabSelected = { mainTab = it },
                                                copy = copy
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Global Incoming Pair Request Modal
                        if (pendingPairRequest != null) {
                            val req = pendingPairRequest!!
                            net.syncswift.ui.components.SyncSwiftModal(
                                onDismissRequest = { net.syncswift.connection.ConnectionManager.rejectPairRequest(req) },
                                title = copy.connectionRequestTitle,
                                confirmText = copy.accept,
                                onConfirm = { net.syncswift.connection.ConnectionManager.acceptPairRequest(req) },
                                dismissText = copy.decline,
                                onDismiss = { net.syncswift.connection.ConnectionManager.rejectPairRequest(req) }
                            ) {
                                Text(
                                    text = "${req.senderName} ${copy.wantsToConnect}",
                                    color = Mist,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        // Global Incoming Transfer Request Modal
                        if (pendingTransferRequest != null) {
                            val req = pendingTransferRequest!!
                            net.syncswift.ui.components.SyncSwiftModal(
                                onDismissRequest = { net.syncswift.connection.ConnectionManager.rejectTransferRequest(req) },
                                title = copy.incomingFileTitle,
                                confirmText = copy.accept,
                                onConfirm = { net.syncswift.connection.ConnectionManager.acceptTransferRequest(req) },
                                dismissText = copy.decline,
                                onDismiss = { net.syncswift.connection.ConnectionManager.rejectTransferRequest(req) }
                            ) {
                                Text(
                                    text = copy.incomingFilePrompt.replaceFirst("%s", req.senderName).replaceFirst("%s", req.fileName).replaceFirst("%s", req.formattedSize),
                                    color = Mist,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        // Device Selection Modal for Sending File
                        if (showDeviceSelectModal && pendingFileToSend != null) {
                            val fileData = pendingFileToSend!!
                            net.syncswift.ui.components.SyncSwiftModal(
                                onDismissRequest = {
                                    showDeviceSelectModal = false
                                    pendingFileToSend = null
                                },
                                title = copy.selectDeviceToSend,
                                dismissText = copy.otpCancelAction,
                                onDismiss = {
                                    showDeviceSelectModal = false
                                    pendingFileToSend = null
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "${fileData.first} (${net.syncswift.transfer.TransferItem("", fileData.first, fileData.second.size.toLong(), fileData.third).formattedSize})",
                                        color = Mist,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    discoveredDevices.forEach { dev ->
                                        val isConnected = net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    showDeviceSelectModal = false
                                                    pendingFileToSend = null
                                                    net.syncswift.transfer.TransferEngine.sendFile(
                                                        targetHost = dev.host,
                                                        targetPort = dev.port,
                                                        targetDeviceName = dev.name,
                                                        fileName = fileData.first,
                                                        fileBytes = fileData.second,
                                                        mimeType = fileData.third
                                                    )
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = InputBg.copy(alpha = 0.7f),
                                            border = BorderStroke(1.dp, if (isConnected) Blue.copy(alpha = 0.5f) else InputBorder.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(dev.name, color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        if (isConnected) copy.connectedStatus else if (dev.isOnline) copy.deviceOnline else copy.deviceSleepMode,
                                                        color = if (isConnected) Blue else Mist,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                ChevronRightIcon(Modifier.size(16.dp), color = Mist)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Universal Outgoing Request & Transfer Modal (Radar / Approval / Progress / Checkmark)
            val outgoingState by net.syncswift.connection.OutgoingStatusManager.state.collectAsState()
            if (outgoingState != null) {
                net.syncswift.ui.components.OutgoingStatusModal(
                    state = outgoingState!!,
                    copy = copy,
                    onDismiss = {
                        net.syncswift.connection.OutgoingStatusManager.dismiss()
                    }
                )
            }

            // Paywall Subscription Modal
            val showPaywall by net.syncswift.subscription.SubscriptionManager.showPaywall.collectAsState()
            val paywallReason by net.syncswift.subscription.SubscriptionManager.paywallReason.collectAsState()
            if (showPaywall) {
                net.syncswift.ui.components.PaywallModal(
                    reason = paywallReason,
                    copy = copy,
                    onDismiss = {
                        net.syncswift.subscription.SubscriptionManager.dismissPaywall()
                    }
                )
            }
        }
    }
}

