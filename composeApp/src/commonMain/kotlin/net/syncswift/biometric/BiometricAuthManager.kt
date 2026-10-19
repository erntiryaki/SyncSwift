package net.syncswift.biometric

import kotlinx.coroutines.flow.StateFlow

expect object BiometricAuthManager {
    val isAppLocked: StateFlow<Boolean>
    val promptTrigger: StateFlow<Int>
    fun isSupported(): Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun lockApp()
    fun unlockApp()
    fun requestPrompt()
    fun authenticate(title: String, subtitle: String, onResult: (Boolean) -> Unit)
}
