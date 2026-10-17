package net.syncswift

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

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
            val sysLang = getSystemLanguageCode()
            val matched = resolveInitialLanguage(sysLang)
            SessionManager.saveLanguage(matched.code)
            matched
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
    var inConnectDesktop by remember { mutableStateOf(false) }
    var inNotifications by remember { mutableStateOf(false) }

    var authAlert by remember { mutableStateOf(AuthAlertState.None) }
    // Empty by default — filled when OTP is actually sent; prevents guessing before send
    var currentOtpCode by remember { mutableStateOf("") }
    val authPolicy = remember { AuthPolicy() }
    val scope = rememberCoroutineScope()

    fun updateLanguage(newLang: AppLanguage) {
        language = newLang
        SessionManager.saveLanguage(newLang.code)
        if (email.isNotBlank() && screen == AuthStep.Home) {
            scope.launch {
                SupabaseNetwork.registerDeviceInSupabase(
                    email = email,
                    deviceId = SessionManager.getPlatformDeviceId(),
                    deviceName = SessionManager.getMyDeviceName(),
                    deviceType = SessionManager.getMyPlatformName(),
                    pushToken = SessionManager.getMyPushToken()
                )
            }
        }
    }

    fun updateThemeMode(newTheme: AppThemeMode) {
        themeMode = newTheme
        SessionManager.saveThemeMode(newTheme.name)
    }

    val copy = copyFor(language)
    val alertMessage = when (authAlert) {
        AuthAlertState.WrongPassword -> copy.wrongPassword
        AuthAlertState.CrossProviderEmailToGoogle -> copy.crossProviderEmailToGoogle
        AuthAlertState.None -> null
    }
    val direction = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val discoveredDevices by net.syncswift.discovery.DeviceDiscoveryManager.devices.collectAsState()
    val otherDevices = remember(discoveredDevices) {
        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
        discoveredDevices.filter { dev ->
            !dev.isSelf &&
            (self == null || (dev.host != self.host && net.syncswift.discovery.cleanDeviceDisplayName(dev.name) != net.syncswift.discovery.cleanDeviceDisplayName(self.name)))
        }.distinctBy { it.host to net.syncswift.discovery.cleanDeviceDisplayName(it.name) }
    }
    val connectedKeys by net.syncswift.connection.ConnectionManager.connectedDeviceKeys.collectAsState()
    val selectableDevicesForTransfer = remember(otherDevices, connectedKeys) {
        otherDevices.filter { dev ->
            val isConn = connectedKeys.contains("${dev.host}:${dev.port}") ||
                         connectedKeys.contains(dev.id) ||
                         connectedKeys.contains(dev.host) ||
                         connectedKeys.contains(net.syncswift.discovery.cleanDeviceDisplayName(dev.name)) ||
                         net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
            (!net.syncswift.connection.ConnectionManager.isRemoteDevice(dev) && dev.isOnline) || isConn
        }
    }
    val pendingPairRequest by net.syncswift.connection.ConnectionManager.pendingPairRequest.collectAsState()
    val pendingTransferRequest by net.syncswift.connection.ConnectionManager.pendingTransferRequest.collectAsState()
    val allTransfers by net.syncswift.transfer.TransferEngine.transfers.collectAsState()
    var latestCompletedItem by remember { mutableStateOf<net.syncswift.transfer.TransferItem?>(null) }
    var lastHandledTransferId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(allTransfers) {
        val completedIncoming = allTransfers.firstOrNull { it.status == net.syncswift.transfer.TransferStatus.COMPLETED && !it.isSent }
        if (completedIncoming != null && completedIncoming.id != lastHandledTransferId) {
            if (currentTimeMs() - completedIncoming.timestamp < 10000L) {
                lastHandledTransferId = completedIncoming.id
                latestCompletedItem = completedIncoming
            }
        }
    }

    LaunchedEffect(latestCompletedItem) {
        if (latestCompletedItem != null) {
            kotlinx.coroutines.delay(6500)
            latestCompletedItem = null
        }
    }
    LaunchedEffect(Unit) {
        if (!net.syncswift.subscription.SubscriptionManager.isProActive() &&
            !net.syncswift.subscription.SubscriptionManager.isPro.value &&
            !net.syncswift.SessionManager.getSavedProStatus()) {
            net.syncswift.subscription.SubscriptionManager.checkTimedPaywall()
        }
    }
    var targetedDevice by remember { mutableStateOf<net.syncswift.discovery.DiscoveredDevice?>(null) }
    var pendingFileToSend by remember { mutableStateOf<Triple<String, ByteArray, String>?>(null) }
    var pendingClipboardToSend by remember { mutableStateOf<String?>(null) }
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
        } else if (selectableDevicesForTransfer.isEmpty()) {
            showNativeToast(copy.noDevicesOnNetwork)
        } else {
            pendingFileToSend = Triple(fileName, bytes, mimeType)
            showDeviceSelectModal = true
        }
    }

    LaunchedEffect(Unit) {
        net.syncswift.subscription.SubscriptionManager.init()
    }

    // Fetch profile data from Supabase to sync names and Pro subscription status across devices
    LaunchedEffect(email, screen, authProvider) {
        if (screen == AuthStep.Home && authProvider != IdentityProvider.Local && email.isNotBlank()) {
            val prof = SupabaseNetwork.fetchUserProfile(email)
            if (prof != null) {
                if (prof.isPro) {
                    net.syncswift.subscription.SubscriptionManager.setProState(true)
                    if (prof.proStartDate > 0L) SessionManager.saveProStartDateMillis(prof.proStartDate)
                    if (prof.proRenewalDate > 0L) SessionManager.saveProRenewalDateMillis(prof.proRenewalDate)
                } else if (net.syncswift.subscription.SubscriptionManager.isProActive() || SessionManager.getSavedProStatus()) {
                    SupabaseNetwork.updateUserProStatus(
                        email = email,
                        isPro = true,
                        startDate = SessionManager.getProStartDateMillis(),
                        renewalDate = SessionManager.getProRenewalDateMillis()
                    )
                }
                val needsNameRefresh = firstName.isBlank() || lastName.isBlank() || firstName.any { it.isDigit() } || firstName.contains("@")
                if (needsNameRefresh) {
                    val (f, l) = SmartNameExtractor.extract(email, prof.firstName, prof.lastName)
                    if (f.isNotBlank()) firstName = f
                    if (l.isNotBlank()) lastName = l
                    SessionManager.updateUserProfile(firstName, lastName)
                    if (prof.firstName.any { it.isDigit() } || prof.firstName.contains("@") || (prof.lastName.isBlank() && l.isNotBlank())) {
                        SupabaseNetwork.registerUserInSupabase(
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            provider = authProvider
                        )
                    }
                }
            } else {
                val (f, l) = SmartNameExtractor.extract(email, firstName, lastName)
                if (f.isNotBlank()) firstName = f
                if (l.isNotBlank()) lastName = l
                SessionManager.updateUserProfile(firstName, lastName)
            }
            val uid = SupabaseNetwork.getUserId(email) ?: SessionManager.getLoggedInUserId()
            SupabaseNetwork.registerDeviceInSupabase(
                email = email,
                deviceId = SessionManager.getPlatformDeviceId(),
                deviceName = SessionManager.getMyDeviceName(),
                deviceType = SessionManager.getMyPlatformName(),
                pushToken = SessionManager.getMyPushToken(),
                userId = uid
            )
        }
    }

    // Handle double-back press to exit app on root screens
    val isRootAuthScreen = (screen == AuthStep.Email && authProvider != IdentityProvider.Local) || screen == AuthStep.Onboarding
    val isRootHomeScreen = screen == AuthStep.Home && mainTab == net.syncswift.ui.components.MainNavTab.Home
    val isAnySubScreenOpen = inSettings || inEditProfile || inHelpSupport || inConnectedDevices || inConnectDesktop || inNotifications
    if (!isAnySubScreenOpen && (isRootAuthScreen || isRootHomeScreen)) {
        DoubleBackToExitHandler(exitMessage = copy.exitToast)
    }

    // System Back Gesture / Button Navigation
    if (inNotifications) {
        BackHandler { inNotifications = false }
    } else if (inSettings) {
        BackHandler { inSettings = false }
    } else if (inEditProfile) {
        BackHandler { inEditProfile = false }
    } else if (inHelpSupport) {
        BackHandler { inHelpSupport = false }
    } else if (inConnectedDevices) {
        BackHandler { inConnectedDevices = false }
    } else if (inConnectDesktop) {
        BackHandler { inConnectDesktop = false }
    } else if (screen == AuthStep.Home && mainTab != net.syncswift.ui.components.MainNavTab.Home) {
        BackHandler { mainTab = net.syncswift.ui.components.MainNavTab.Home }
    } else if (screen == AuthStep.Email && authProvider == IdentityProvider.Local) {
        BackHandler { screen = AuthStep.Home }
    } else if (screen == AuthStep.Password || screen == AuthStep.Otp) {
        BackHandler { screen = AuthStep.Email }
    } else if (screen == AuthStep.Profile) {
        BackHandler { screen = AuthStep.Otp }
    }

    data class BlockedDeviceLimitData(
        val email: String,
        val provider: IdentityProvider,
        val firstName: String,
        val lastName: String,
        val password: String = "",
        val devices: List<net.syncswift.discovery.DiscoveredDevice>
    )

    var blockedDeviceLimitData by remember { mutableStateOf<BlockedDeviceLimitData?>(null) }
    var showOldDevicesModal by remember { mutableStateOf(false) }
    var oldDeviceToConfirmRemove by remember { mutableStateOf<net.syncswift.discovery.DiscoveredDevice?>(null) }
    var isRemovingOldDevice by remember { mutableStateOf(false) }

    // Commit User to AuthPolicy, SharedPreferences & Database ONLY when reaching Home Screen!
    fun commitUserRegistration(userEmail: String, provider: IdentityProvider, fName: String = "", lName: String = "", password: String = "") {
        authPolicy.registerAccount(userEmail, provider)
        authProvider = provider
        email = userEmail
        if (fName.isNotBlank()) firstName = fName
        if (lName.isNotBlank()) lastName = lName
        SessionManager.saveSession(userEmail, provider, firstName, lastName)
        scope.launch {
            SupabaseNetwork.registerUserInSupabase(
                email = userEmail,
                firstName = firstName,
                lastName = lastName,
                provider = provider,
                password = password
            )
            val uid = SupabaseNetwork.getUserId(userEmail) ?: SessionManager.getLoggedInUserId()
            SupabaseNetwork.registerDeviceInSupabase(
                email = userEmail,
                deviceId = SessionManager.getPlatformDeviceId(),
                deviceName = SessionManager.getMyDeviceName(),
                deviceType = SessionManager.getMyPlatformName(),
                pushToken = SessionManager.getMyPushToken(),
                userId = uid
            )
            // Check if account has Pro purchased on another device or prior session
            val prof = SupabaseNetwork.fetchUserProfile(userEmail)
            if (prof != null && prof.isPro) {
                net.syncswift.subscription.SubscriptionManager.setProState(true)
                if (prof.proStartDate > 0L) SessionManager.saveProStartDateMillis(prof.proStartDate)
                if (prof.proRenewalDate > 0L) SessionManager.saveProRenewalDateMillis(prof.proRenewalDate)
            } else if (net.syncswift.subscription.SubscriptionManager.isPro.value || SessionManager.getSavedProStatus()) {
                // If already purchased Pro on this device, sync it to the newly logged-in account
                SupabaseNetwork.updateUserProStatus(
                    email = userEmail,
                    isPro = true,
                    startDate = SessionManager.getProStartDateMillis(),
                    renewalDate = SessionManager.getProRenewalDateMillis()
                )
            }
        }
    }

    fun verifyDeviceLimitAndLogin(
        targetEmail: String,
        provider: IdentityProvider,
        fName: String = "",
        lName: String = "",
        pwd: String = ""
    ) {
        scope.launch {
            val prof = SupabaseNetwork.fetchUserProfile(targetEmail)
            val isProUser = (prof?.isPro == true) || net.syncswift.subscription.SubscriptionManager.isProActive() || net.syncswift.subscription.SubscriptionManager.isPro.value || SessionManager.getSavedProStatus()
            val currentDeviceId = SessionManager.getPlatformDeviceId()
            if (!isProUser) {
                val activeMobiles = SupabaseNetwork.getActiveMobileDevices(targetEmail)
                val otherMobiles = activeMobiles.filter { it.id != currentDeviceId }
                if (otherMobiles.isNotEmpty()) {
                    blockedDeviceLimitData = BlockedDeviceLimitData(
                        email = targetEmail,
                        provider = provider,
                        firstName = fName,
                        lastName = lName,
                        password = pwd,
                        devices = otherMobiles
                    )
                    return@launch
                }
            }
            commitUserRegistration(targetEmail, provider, fName, lName, pwd)
            authDraftEmail = ""
            screen = AuthStep.Home
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
            Box(
                Modifier
                    .fillMaxSize()
                    .edgeSwipeToBack(enabled = BackDispatcher.canGoBack()) {
                        BackDispatcher.handleBack()
                    }
            ) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = platformFontFamily(), textAlign = textAlign)) {
                    Surface(Modifier.fillMaxSize(), color = Night) {
                        Box(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "ScreenTransition"
                        ) { currentScreen ->
                            when (currentScreen) {
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
                                            verifyDeviceLimitAndLogin(userEmail, IdentityProvider.EmailPassword, f, l, userPassword)
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
                                    val rawFirst = googleUser.givenName ?: googleUser.displayName?.split(" ")?.firstOrNull() ?: ""
                                    val rawLast = googleUser.familyName ?: googleUser.displayName?.split(" ")?.drop(1)?.joinToString(" ") ?: ""
                                    val (gFirstName, gLastName) = SmartNameExtractor.extract(targetEmail, rawFirst, rawLast)
                                    scope.launch {
                                        val existingProvider = SupabaseNetwork.getUserProvider(targetEmail) ?: authPolicy.getRegisteredProvider(targetEmail)
                                        if (existingProvider == IdentityProvider.EmailPassword) {
                                            authAlert = AuthAlertState.CrossProviderEmailToGoogle
                                        } else {
                                            authDraftEmail = ""
                                            authPolicy.registerAccount(targetEmail, IdentityProvider.Google)
                                            verifyDeviceLimitAndLogin(targetEmail, IdentityProvider.Google, gFirstName, gLastName)
                                        }
                                    }
                                },
                                onAppleSignIn = { appleUser ->
                                    val targetEmail = when {
                                        appleUser.email.isNotBlank() && !appleUser.email.contains("@privaterelay") -> appleUser.email.trim().lowercase()
                                        appleUser.email.isNotBlank() -> appleUser.email.trim().lowercase()
                                        !SessionManager.getLoggedInEmail().isNullOrBlank() -> SessionManager.getLoggedInEmail()!!.trim().lowercase()
                                        else -> "${appleUser.userIdentifier}@apple.id"
                                    }
                                    val (cleanFirst, cleanLast) = SmartNameExtractor.extract(
                                        email = targetEmail,
                                        hintFirstName = appleUser.givenName ?: "",
                                        hintLastName = appleUser.familyName ?: ""
                                    )
                                    var aFirstName = cleanFirst
                                    var aLastName = cleanLast
                                    scope.launch {
                                        if (aFirstName.isBlank() || aLastName.isBlank()) {
                                            val existingProfile = SupabaseNetwork.fetchUserProfile(targetEmail)
                                            if (existingProfile != null) {
                                                val (pFirst, pLast) = SmartNameExtractor.extract(
                                                    email = targetEmail,
                                                    hintFirstName = existingProfile.firstName,
                                                    hintLastName = existingProfile.lastName
                                                )
                                                if (aFirstName.isBlank() && pFirst.isNotBlank()) aFirstName = pFirst
                                                if (aLastName.isBlank() && pLast.isNotBlank()) aLastName = pLast
                                            }
                                        }
                                        if (aFirstName.isBlank() || aLastName.isBlank()) {
                                            val (sFirst, sLast) = SmartNameExtractor.extract(
                                                email = targetEmail,
                                                hintFirstName = SessionManager.getUserFirstName(),
                                                hintLastName = SessionManager.getUserLastName()
                                            )
                                            if (aFirstName.isBlank() && sFirst.isNotBlank()) aFirstName = sFirst
                                            if (aLastName.isBlank() && sLast.isNotBlank()) aLastName = sLast
                                        }
                                        val existingProvider = SupabaseNetwork.getUserProvider(targetEmail) ?: authPolicy.getRegisteredProvider(targetEmail)
                                        if (existingProvider == IdentityProvider.EmailPassword) {
                                            authAlert = AuthAlertState.CrossProviderEmailToGoogle
                                        } else {
                                            authDraftEmail = ""
                                            authPolicy.registerAccount(targetEmail, IdentityProvider.Apple)
                                            verifyDeviceLimitAndLogin(targetEmail, IdentityProvider.Apple, aFirstName, aLastName)
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
                                        verifyDeviceLimitAndLogin(authDraftEmail, IdentityProvider.EmailPassword, f, l)
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
                                    verifyDeviceLimitAndLogin(authDraftEmail, IdentityProvider.EmailPassword, pFirstName, pLastName, password)
                                },
                                language = language,
                                setLanguage = ::updateLanguage
                            )
                            AuthStep.Home -> {
                                val currentSubScreen = when {
                                    inSettings -> 1
                                    inEditProfile -> 2
                                    inHelpSupport -> 3
                                    inConnectedDevices -> 4
                                    inConnectDesktop -> 5
                                    inNotifications -> 6
                                    else -> 0
                                }
                                AnimatedContent(
                                    targetState = currentSubScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(160))
                                    },
                                    label = "SubScreenTransition"
                                ) { subIndex ->
                                    when (subIndex) {
                                        1 -> net.syncswift.ui.account.SettingsScreen(
                                            copy = copy,
                                            language = language,
                                            setLanguage = ::updateLanguage,
                                            themeMode = themeMode,
                                            setThemeMode = ::updateThemeMode,
                                            userEmail = email,
                                            provider = authProvider,
                                            onBack = { inSettings = false },
                                            onAccountDeleted = {
                                                scope.launch {
                                                    SupabaseNetwork.clearDeviceUser(SessionManager.getPlatformDeviceId())
                                                }
                                                inSettings = false
                                                email = ""
                                                firstName = ""
                                                lastName = ""
                                                authDraftEmail = ""
                                                authProvider = IdentityProvider.EmailPassword
                                                SessionManager.clearSession()
                                                net.syncswift.subscription.SubscriptionManager.setProState(false)
                                                mainTab = net.syncswift.ui.components.MainNavTab.Home
                                                screen = AuthStep.Email
                                            }
                                        )
                                        2 -> net.syncswift.ui.account.EditProfileScreen(
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
                                        3 -> net.syncswift.ui.account.HelpSupportScreen(
                                            copy = copy,
                                            onBack = { inHelpSupport = false }
                                        )
                                        4 -> net.syncswift.ui.account.ConnectedDevicesScreen(
                                            copy = copy,
                                            onBack = { inConnectedDevices = false },
                                            onSendFileToDevice = { dev ->
                                                inConnectedDevices = false
                                                targetedDevice = dev
                                                pickFile()
                                            }
                                        )
                                        5 -> net.syncswift.ui.account.ConnectDesktopScreen(
                                            copy = copy,
                                            onDismiss = { inConnectDesktop = false }
                                        )
                                        6 -> net.syncswift.ui.notifications.NotificationsScreen(
                                            copy = copy,
                                            onBack = { inNotifications = false }
                                        )
                                        else -> {
                                            Column(Modifier.fillMaxSize()) {
                                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                                    Crossfade(
                                                    targetState = mainTab,
                                                    animationSpec = tween(180),
                                                    label = "MainTabCrossfade"
                                                ) { tab ->
                                                    when (tab) {
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
                                                                    } else if (selectableDevicesForTransfer.isEmpty()) {
                                                                        showNativeToast(copy.noDevicesOnNetwork)
                                                                    } else {
                                                                        pendingClipboardToSend = clipText
                                                                    }
                                                                },
                                                                onDeviceClick = { dev ->
                                                                    targetedDevice = dev
                                                                    pickFile()
                                                                },
                                                                onViewAllTransfers = {
                                                                    mainTab = net.syncswift.ui.components.MainNavTab.Transfers
                                                                },
                                                                onOpenNotifications = {
                                                                    inNotifications = true
                                                                }
                                                            )
                                                        }
                                                        net.syncswift.ui.components.MainNavTab.Transfers -> {
                                                            net.syncswift.ui.home.TransfersScreen(
                                                                copy = copy
                                                            )
                                                        }
                                                        net.syncswift.ui.components.MainNavTab.ClipboardHistory -> {
                                                            net.syncswift.ui.clipboard.ClipboardHistoryScreen(
                                                                copy = copy,
                                                                onSendToDevice = { text ->
                                                                    if (selectableDevicesForTransfer.isEmpty()) {
                                                                        showNativeToast(copy.noDevicesOnNetwork)
                                                                    } else {
                                                                        pendingClipboardToSend = text
                                                                    }
                                                                }
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
                                                                    scope.launch {
                                                                        SupabaseNetwork.clearDeviceUser(SessionManager.getPlatformDeviceId())
                                                                    }
                                                                    email = ""
                                                                    firstName = ""
                                                                    lastName = ""
                                                                    authDraftEmail = ""
                                                                    authProvider = IdentityProvider.EmailPassword
                                                                    SessionManager.clearSession()
                                                                    net.syncswift.subscription.SubscriptionManager.setProState(false)
                                                                    mainTab = net.syncswift.ui.components.MainNavTab.Home
                                                                    screen = AuthStep.Email
                                                                },
                                                                onSignInRequested = {
                                                                    authDraftEmail = ""
                                                                    screen = AuthStep.Email
                                                                },
                                                                onOpenConnectedDevices = {
                                                                    inConnectedDevices = true
                                                                },
                                                                onOpenConnectDesktop = {
                                                                    inConnectDesktop = true
                                                                },
                                                                onOpenNotifications = {
                                                                    inNotifications = true
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Bottom Navigation Bar
                                                net.syncswift.ui.components.BottomNavBar(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    selectedTab = mainTab,
                                                    onTabSelected = { mainTab = it },
                                                    copy = copy
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // Global Incoming Pair Request Full Page (No Modal)
                        AnimatedVisibility(
                            visible = pendingPairRequest != null,
                            enter = fadeIn(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(180))
                        ) {
                            val req = pendingPairRequest
                            if (req != null) {
                                BackHandler {
                                    net.syncswift.connection.ConnectionManager.rejectPairRequest(req)
                                }
                                net.syncswift.ui.components.IncomingPairScreen(
                                    request = req,
                                    copy = copy,
                                    onAccept = { net.syncswift.connection.ConnectionManager.acceptPairRequest(req) },
                                    onDecline = { net.syncswift.connection.ConnectionManager.rejectPairRequest(req) }
                                )
                            }
                        }

                        // Global Floating Received File Banner with "Dosya Konumunu Gör" & "Aç"
                        AnimatedVisibility(
                            visible = latestCompletedItem != null,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .zIndex(99f)
                        ) {
                            val item = latestCompletedItem
                            if (item != null) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = CardSurface,
                                    border = BorderStroke(1.dp, Blue.copy(alpha = 0.5f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                AnimatedCheckCircleIcon(
                                                    modifier = Modifier.size(28.dp),
                                                    circleColor = SuccessGreen,
                                                    checkColor = Color.White
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = item.fileName,
                                                        color = Cloud,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${item.formattedSize} · ${copy.fileReceivedSuccessfully.replace("%s", "").trim(' ', ':')}",
                                                        color = Mist,
                                                        fontSize = 12.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .clickable { latestCompletedItem = null },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CrossVectorIcon(Modifier.size(12.dp), color = Mist)
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 1. Dosya Konumunu Gör
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .clickable {
                                                        latestCompletedItem = null
                                                        net.syncswift.showFileLocation(item.localFilePath, item.fileName)
                                                    },
                                                shape = RoundedCornerShape(18.dp),
                                                color = InputBg,
                                                border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    FolderOutlineIcon(Modifier.size(15.dp), color = Mist)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = copy.showFileLocationAction,
                                                        color = Cloud,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // 2. Aç
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .clickable {
                                                        latestCompletedItem = null
                                                        net.syncswift.openFile(item.localFilePath, item.fileName, item.mimeType)
                                                    },
                                                shape = RoundedCornerShape(18.dp),
                                                color = PillButtonBg,
                                                border = BorderStroke(1.dp, PillButtonBorder)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    OpenExternalIcon(Modifier.size(14.dp), color = PillButtonText)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = copy.openFileAction,
                                                        color = PillButtonText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Global Incoming Transfer Request Full Page (No Modal)
                        AnimatedVisibility(
                            visible = pendingTransferRequest != null,
                            enter = fadeIn(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(180))
                        ) {
                            val req = pendingTransferRequest
                            if (req != null) {
                                BackHandler {
                                    net.syncswift.connection.ConnectionManager.rejectTransferRequest(req)
                                }
                                net.syncswift.ui.components.IncomingTransferScreen(
                                    request = req,
                                    copy = copy,
                                    onAccept = { net.syncswift.connection.ConnectionManager.acceptTransferRequest(req) },
                                    onDecline = { net.syncswift.connection.ConnectionManager.rejectTransferRequest(req) }
                                )
                            }
                        }

                        // Device Selection Screen for Sending File (Full Page, No Modal)
                        AnimatedVisibility(
                            visible = showDeviceSelectModal && pendingFileToSend != null,
                            enter = fadeIn(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(180))
                        ) {
                            val fileData = pendingFileToSend
                            if (fileData != null) {
                                BackHandler {
                                    showDeviceSelectModal = false
                                    pendingFileToSend = null
                                }
                                val formattedSize = net.syncswift.transfer.TransferItem("", fileData.first, fileData.second.size.toLong(), fileData.third).formattedSize
                                net.syncswift.ui.components.FileDeviceSelectScreen(
                                    fileName = fileData.first,
                                    fileSizeFormatted = formattedSize,
                                    devices = selectableDevicesForTransfer,
                                    copy = copy,
                                    isDeviceConnected = { dev -> net.syncswift.connection.ConnectionManager.isDeviceConnected(dev) },
                                    onSelectDevice = { dev ->
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
                                    onDismiss = {
                                        showDeviceSelectModal = false
                                        pendingFileToSend = null
                                    }
                                )
                            }
                        }

                        // Device Selection Screen for Sending Clipboard (Full Page, No Modal)
                        AnimatedVisibility(
                            visible = pendingClipboardToSend != null,
                            enter = fadeIn(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(180))
                        ) {
                            val clipText = pendingClipboardToSend
                            if (clipText != null) {
                                BackHandler {
                                    pendingClipboardToSend = null
                                }
                                val previewText = clipText.take(35).replace("\n", " ")
                                val sizeBytes = clipText.encodeToByteArray().size.toLong()
                                val formattedSize = "${net.syncswift.transfer.formatBytes(sizeBytes)} · ${clipText.length} chars"
                                net.syncswift.ui.components.FileDeviceSelectScreen(
                                    fileName = "${copy.clipboard}: $previewText",
                                    fileSizeFormatted = formattedSize,
                                    devices = selectableDevicesForTransfer,
                                    copy = copy,
                                    isDeviceConnected = { dev -> net.syncswift.connection.ConnectionManager.isDeviceConnected(dev) },
                                    onSelectDevice = { dev ->
                                        pendingClipboardToSend = null
                                        net.syncswift.transfer.TransferEngine.sendClipboard(
                                            targetHost = dev.host,
                                            targetPort = dev.port,
                                            targetDeviceName = dev.name,
                                            text = clipText
                                        )
                                    },
                                    onDismiss = {
                                        pendingClipboardToSend = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Universal Outgoing Request & Transfer Screen (Animated Checkmark / Cross - Full Page)
            val outgoingState by net.syncswift.connection.OutgoingStatusManager.state.collectAsState()
            AnimatedVisibility(
                visible = outgoingState != null,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                val state = outgoingState
                if (state != null) {
                    BackHandler {
                        if (state.step == net.syncswift.connection.OutgoingStep.WAITING_APPROVAL || state.step == net.syncswift.connection.OutgoingStep.IN_PROGRESS) {
                            net.syncswift.connection.OutgoingStatusManager.cancel()
                        } else {
                            net.syncswift.connection.OutgoingStatusManager.dismiss()
                        }
                    }
                    net.syncswift.ui.components.TransferStatusScreen(
                        state = state,
                        copy = copy,
                        onDismiss = {
                            net.syncswift.connection.OutgoingStatusManager.dismiss()
                        },
                        onCancel = {
                            net.syncswift.connection.OutgoingStatusManager.cancel()
                        }
                    )
                }
            }

            // Paywall 7-Day Trial Full Page Screen (No Modal)
            val showPaywall by net.syncswift.subscription.SubscriptionManager.showPaywall.collectAsState()
            val paywallReason by net.syncswift.subscription.SubscriptionManager.paywallReason.collectAsState()
            AnimatedVisibility(
                visible = showPaywall,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                if (showPaywall) {
                    BackHandler {
                        net.syncswift.subscription.SubscriptionManager.dismissPaywall()
                    }
                    net.syncswift.ui.components.PaywallScreen(
                        reason = paywallReason,
                        copy = copy,
                        onDismiss = {
                            net.syncswift.subscription.SubscriptionManager.dismissPaywall()
                        },
                        onNavigateToAuth = {
                            net.syncswift.subscription.SubscriptionManager.dismissPaywall()
                            inConnectedDevices = false
                            inConnectDesktop = false
                            inNotifications = false
                            inSettings = false
                            inEditProfile = false
                            inHelpSupport = false
                            screen = AuthStep.Email
                        }
                    )
                }
            }

            // MODAL: Maksimum Cihaz Sınırı Uyarısı & Eski Cihazımı Kaldır
            if (blockedDeviceLimitData != null) {
                val limitData = blockedDeviceLimitData!!
                net.syncswift.ui.components.SyncSwiftModal(
                    onDismissRequest = {
                        if (!isRemovingOldDevice) blockedDeviceLimitData = null
                    },
                    title = copy.maxDeviceLimitReached,
                    titleIcon = { WarningTriangleIcon(Modifier.size(20.dp), color = Blue) },
                    confirmText = copy.unlockWithPro,
                    confirmColor = Blue,
                    confirmTextColor = Color.White,
                    onConfirm = {
                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                            net.syncswift.subscription.PaywallReason.DEVICE_LIMIT
                        )
                    },
                    dismissText = copy.cancelAction,
                    onDismiss = {
                        if (!isRemovingOldDevice) blockedDeviceLimitData = null
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = copy.maxDeviceLimitReached,
                            color = Mist,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        Button(
                            onClick = { showOldDevicesModal = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InputBg,
                                contentColor = Cloud
                            ),
                            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = copy.removeOldDeviceAction,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // MODAL: Eski Cihaz Listesi
            if (showOldDevicesModal && blockedDeviceLimitData != null) {
                val limitData = blockedDeviceLimitData!!
                net.syncswift.ui.components.SyncSwiftModal(
                    onDismissRequest = {
                        if (!isRemovingOldDevice) showOldDevicesModal = false
                    },
                    title = copy.registeredDevicesTitle,
                    titleIcon = { DevicesIcon(Modifier.size(20.dp), color = Blue) },
                    confirmText = copy.closeAction,
                    confirmColor = InputBg,
                    confirmTextColor = Cloud,
                    onConfirm = { showOldDevicesModal = false },
                    onDismiss = { showOldDevicesModal = false }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        limitData.devices.forEach { oldDev ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = CardSurface,
                                border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MobileDeviceIcon(Modifier.size(20.dp), color = Blue)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = net.syncswift.discovery.cleanDeviceDisplayName(oldDev.name),
                                            color = Cloud,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = oldDev.osType,
                                            color = Mist,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ErrorRed.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.32f)),
                                        modifier = Modifier.clickable {
                                            oldDeviceToConfirmRemove = oldDev
                                        }
                                    ) {
                                        Text(
                                            text = copy.removeDeviceConfirmTitle,
                                            color = ErrorRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MODAL: Eski Cihazı Kaldır Onay
            if (oldDeviceToConfirmRemove != null && blockedDeviceLimitData != null) {
                val targetDev = oldDeviceToConfirmRemove!!
                val limitData = blockedDeviceLimitData!!
                net.syncswift.ui.components.SyncSwiftModal(
                    onDismissRequest = {
                        if (!isRemovingOldDevice) oldDeviceToConfirmRemove = null
                    },
                    title = copy.removeDeviceConfirmTitle,
                    titleIcon = { WarningTriangleIcon(Modifier.size(20.dp), color = ErrorRed) },
                    confirmText = copy.removeDeviceConfirmTitle,
                    confirmColor = ErrorRed,
                    confirmTextColor = Color.White,
                    confirmLoading = isRemovingOldDevice,
                    onConfirm = {
                        scope.launch {
                            isRemovingOldDevice = true
                            try {
                                SupabaseNetwork.removeUserDevice(limitData.email, targetDev.id)
                                showNativeToast(copy.deviceRemovedSuccess)
                                oldDeviceToConfirmRemove = null
                                showOldDevicesModal = false
                                val updatedDevices = limitData.devices.filter { it.id != targetDev.id }
                                if (updatedDevices.isEmpty()) {
                                    blockedDeviceLimitData = null
                                    commitUserRegistration(
                                        limitData.email,
                                        limitData.provider,
                                        limitData.firstName,
                                        limitData.lastName,
                                        limitData.password
                                    )
                                    authDraftEmail = ""
                                    screen = AuthStep.Home
                                } else {
                                    blockedDeviceLimitData = limitData.copy(devices = updatedDevices)
                                }
                            } finally {
                                isRemovingOldDevice = false
                            }
                        }
                    },
                    dismissText = copy.cancelAction,
                    onDismiss = {
                        if (!isRemovingOldDevice) oldDeviceToConfirmRemove = null
                    }
                ) {
                    Text(
                        text = copy.removeDeviceConfirmDesc,
                        color = Mist,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // Modern Minimalist Toast with modern contextual icon and swipe-to-dismiss
            val currentToastMessage by ToastNotifier.currentToast.collectAsState()
            var toastDragOffsetY by remember { mutableStateOf(0f) }

            LaunchedEffect(currentToastMessage) {
                toastDragOffsetY = 0f
                if (currentToastMessage != null) {
                    kotlinx.coroutines.delay(2800)
                    ToastNotifier.dismiss()
                }
            }

            AnimatedVisibility(
                visible = currentToastMessage != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(220)),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(animationSpec = androidx.compose.animation.core.tween(220)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp, start = 20.dp, end = 20.dp)
                    .zIndex(20000f)
            ) {
                val msg = currentToastMessage ?: ""
                val lower = msg.lowercase()
                val isError = lower.contains("hata") || lower.contains("error") || lower.contains("iptal") ||
                              lower.contains("reddedildi") || lower.contains("failed") || lower.contains("yanlış") ||
                              lower.contains("uyuşmuyor") || lower.contains("bulunamadı")
                val isSuccess = !isError && (lower.contains("başarı") || lower.contains("success") || lower.contains("senkronize") ||
                                lower.contains("bağlandı") || lower.contains("gönderildi") || lower.contains("alındı") ||
                                lower.contains("kaydedildi") || lower.contains("aktif"))
                val isClipboard = lower.contains("pano") || lower.contains("clipboard")

                val isDark = LocalSyncSwiftColors.current.isDark
                val iconColor = if (!isDark) {
                    Color.White
                } else {
                    when {
                        isError -> ErrorRed
                        isSuccess -> SuccessGreen
                        isClipboard -> Blue
                        else -> Color.White
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xF2121214),
                    border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .offset { IntOffset(0, toastDragOffsetY.roundToInt()) }
                        .alpha(((120f - toastDragOffsetY) / 120f).coerceIn(0f, 1f))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (toastDragOffsetY > 45f) {
                                        ToastNotifier.dismiss()
                                    }
                                    toastDragOffsetY = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0 || toastDragOffsetY > 0) {
                                        toastDragOffsetY = (toastDragOffsetY + dragAmount).coerceAtLeast(0f)
                                    }
                                }
                            )
                        }
                        .clickable { ToastNotifier.dismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(iconColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isError -> WarningTriangleIcon(Modifier.size(13.dp), color = iconColor)
                                isSuccess -> CheckIcon(Modifier.size(13.dp), color = iconColor)
                                isClipboard -> ClipboardVectorIcon(Modifier.size(13.dp), color = iconColor)
                                else -> NotificationBellIcon(Modifier.size(13.dp), color = iconColor)
                            }
                        }

                        Text(
                            text = msg,
                            color = Color(0xFFF2F2F5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}
}


