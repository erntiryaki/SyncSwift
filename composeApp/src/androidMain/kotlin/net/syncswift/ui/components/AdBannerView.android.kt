package net.syncswift.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import net.syncswift.PlatformConfig
import net.syncswift.ui.theme.CardSurface
import net.syncswift.ui.theme.InputBorder

@Composable
actual fun AdBannerView(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardSurface)
    ) {
        // Divider on top to seamlessly stick above the bottom navbar
        HorizontalDivider(
            thickness = 1.dp,
            color = InputBorder.copy(alpha = 0.25f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    val adView = AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        val adUnit = PlatformConfig.admobBannerId.ifBlank {
                            "ca-app-pub-3940256099942544/6300978111"
                        }
                        adUnitId = adUnit
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        loadAd(AdRequest.Builder().build())
                    }
                    adView
                }
            )
        }
    }
}
