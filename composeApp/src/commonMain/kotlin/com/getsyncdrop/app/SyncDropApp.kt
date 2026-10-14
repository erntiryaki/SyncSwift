package com.getsyncdrop.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import com.getsyncdrop.app.ui.auth.*
import com.getsyncdrop.app.ui.home.HomeScreen
import com.getsyncdrop.app.ui.onboarding.OnboardingScreen
import com.getsyncdrop.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SyncDropApp(onboardingSeen: Boolean = false, onOnboardingFinished: () -> Unit = {}) {
    var screen by remember(onboardingSeen) { mutableStateOf(if (onboardingSeen) AuthStep.Email else AuthStep.Onboarding) }
    var language by remember { mutableStateOf(AppLanguage.English) }
    var email by remember { mutableStateOf("") }
    var authProvider by remember { mutableStateOf(IdentityProvider.EmailPassword) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var currentOtpCode by remember { mutableStateOf("123456") }
    val authPolicy = remember { AuthPolicy() }
    val scope = rememberCoroutineScope()

    val copy = copyFor(language)
    val direction = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    // Handle double-back press to exit app on root screens
    if (screen == AuthStep.Onboarding || screen == AuthStep.Email || screen == AuthStep.Home) {
        DoubleBackToExitHandler(exitMessage = copy.exitToast)
    }

    // Commit User to AuthPolicy & Database ONLY when reaching Home Screen!
    fun commitUserRegistration(userEmail: String, provider: IdentityProvider, firstName: String = "", lastName: String = "") {
        authPolicy.registerAccount(userEmail, provider)
        scope.launch {
            SupabaseNetwork.registerUserInSupabase(
                email = userEmail,
                firstName = firstName,
                lastName = lastName,
                provider = provider
            )
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        SyncDropTheme {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = platformFontFamily())) {
                Surface(Modifier.fillMaxSize(), color = Night) {
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            AuthStep.Onboarding -> OnboardingScreen(
                                copy = copy,
                                language = language,
                                setLanguage = { language = it },
                                continueToAuth = {
                                    screen = AuthStep.Email
                                    onOnboardingFinished()
                                }
                            )
                            AuthStep.Email -> EmailAuthScreen(
                                copy = copy,
                                email = email,
                                updateEmail = { email = it },
                                next = {
                                    val existingProvider = authPolicy.getRegisteredProvider(email)
                                    if (existingProvider == IdentityProvider.Google) {
                                        // Block email sign in for Google-registered account!
                                        alertMessage = copy.crossProviderGoogleToEmail
                                    } else if (existingProvider == IdentityProvider.EmailPassword) {
                                        authProvider = IdentityProvider.EmailPassword
                                        screen = AuthStep.Password
                                    } else {
                                        // Send 6-digit OTP code via Brevo REST API!
                                        val code = (100000..999999).random().toString()
                                        currentOtpCode = code
                                        authProvider = IdentityProvider.EmailPassword
                                        scope.launch {
                                            BrevoOtpGateway().sendCode(email, code)
                                        }
                                        screen = AuthStep.Otp
                                    }
                                },
                                social = {
                                    val targetEmail = if (email.isBlank()) "erntiryaki@gmail.com" else email
                                    val existingProvider = authPolicy.getRegisteredProvider(targetEmail)
                                    if (existingProvider == IdentityProvider.EmailPassword) {
                                        // Block Google sign in for Email-registered account!
                                        alertMessage = copy.crossProviderEmailToGoogle
                                    } else {
                                        authProvider = IdentityProvider.Google
                                        commitUserRegistration(targetEmail, IdentityProvider.Google, "Eren", "Tiryaki")
                                        screen = AuthStep.Home
                                    }
                                },
                                language = language,
                                setLanguage = { language = it }
                            )
                            AuthStep.Password -> PasswordAuthScreen(
                                copy = copy,
                                done = {
                                    commitUserRegistration(email, IdentityProvider.EmailPassword)
                                    screen = AuthStep.Home
                                },
                                language = language,
                                setLanguage = { language = it }
                            )
                            AuthStep.Otp -> OtpAuthScreen(
                                copy = copy,
                                email = email,
                                verified = { screen = AuthStep.Profile },
                                language = language,
                                setLanguage = { language = it }
                            )
                            AuthStep.Profile -> ProfileAuthScreen(
                                copy = copy,
                                finished = {
                                    // User completed profile -> Commit account and navigate to Home!
                                    commitUserRegistration(email, IdentityProvider.EmailPassword)
                                    screen = AuthStep.Home
                                },
                                language = language,
                                setLanguage = { language = it }
                            )
                            AuthStep.Home -> HomeScreen(
                                copy = copy,
                                language = language,
                                setLanguage = { language = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
