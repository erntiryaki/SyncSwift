package net.syncswift

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    if (net.syncswift.biometric.BiometricAuthManager.isEnabled()) {
        net.syncswift.biometric.BiometricAuthManager.lockApp()
    }

    LaunchedEffect(Unit) {
        requestIosPermissions()
        try {
            net.syncswift.transfer.TransferEngine.startForegroundServer()
            net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
        } catch (_: Throwable) {}
    }

    SyncSwiftApp(
        onboardingSeen = SessionManager.isOnboardingSeen(),
        onOnboardingFinished = {
            SessionManager.saveOnboardingSeen(true)
            requestIosPermissions()
            try {
                net.syncswift.discovery.DeviceDiscoveryManager.startDiscovery()
            } catch (_: Throwable) {}
        }
    )
}
