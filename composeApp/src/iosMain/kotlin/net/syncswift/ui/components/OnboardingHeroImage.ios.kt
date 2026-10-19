package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import net.syncswift.resources.*

@Composable
actual fun OnboardingHeroImage(step: Int, modifier: Modifier) {
    val res = when (step) {
        0 -> Res.drawable.onboarding_1
        1 -> Res.drawable.onboarding_2
        else -> Res.drawable.onboarding_3
    }
    Image(
        painter = painterResource(res),
        contentDescription = "SyncSwift Onboarding Step ${step + 1}",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
