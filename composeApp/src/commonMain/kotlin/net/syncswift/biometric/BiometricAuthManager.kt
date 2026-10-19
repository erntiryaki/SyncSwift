package net.syncswift.biometric

import kotlinx.coroutines.flow.StateFlow

expect object BiometricAuthManager {
    val isAppLocked: StateFlow<Boolean>
    fun isSupported(): Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun lockApp()
    fun unlockApp()
    fun authenticate(title: String, subtitle: String, onResult: (Boolean) -> Unit)
}
