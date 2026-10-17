package net.syncswift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

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

private val googleAuthScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

@OptIn(ExperimentalForeignApi::class)
private suspend fun httpPostUrlEncoded(
    urlStr: String,
    params: Map<String, String>
): Pair<Int, String> = suspendCancellableCoroutine { cont ->
    val url = NSURL.URLWithString(urlStr)
    if (url == null) {
        cont.resume(Pair(-1, ""))
        return@suspendCancellableCoroutine
    }
    val req = NSMutableURLRequest.requestWithURL(url)
    req.setHTTPMethod("POST")
    req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
    req.setTimeoutInterval(15.0)

    val allowedChars = NSCharacterSet.alphanumericCharacterSet
    val bodyStr = params.entries.joinToString("&") { (k, v) ->
        val encK = (k as NSString).stringByAddingPercentEncodingWithAllowedCharacters(allowedChars) ?: k
        val encV = (v as NSString).stringByAddingPercentEncodingWithAllowedCharacters(allowedChars) ?: v
        "$encK=$encV"
    }
    val data = (bodyStr as NSString).dataUsingEncoding(NSUTF8StringEncoding)
    req.setHTTPBody(data)

    val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { resData, response, error ->
        val httpResp = response as? NSHTTPURLResponse
        val code = httpResp?.statusCode?.toInt() ?: if (error != null) -1 else 200
        val resStr = if (resData != null) {
            NSString.create(resData, NSUTF8StringEncoding)?.toString() ?: ""
        } else ""
        if (cont.isActive) {
            cont.resume(Pair(code, resStr))
        }
    }
    task.resume()
    cont.invokeOnCancellation { task.cancel() }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun httpGetJson(
    urlStr: String,
    bearerToken: String
): Pair<Int, String> = suspendCancellableCoroutine { cont ->
    val url = NSURL.URLWithString(urlStr)
    if (url == null) {
        cont.resume(Pair(-1, ""))
        return@suspendCancellableCoroutine
    }
    val req = NSMutableURLRequest.requestWithURL(url)
    req.setHTTPMethod("GET")
    req.setValue("Bearer $bearerToken", forHTTPHeaderField = "Authorization")
    req.setTimeoutInterval(15.0)

    val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { resData, response, error ->
        val httpResp = response as? NSHTTPURLResponse
        val code = httpResp?.statusCode?.toInt() ?: if (error != null) -1 else 200
        val resStr = if (resData != null) {
            NSString.create(resData, NSUTF8StringEncoding)?.toString() ?: ""
        } else ""
        if (cont.isActive) {
            cont.resume(Pair(code, resStr))
        }
    }
    task.resume()
    cont.invokeOnCancellation { task.cancel() }
}

@Composable
actual fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (userData: GoogleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
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
                            dispatch_async(dispatch_get_main_queue()) {
                                onError(error.localizedDescription ?: "Google Sign-In failed")
                            }
                        }
                        return@ASWebAuthenticationSession
                    }

                    if (callbackUrl != null) {
                        var code: String? = null
                        try {
                            val components = NSURLComponents.componentsWithURL(callbackUrl, resolvingAgainstBaseURL = false)
                            val items = components?.queryItems as? List<*>
                            val item = items?.mapNotNull { it as? NSURLQueryItem }?.firstOrNull { it.name == "code" }
                            code = item?.value
                        } catch (_: Throwable) {}

                        if (code.isNullOrBlank()) {
                            val urlStr = callbackUrl.absoluteString ?: ""
                            code = Regex("""code=([^&]+)""").find(urlStr)?.groupValues?.get(1)?.substringBefore("#")
                        }

                        if (!code.isNullOrBlank()) {
                            googleAuthScope.launch {
                                try {
                                    val (tokenStatus, tokenJson) = httpPostUrlEncoded(
                                        urlStr = "https://oauth2.googleapis.com/token",
                                        params = mapOf(
                                            "code" to code,
                                            "client_id" to iosClientId,
                                            "grant_type" to "authorization_code",
                                            "redirect_uri" to redirectUri
                                        )
                                    )

                                    val accessToken = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(tokenJson)?.groupValues?.get(1) ?: ""
                                    val idToken = Regex(""""id_token"\s*:\s*"([^"]+)"""").find(tokenJson)?.groupValues?.get(1) ?: ""

                                    if (accessToken.isNotBlank()) {
                                        val (userStatus, uJson) = httpGetJson(
                                            urlStr = "https://www.googleapis.com/oauth2/v3/userinfo",
                                            bearerToken = accessToken
                                        )

                                        val userEmail = Regex(""""email"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                        val userName = Regex(""""name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                        val userGivenName = Regex(""""given_name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""
                                        val userFamilyName = Regex(""""family_name"\s*:\s*"([^"]+)"""").find(uJson)?.groupValues?.get(1) ?: ""

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
                            dispatch_async(dispatch_get_main_queue()) {
                                onError("Google yetkilendirme kodu alınamadı.")
                            }
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
