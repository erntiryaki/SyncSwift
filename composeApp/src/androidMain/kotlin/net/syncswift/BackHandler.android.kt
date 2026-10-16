package net.syncswift

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun DoubleBackToExitHandler(exitMessage: String) {
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    AndroidBackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000L) {
            (context as? Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}
