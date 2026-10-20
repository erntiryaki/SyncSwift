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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha

@Composable
actual fun AdBannerView(modifier: Modifier) {
    val isModalOpen by AdBannerManager.isModalOpen.collectAsState()

    val adUnit = PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/6300978111"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isModalOpen) 0.dp else 50.dp)
            .alpha(if (isModalOpen) 0f else 1f)
            .background(if (isModalOpen) androidx.compose.ui.graphics.Color.Transparent else CardSurface),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isModalOpen) 0.dp else 50.dp),
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
            update = { adView ->
                val targetVis = if (isModalOpen) android.view.View.GONE else android.view.View.VISIBLE
                if (adView.visibility != targetVis) {
                    adView.visibility = targetVis
                    if (isModalOpen) {
                        try { adView.pause() } catch (_: Exception) {}
                    } else {
                        try { adView.resume() } catch (_: Exception) {}
                    }
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
