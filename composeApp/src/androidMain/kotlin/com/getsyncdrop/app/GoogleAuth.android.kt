package com.getsyncdrop.app

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onSuccess: (idToken: String) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return remember(webClientId) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                Log.e("GoogleAuth", "Activity context not found")
                onError("Activity context not found")
            } else {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        try {
                            Log.d("GoogleAuth", "Launching Native Google One Tap with WebClientID: $webClientId")
                            val credentialManager = CredentialManager.create(activity)

                            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInWithGoogleOption)
                                .build()

                            val response = credentialManager.getCredential(activity, request)
                            val credential = response.credential

                            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                Log.d("GoogleAuth", "Native Google Token Received: $idToken")
                                Toast.makeText(activity, "Google Girişi Başarılı!", Toast.LENGTH_SHORT).show()
                                onSuccess(idToken)
                            } else {
                                Log.e("GoogleAuth", "Unsupported credential type: ${credential.type}")
                                onError("Unsupported credential type")
                            }
                        } catch (e: GetCredentialCancellationException) {
                            Log.d("GoogleAuth", "Google Sign-In cancelled by user")
                        } catch (e: GetCredentialException) {
                            Log.e("GoogleAuth", "Native One Tap Error: ${e.type} - ${e.message}", e)
                            Toast.makeText(activity, "Google Auth: ${e.message}", Toast.LENGTH_SHORT).show()
                            onError(e.message ?: "Google Sign-In failed")
                        } catch (e: Exception) {
                            Log.e("GoogleAuth", "Unexpected Exception: ${e.message}", e)
                            Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            onError(e.message ?: "Google Sign-In failed")
                        }
                    }
                }
            }
        }
    }
}
