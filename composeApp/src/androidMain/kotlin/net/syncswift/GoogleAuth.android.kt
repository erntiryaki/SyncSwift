package net.syncswift

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
actual fun rememberGoogleAuthLauncher(
    webClientId: String,
    onSuccess: (userData: GoogleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val oneTapClient = remember(context) {
        activity?.let { Identity.getSignInClient(it) }
    }

    // Fallback standard Google Sign-In launcher (in case One Tap is in cooldown/unavailable)
    val standardGoogleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken ?: ""
                val email = account?.email ?: ""
                val displayName = account?.displayName
                val givenName = account?.givenName
                val familyName = account?.familyName

                Log.d("GoogleAuth", "Standard Google Account Selected: $email")
                onSuccess(
                    GoogleUserData(
                        idToken = idToken,
                        email = email,
                        displayName = displayName,
                        givenName = givenName,
                        familyName = familyName
                    )
                )
            } catch (e: ApiException) {
                Log.e("GoogleAuth", "Standard Google Sign-In failed: ${e.statusCode} - ${e.message}", e)
                onError(e.message ?: "Google Sign-In failed")
            }
        } else {
            Log.d("GoogleAuth", "Standard Google Sign-In cancelled or failed with resultCode=${result.resultCode}")
            onError("canceled")
        }
    }

    // Google Play Services One Tap Bottom-Sheet Launcher
    val oneTapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient?.getSignInCredentialFromIntent(result.data)
                if (credential != null) {
                    val idToken = credential.googleIdToken ?: ""
                    val email = credential.id // Email address of the selected Google Account
                    val displayName = credential.displayName
                    val givenName = credential.givenName
                    val familyName = credential.familyName

                    Log.d("GoogleAuth", "Google One Tap Account Selected: $email ($displayName)")
                    onSuccess(
                        GoogleUserData(
                            idToken = idToken,
                            email = email,
                            displayName = displayName,
                            givenName = givenName,
                            familyName = familyName
                        )
                    )
                } else {
                    onError("No credential received from Google One Tap")
                }
            } catch (e: ApiException) {
                Log.e("GoogleAuth", "Google One Tap credential extraction error: ${e.statusCode} - ${e.message}", e)
                onError(e.message ?: "Google Sign-In failed")
            }
        } else {
            Log.d("GoogleAuth", "Google One Tap dismissed by user (resultCode=${result.resultCode})")
            onError("canceled")
        }
    }

    fun launchStandardFallback() {
        if (activity == null) return
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        // Sign out client first to ensure account chooser is always displayed!
        client.signOut().addOnCompleteListener {
            standardGoogleLauncher.launch(client.signInIntent)
        }
    }

    return {
        if (activity == null || oneTapClient == null) {
            Log.e("GoogleAuth", "Activity or One Tap Client not available")
            onError("Activity not available")
        } else {
            Log.d("GoogleAuth", "Starting Google One Tap Sheet with WebClientId: $webClientId")
            val signInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(
                    BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(false)
                        .build()
                )
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false) // Show all Google accounts on device
                        .build()
                )
                .setAutoSelectEnabled(false) // Do NOT auto-select default account, user MUST select!
                .build()

            oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(activity) { result ->
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                        oneTapLauncher.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        Log.e("GoogleAuth", "Error launching One Tap IntentSender, falling back to standard sign-in", e)
                        launchStandardFallback()
                    }
                }
                .addOnFailureListener(activity) { e ->
                    Log.w("GoogleAuth", "Google One Tap failed (cooldown or unsupported), falling back to standard sign-in: ${e.message}")
                    launchStandardFallback()
                }
        }
    }
}
