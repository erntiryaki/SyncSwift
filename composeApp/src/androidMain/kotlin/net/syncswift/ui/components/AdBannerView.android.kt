package net.syncswift.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import net.syncswift.PlatformConfig
import net.syncswift.ui.theme.CardSurface

@Composable
actual fun AdBannerView(modifier: Modifier) {
    val adUnit = PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/6300978111"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(CardSurface),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                val displayMetrics = context.resources.displayMetrics
                val widthPixels = displayMetrics.widthPixels
                val density = displayMetrics.density
                val adWidth = if (density > 0) (widthPixels / density).toInt() else 320

                val adSize = try {
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
                } catch (_: Exception) {
                    AdSize.BANNER
                }

                AdView(context).apply {
                    setAdSize(adSize)
                    this.adUnitId = adUnit
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { adView ->
                try {
                    adView.destroy()
                } catch (_: Exception) {}
            }
        )
    }
}
