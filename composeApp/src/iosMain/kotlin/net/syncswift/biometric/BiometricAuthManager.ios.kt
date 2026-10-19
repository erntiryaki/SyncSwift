package net.syncswift.biometric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi
import net.syncswift.SessionManager

@OptIn(ExperimentalForeignApi::class)
actual object BiometricAuthManager {
    private val _isAppLocked = MutableStateFlow(false)
    actual val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    actual fun isSupported(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
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
        val context = LAContext()
        val reason = subtitle.ifBlank { title }
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                if (success) {
                    _isAppLocked.value = false
                }
                onResult(success)
            }
        }
    }
}
