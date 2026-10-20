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

private var iosAdBannerProvider: ((String) -> UIView)? = null

fun setIosAdBannerProvider(provider: (String) -> UIView) {
    iosAdBannerProvider = provider
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBannerView(modifier: Modifier) {
    val isModalOpen by AdBannerManager.isModalOpen.collectAsState()
    if (isModalOpen) return

    val adUnit = net.syncswift.PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/2934735716"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(net.syncswift.ui.theme.CardSurface),
        contentAlignment = Alignment.Center
    ) {
        UIKitView(
            factory = {
                val provided = iosAdBannerProvider?.invoke(adUnit)
                provided ?: UIView()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            onRelease = { view ->
                view.removeFromSuperview()
            }
        )
    }
}
