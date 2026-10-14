package com.getsyncdrop.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("syncdrop_preferences", MODE_PRIVATE)
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        setContent {
            var onboardingSeen by remember { mutableStateOf(preferences.getBoolean("onboarding_seen", false)) }
            SyncDropApp(
                onboardingSeen = onboardingSeen,
                onOnboardingFinished = {
                    preferences.edit().putBoolean("onboarding_seen", true).apply()
                    onboardingSeen = true
                    val shouldAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                        !preferences.getBoolean("notification_permission_asked", false)
                    if (shouldAsk) {
                        preferences.edit().putBoolean("notification_permission_asked", true).apply()
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
    }
}
