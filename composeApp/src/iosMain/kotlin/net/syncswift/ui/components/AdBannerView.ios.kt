package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.SyncArrowIcon
import net.syncswift.subscription.PaywallReason
import androidx.compose.runtime.*
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSClassFromString
import platform.UIKit.UIView
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdBannerView(modifier: Modifier) {
    var hasGadClass by remember { mutableStateOf<Boolean?>(null) }
    var adMobError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            hasGadClass = NSClassFromString("GADBannerView") != null
        } catch (_: Throwable) {
            hasGadClass = false
        }
    }

    if (hasGadClass == true && !adMobError) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            UIKitView(
                factory = {
                    try {
                        val cls = NSClassFromString("GADBannerView")
                        if (cls != null) {
                            val alloc = platform.Foundation.NSSelectorFromString("alloc")
                            val initSel = platform.Foundation.NSSelectorFromString("init")
                            val rawObj = (cls as? platform.darwin.NSObject)?.performSelector(alloc)
                            val bannerView = (rawObj as? platform.darwin.NSObject)?.performSelector(initSel) as? UIView

                            if (bannerView != null) {
                                val adUnit = net.syncswift.PlatformConfig.admobBannerId.ifBlank {
                                    "ca-app-pub-3940256099942544/2934735716"
                                }
                                bannerView.setValue(adUnit, forKey = "adUnitID")

                                val window = UIApplication.sharedApplication.keyWindow
                                    ?: UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
                                val rootVC = window?.rootViewController
                                if (rootVC != null) {
                                    bannerView.setValue(rootVC, forKey = "rootViewController")
                                }

                                val reqCls = NSClassFromString("GADRequest")
                                val reqObj = (reqCls as? platform.darwin.NSObject)?.performSelector(platform.Foundation.NSSelectorFromString("request"))
                                    ?: (reqCls as? platform.darwin.NSObject)?.performSelector(alloc)?.let { (it as? platform.darwin.NSObject)?.performSelector(initSel) }

                                val loadRequestSel = platform.Foundation.NSSelectorFromString("loadRequest:")
                                (bannerView as? platform.darwin.NSObject)?.performSelector(loadRequestSel, withObject = reqObj)

                                bannerView
                            } else {
                                UIView()
                            }
                        } else {
                            UIView()
                        }
                    } catch (e: Throwable) {
                        adMobError = true
                        UIView()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }
    } else {
        CustomFallbackBanner(modifier)
    }
}

@Composable
private fun CustomFallbackBanner(modifier: Modifier) {
    val copy = net.syncswift.currentAppCopy()
    val electricColor = Color(0xFF2BC5D3)
    val skyColor = Color(0xFF38BDF8)
    val midnightColor = Color(0xFF0B101B)
    val navyColor = Color(0xFF1E293B)
    val cloudColor = Color(0xFFF8FAFC)
    val mistColor = Color(0xFFA6B4C9)
    val cardSurfaceColor = Color(0xFF131B28)
    val inputBorderColor = Color(0xFF28364D)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardSurfaceColor)
                .border(1.dp, inputBorderColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickable {
                    net.syncswift.subscription.SubscriptionManager.triggerPaywall(net.syncswift.subscription.PaywallReason.UPGRADE_BUTTON)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon pill
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                electricColor.copy(alpha = 0.22f),
                                skyColor.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                SyncArrowIcon(
                    modifier = Modifier.size(20.dp),
                    color = electricColor
                )
            }

            Spacer(Modifier.width(12.dp))

            // Text column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = copy.adTitle,
                        color = cloudColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(navyColor.copy(alpha = 0.6f))
                            .border(0.8.dp, inputBorderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = copy.adBadge,
                            color = mistColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = copy.adSubtitle,
                    color = mistColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action button pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(electricColor)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = copy.adAction,
                    color = midnightColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
