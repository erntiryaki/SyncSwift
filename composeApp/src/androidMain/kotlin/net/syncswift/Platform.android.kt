package net.syncswift

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Android
actual fun currentTimeMs(): Long = System.currentTimeMillis()
actual fun getSystemLanguageCode(): String = java.util.Locale.getDefault().language ?: "en"
actual fun showNativeToast(message: String) {
    SessionManager.getAppContext()?.let { ctx ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@androidx.compose.runtime.Composable
actual fun UpdateSystemBars(isDark: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }
}

