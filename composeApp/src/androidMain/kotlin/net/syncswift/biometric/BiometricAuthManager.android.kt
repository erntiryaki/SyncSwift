package net.syncswift.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager

actual object BiometricAuthManager {
    private val _isAppLocked = MutableStateFlow(false)
    actual val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _promptTrigger = MutableStateFlow(0)
    actual val promptTrigger: StateFlow<Int> = _promptTrigger.asStateFlow()

    actual fun requestPrompt() {
        _promptTrigger.value += 1
    }

    actual fun isSupported(): Boolean {
        val activity = SessionManager.getCurrentActivity() ?: return false
        val bm = BiometricManager.from(activity)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.BIOMETRIC_WEAK or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual fun isEnabled(): Boolean {
        return SessionManager.getSavedBiometricEnabled()
    }

    actual fun setEnabled(enabled: Boolean) {
        SessionManager.saveBiometricEnabled(enabled)
    }

    actual fun lockApp() {
        if (isEnabled()) {
            _isAppLocked.value = true
        }
    }

    actual fun unlockApp() {
        _isAppLocked.value = false
    }

    actual fun authenticate(title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        val activity = SessionManager.getCurrentActivity() as? FragmentActivity
        if (activity == null) {
            onResult(false)
            return
        }

        activity.runOnUiThread {
            try {
                val executor = ContextCompat.getMainExecutor(activity)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        _isAppLocked.value = false
                        onResult(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onResult(false)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onResult(false)
                    }
                })

                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
