package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class WebAuthContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
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

// Keep a strong reference to active ASWebAuthenticationSession to avoid ARC deallocation
private var activeAuthSession: ASWebAuthenticationSession? = null

@Composable
actual fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (userData: GoogleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val contextProvider = remember { WebAuthContextProvider() }

    return {
        try {
            val supabaseUrl = PlatformConfig.supabaseUrl
            val anonKey = PlatformConfig.supabaseAnonKey
            val redirectUri = "net.syncswift://auth-callback"
            val authUrlString = "$supabaseUrl/auth/v1/authorize?provider=google&redirect_to=$redirectUri"
            val nsUrl = NSURL.URLWithString(authUrlString)

            if (nsUrl == null) {
                onError("Invalid OAuth URL")
            } else {
                val session = ASWebAuthenticationSession(
                    uRL = nsUrl,
                    callbackURLScheme = "net.syncswift"
                ) { callbackUrl, error ->
                activeAuthSession = null
                if (error != null) {
                    if (error.code.toInt() != 1) { // 1 = canceled by user
                        onError(error.localizedDescription ?: "Google Sign-In failed")
                    }
                    return@ASWebAuthenticationSession
                }

                if (callbackUrl != null) {
                    val urlStr = callbackUrl.absoluteString ?: ""
                    val fragment = callbackUrl.fragment ?: urlStr.substringAfter("#", "")
                    val params = fragment.split("&").associate { param ->
                        val parts = param.split("=", limit = 2)
                        parts[0] to (parts.getOrNull(1) ?: "")
                    }

                    val accessToken = params["access_token"]
                    if (!accessToken.isNullOrBlank()) {
                        scope.launch(Dispatchers.Default) {
                            try {
                                val userUrl = NSURL.URLWithString("$supabaseUrl/auth/v1/user") ?: return@launch
                                val req = NSMutableURLRequest.requestWithURL(userUrl)
                                req.setHTTPMethod("GET")
                                req.setValue("Bearer $accessToken", forHTTPHeaderField = "Authorization")
                                req.setValue(anonKey, forHTTPHeaderField = "apikey")

                                val sem = platform.darwin.dispatch_semaphore_create(0)
                                var userEmail = ""
                                var userGivenName: String? = null
                                var userFamilyName: String? = null
                                var userFullName: String? = null

                                NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, reqErr ->
                                    if (reqErr == null && data != null) {
                                        val jsonString = NSString.create(data, NSUTF8StringEncoding).toString()
                                        userEmail = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)?.groupValues?.get(1) ?: ""
                                        userFullName = Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)?.groupValues?.get(1)
                                            ?: Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)?.groupValues?.get(1)
                                        userGivenName = Regex("\"given_name\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)?.groupValues?.get(1)
                                        userFamilyName = Regex("\"family_name\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)?.groupValues?.get(1)
                                    }
                                    platform.darwin.dispatch_semaphore_signal(sem)
                                }.resume()

                                platform.darwin.dispatch_semaphore_wait(sem, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 6_000_000_000L))

                                if (userEmail.isNotBlank()) {
                                    val gUser = GoogleUserData(
                                        idToken = accessToken,
                                        email = userEmail,
                                        displayName = userFullName,
                                        givenName = userGivenName ?: userFullName?.split(" ")?.firstOrNull(),
                                        familyName = userFamilyName ?: userFullName?.split(" ")?.drop(1)?.joinToString(" ")
                                    )
                                    onSuccess(gUser)
                                } else {
                                    onError("Could not retrieve Google profile.")
                                }
                            } catch (e: Exception) {
                                onError(e.message ?: "Authentication error")
                            }
                        }
                    } else {
                        onError("No access token received from Google.")
                    }
                }
            }

                session.presentationContextProvider = contextProvider
                session.prefersEphemeralWebBrowserSession = false
                activeAuthSession = session
                session.start()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Could not launch Google Sign-In.")
        }
    }
}
