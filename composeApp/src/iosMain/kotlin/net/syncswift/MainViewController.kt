package net.syncswift

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    SyncSwiftApp(
        onboardingSeen = SessionManager.isOnboardingSeen(),
        onOnboardingFinished = {
            SessionManager.saveOnboardingSeen(true)
            requestIosPermissions()
        }
    )
}
