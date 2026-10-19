package net.syncswift.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import net.syncswift.PlatformConfig
import net.syncswift.ui.theme.CardSurface

object AndroidAdBannerHolder {
    private var cachedAdView: AdView? = null

    fun getBanner(context: Context, adUnitId: String): AdView {
        val existing = cachedAdView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val displayMetrics = context.resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = if (density > 0) (widthPixels / density).toInt() else 320

        val adSize = try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
        } catch (_: Exception) {
            AdSize.BANNER
        }

        val newAdView = AdView(context).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            loadAd(AdRequest.Builder().build())
        }
        cachedAdView = newAdView
        return newAdView
    }
}

@Composable
actual fun AdBannerView(modifier: Modifier) {
    val adUnit = PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/6300978111"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(CardSurface),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                val banner = AndroidAdBannerHolder.getBanner(context, adUnit)
                (banner.parent as? ViewGroup)?.removeView(banner)
                banner
            },
            onRelease = { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
        )
    }
}
