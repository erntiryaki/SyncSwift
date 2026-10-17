package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.*

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

// Keep strong references to active ASWebAuthenticationSession and context provider to avoid ARC deallocation
private var activeAuthSession: ASWebAuthenticationSession? = null
private var activeContextProvider: WebAuthContextProvider? = null

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
            val iosClientId = "911753763379-r87i6iu84q9v71euhbf882us86g7bs74.apps.googleusercontent.com"
            val reversedScheme = "com.googleusercontent.apps.911753763379-r87i6iu84q9v71euhbf882us86g7bs74"
            val redirectUri = "$reversedScheme:/oauth2redirect"
            val authUrlString = "https://accounts.google.com/o/oauth2/v2/auth?" +
                    "client_id=$iosClientId&" +
                    "response_type=code&" +
                    "redirect_uri=$redirectUri&" +
                    "scope=openid%20profile%20email"
            val nsUrl = NSURL.URLWithString(authUrlString)

            if (nsUrl == null) {
                onError("Invalid OAuth URL")
            } else {
                val session = ASWebAuthenticationSession(
                    uRL = nsUrl,
                    callbackURLScheme = reversedScheme
                ) { callbackUrl, error ->
                    activeAuthSession = null
                    activeContextProvider = null
                    if (error != null) {
                        if (error.code.toInt() != 1) { // 1 = canceled by user
                            onError(error.localizedDescription ?: "Google Sign-In failed")
                        }
                        return@ASWebAuthenticationSession
                    }

                    if (callbackUrl != null) {
                        val urlStr = callbackUrl.absoluteString ?: ""
                        val code = Regex("""code=([^&]+)""").find(urlStr)?.groupValues?.get(1)?.substringBefore("#")
                        if (!code.isNullOrBlank()) {
                            scope.launch(Dispatchers.Default) {
                                try {
                                    val tokenUrl = NSURL.URLWithString("https://oauth2.googleapis.com/token") ?: return@launch
                                    val tokenReq = NSMutableURLRequest.requestWithURL(tokenUrl)
                                    tokenReq.setHTTPMethod("POST")
                                    tokenReq.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
                                    val postBody = "code=$code&client_id=$iosClientId&grant_type=authorization_code&redirect_uri=$redirectUri"
                                    tokenReq.setHTTPBody((postBody as NSString).dataUsingEncoding(NSUTF8StringEncoding))

                                    val sem = platform.darwin.dispatch_semaphore_create(0)
                                    var accessToken = ""
                                    var idToken = ""

                                    NSURLSession.sharedSession.dataTaskWithRequest(tokenReq) { data, _, reqErr ->
                                        if (reqErr == null && data != null) {
                                            val json = NSString.create(data, NSUTF8StringEncoding).toString()
                                            accessToken = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                                            idToken = Regex(""""id_token"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                                        }
                                        platform.darwin.dispatch_semaphore_signal(sem)
                                    }.resume()
                                    platform.darwin.dispatch_semaphore_wait(sem, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 8_000_000_000L))

                                    if (accessToken.isNotBlank()) {
                                        val userinfoUrl = NSURL.URLWithString("https://www.googleapis.com/oauth2/v3/userinfo") ?: return@launch
                                        val userReq = NSMutableURLRequest.requestWithURL(userinfoUrl)
                                        userReq.setHTTPMethod("GET")
                                        userReq.setValue("Bearer $accessToken", forHTTPHeaderField = "Authorization")

                                        val sem2 = platform.darwin.dispatch_semaphore_create(0)
                                        var userEmail = ""
                                        var userName = ""
                                        var userGivenName = ""
                                        var userFamilyName = ""

                                        NSURLSession.sharedSession.dataTaskWithRequest(userReq) { uData, _, uErr ->
                                            if (uErr == null && uData != null) {
                                                val uJson = NSString.create(uData, NSUTF8StringEncoding).toString()
                                                userEmail = Regex(""""email"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                                userName = Regex(""""name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                                userGivenName = Regex(""""given_name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                                userFamilyName = Regex(""""family_name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                            }
                                            platform.darwin.dispatch_semaphore_signal(sem2)
                                        }.resume()
                                        platform.darwin.dispatch_semaphore_wait(sem2, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 8_000_000_000L))

                                        if (userEmail.isNotBlank()) {
                                            withContext(Dispatchers.Main) {
                                                onSuccess(
                                                    GoogleUserData(
                                                        idToken = idToken.ifBlank { accessToken },
                                                        email = userEmail,
                                                        displayName = userName.ifBlank { null },
                                                        givenName = userGivenName.ifBlank { null },
                                                        familyName = userFamilyName.ifBlank { null }
                                                    )
                                                )
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) { onError("Google profili alınamadı.") }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) { onError("Google ile giriş tamamlanamadı.") }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { onError(e.message ?: "Kimlik doğrulama hatası") }
                                }
                            }
                        } else {
                            onError("Google yetkilendirme kodu alınamadı.")
                        }
                    }
                }

                session.presentationContextProvider = contextProvider
                session.prefersEphemeralWebBrowserSession = false
                activeAuthSession = session
                activeContextProvider = contextProvider
                dispatch_async(dispatch_get_main_queue()) {
                    session.start()
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Google ile giriş başlatılamadı.")
        }
    }
}
