package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class AppleSignInHandler(
    private val onSuccess: (AppleUserData) -> Unit,
    private val onError: (String) -> Unit
) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential != null) {
            val userIdentifier = credential.user
            val idTokenData = credential.identityToken
            val idToken = idTokenData?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() } ?: userIdentifier
            val jwtEmail = decodeAppleJwtEmail(idToken)
            val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults

            var email = credential.email?.ifBlank { null }
                ?: jwtEmail?.ifBlank { null }
                ?: defaults.stringForKey("apple_email_$userIdentifier")?.ifBlank { null }
                ?: ""

            if (email.isNotBlank()) {
                defaults.setObject(email, "apple_email_$userIdentifier")
            }

            var givenName = credential.fullName?.givenName?.ifBlank { null }
                ?: credential.fullName?.nickname?.ifBlank { null }

            var familyName = credential.fullName?.familyName?.ifBlank { null }
                ?: credential.fullName?.middleName?.ifBlank { null }

            // If givenName contains digits or looks like an email prefix, invalidate it
            if (givenName != null && (givenName!!.any { it.isDigit() } || givenName!!.contains("@"))) {
                givenName = null
                familyName = null
            }

            if (givenName.isNullOrBlank()) {
                val cachedGiven = defaults.stringForKey("apple_given_name_$userIdentifier")?.ifBlank { null }
                    ?: defaults.stringForKey("apple_given_name")?.ifBlank { null }
                    ?: SessionManager.getUserFirstName().ifBlank { null }
                if (!cachedGiven.isNullOrBlank() && !cachedGiven.any { it.isDigit() } && !cachedGiven.contains("@")) {
                    givenName = cachedGiven
                }
            }
            if (familyName.isNullOrBlank()) {
                val cachedFamily = defaults.stringForKey("apple_family_name_$userIdentifier")?.ifBlank { null }
                    ?: defaults.stringForKey("apple_family_name")?.ifBlank { null }
                    ?: SessionManager.getUserLastName().ifBlank { null }
                if (!cachedFamily.isNullOrBlank() && !cachedFamily.any { it.isDigit() } && !cachedFamily.contains("@")) {
                    familyName = cachedFamily
                }
            }

            // Fallback 1: Extract from device name (e.g. "Eren's iPhone", "Eren Tiryaki's iPhone", "Miraç’ın iPhone’u", "Miraç adlı kişinin iPhone'u")
            var devFirstName = ""
            var devLastName = ""
            try {
                val devName = platform.UIKit.UIDevice.currentDevice.name
                val cleaned = devName
                    .replace(Regex("""(?i)^(?:iPhone|iPad|Mac)\s+(?:de|di|von)\s+"""), "")
                    .replace(Regex("""(?i)\s+adlı\s+kişinin\s+(?:iPhone|iPad|Mac|Apple\s*Watch|iPod).*"""), "")
                    .replace(Regex("""(?i)['’‘´`]?(?:s|in|ın|un|ün|nin|nın|nun|nün)\s+(?:iPhone|iPad|Mac|Apple\s*Watch|iPod|Telefon|Device).*"""), "")
                    .replace(Regex("""(?i)\s+(?:iPhone|iPad|Mac|Apple\s*Watch|iPod|Telefon|Device).*"""), "")
                    .replace(Regex("""['’‘´`].*"""), "")
                    .trim()
                if (cleaned.isNotBlank() && !cleaned.equals("iPhone", ignoreCase = true) && !cleaned.equals("iPad", ignoreCase = true) && !cleaned.equals("Mac", ignoreCase = true)) {
                    val parts = cleaned.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        devFirstName = parts.first()
                        devLastName = parts.drop(1).joinToString(" ")
                    } else if (parts.size == 1) {
                        devFirstName = parts.first()
                    }
                }
            } catch (_: Exception) {}

            val (extractedFirst, extractedLast) = SmartNameExtractor.extract(
                email = email,
                hintFirstName = givenName ?: devFirstName,
                hintLastName = familyName ?: devLastName
            )
            givenName = extractedFirst
            familyName = extractedLast

            if (!givenName.isNullOrBlank()) {
                defaults.setObject(givenName, "apple_given_name_$userIdentifier")
                defaults.setObject(givenName, "apple_given_name")
            }
            if (!familyName.isNullOrBlank()) {
                defaults.setObject(familyName, "apple_family_name_$userIdentifier")
                defaults.setObject(familyName, "apple_family_name")
            }

            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                onSuccess(
                    AppleUserData(
                        idToken = idToken,
                        email = email,
                        userIdentifier = userIdentifier,
                        givenName = givenName,
                        familyName = familyName
                    )
                )
            }
        } else {
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                onError("No Apple ID credentials received.")
            }
        }
    }

    private fun decodeAppleJwtEmail(idToken: String): String? {
        try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            var base64 = parts[1].replace("-", "+").replace("_", "/")
            while (base64.length % 4 != 0) {
                base64 += "="
            }
            val nsData = platform.Foundation.NSData.create(base64Encoding = base64) ?: return null
            val json = platform.Foundation.NSString.create(nsData, platform.Foundation.NSUTF8StringEncoding)?.toString() ?: return null
            val match = Regex(""""email"\s*:\s*"([^"]+)"""").find(json)
            return match?.groupValues?.get(1)
        } catch (_: Exception) {
            return null
        }
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError
    ) {
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
            if (didCompleteWithError.code == 1001L) {
                onError("canceled")
            } else {
                val errorMsg = didCompleteWithError.localizedDescription ?: "Sign in with Apple was cancelled or failed."
                onError(errorMsg)
            }
        }
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController
    ): UIWindow {
        val scenes = UIApplication.sharedApplication.connectedScenes
        for (scene in scenes) {
            val windowScene = scene as? platform.UIKit.UIWindowScene ?: continue
            val keyWin = windowScene.windows.mapNotNull { it as? UIWindow }.firstOrNull { it.isKeyWindow() }
            if (keyWin != null) return keyWin
            val anyWin = windowScene.windows.firstOrNull() as? UIWindow
            if (anyWin != null) return anyWin
        }
        val appWindows = UIApplication.sharedApplication.windows
        val fallback = (appWindows.firstOrNull() as? UIWindow) ?: UIApplication.sharedApplication.keyWindow
        return fallback ?: UIWindow()
    }
}

@Composable
actual fun rememberAppleAuthLauncher(
    onSuccess: (userData: AppleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    val handler = remember(onSuccess, onError) {
        AppleSignInHandler(onSuccess, onError)
    }

    return {
        try {
            val provider = ASAuthorizationAppleIDProvider()
            val request = provider.createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
            }
            val controller = ASAuthorizationController(listOf(request)).apply {
                delegate = handler
                presentationContextProvider = handler
            }
            controller.performRequests()
        } catch (e: Exception) {
            onError(e.message ?: "Could not launch Apple Sign-In.")
        }
    }
}
