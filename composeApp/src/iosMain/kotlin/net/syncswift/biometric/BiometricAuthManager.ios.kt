package net.syncswift.biometric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import net.syncswift.SessionManager

@OptIn(ExperimentalForeignApi::class)
actual object BiometricAuthManager {
    private val _isAppLocked = MutableStateFlow(false)
    actual val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _promptTrigger = MutableStateFlow(0)
    actual val promptTrigger: StateFlow<Int> = _promptTrigger.asStateFlow()

    init {
        // Lock immediately whenever the app enters background or screen is locked
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            if (isEnabled()) {
                lockApp()
            }
        }

        // Auto-request Face ID / Touch ID prompt whenever the app returns to foreground / active
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            if (isEnabled() && _isAppLocked.value) {
                requestPrompt()
            }
        }
    }

    actual fun requestPrompt() {
        _promptTrigger.value += 1
    }

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
