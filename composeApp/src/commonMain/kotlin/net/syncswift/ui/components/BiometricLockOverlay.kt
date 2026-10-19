package net.syncswift.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import net.syncswift.Copy
import net.syncswift.FingerprintIcon
import net.syncswift.biometric.BiometricAuthManager
import net.syncswift.ui.theme.*

@Composable
fun BiometricLockOverlay(
    copy: Copy,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val promptTrigger by BiometricAuthManager.promptTrigger.collectAsState()

    // Auto-prompt on appearance and every time app returns from background
    LaunchedEffect(promptTrigger) {
        kotlinx.coroutines.delay(200)
        BiometricAuthManager.authenticate(
            title = copy.biometricPromptTitle,
            subtitle = copy.biometricPromptSubtitle
        ) { success ->
            if (success) {
                BiometricAuthManager.unlockApp()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(9999f)
            .background(
                if (isDark) Color(0xEB0B101B) else Color(0xEFF8FAFC)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Block touches through */ }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing circular icon container
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable {
                        BiometricAuthManager.authenticate(
                            title = copy.biometricPromptTitle,
                            subtitle = copy.biometricPromptSubtitle
                        ) { success ->
                            if (success) {
                                BiometricAuthManager.unlockApp()
                            }
                        }
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Blue.copy(alpha = 0.25f),
                                Blue.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Blue.copy(alpha = 0.6f),
                                Blue.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Blue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    FingerprintIcon(
                        modifier = Modifier.size(40.dp),
                        color = Blue
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = copy.biometricPromptTitle,
                color = Cloud,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = copy.biometricPromptSubtitle,
                color = Mist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(36.dp))

            // Unlock CTA Button
            MainPill(
                text = copy.unlockAction,
                enabled = true,
                action = {
                    BiometricAuthManager.authenticate(
                        title = copy.biometricPromptTitle,
                        subtitle = copy.biometricPromptSubtitle
                    ) { success ->
                        if (success) {
                            BiometricAuthManager.unlockApp()
                        }
                    }
                }
            )
        }
    }
}
