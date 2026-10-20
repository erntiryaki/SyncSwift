package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView
import platform.UIKit.UIApplication
import platform.Foundation.NSClassFromString

import androidx.compose.ui.draw.alpha

private var iosAdBannerProvider: ((String) -> UIView)? = null

fun setIosAdBannerProvider(provider: (String) -> UIView) {
    iosAdBannerProvider = provider
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBannerView(modifier: Modifier) {
    val isModalOpen by AdBannerManager.isModalOpen.collectAsState()

    val adUnit = net.syncswift.PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/2934735716"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isModalOpen) 0.dp else 50.dp)
            .alpha(if (isModalOpen) 0f else 1f)
            .background(if (isModalOpen) androidx.compose.ui.graphics.Color.Transparent else net.syncswift.ui.theme.CardSurface),
        contentAlignment = Alignment.Center
    ) {
        UIKitView(
            factory = {
                val provided = iosAdBannerProvider?.invoke(adUnit)
                provided ?: UIView()
            },
            modifier = Modifier.fillMaxWidth().height(if (isModalOpen) 0.dp else 50.dp),
            update = { view ->
                view.hidden = isModalOpen
            },
            onRelease = { view ->
                view.removeFromSuperview()
            }
        )
    }
}
