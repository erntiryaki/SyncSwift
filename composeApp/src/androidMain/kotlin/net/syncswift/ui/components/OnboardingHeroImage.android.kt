package net.syncswift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import net.syncswift.R

@Composable
actual fun OnboardingHeroImage(step: Int, modifier: Modifier) {
    val resId = when (step) {
        0 -> R.drawable.onboarding_1
        1 -> R.drawable.onboarding_2
        else -> R.drawable.onboarding_3
    }
    Image(
        painter = painterResource(resId),
        contentDescription = "SyncSwift Onboarding Step ${step + 1}",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
