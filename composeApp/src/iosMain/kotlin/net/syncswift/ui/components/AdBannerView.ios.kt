package net.syncswift.ui.components

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
    val adUnit = net.syncswift.PlatformConfig.admobBannerId.ifBlank {
        "ca-app-pub-3940256099942544/2934735716"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        UIKitView(
            factory = {
                val provided = iosAdBannerProvider?.invoke(adUnit)
                if (provided != null) {
                    provided
                } else {
                    // Fallback to reflection if provider not yet wired
                    try {
                        val cls = NSClassFromString("GADBannerView")
                        if (cls != null) {
                            val alloc = platform.Foundation.NSSelectorFromString("alloc")
                            val initSel = platform.Foundation.NSSelectorFromString("init")
                            val rawObj = (cls as? platform.darwin.NSObject)?.performSelector(alloc)
                            val bannerView = (rawObj as? platform.darwin.NSObject)?.performSelector(initSel) as? UIView

                            if (bannerView != null) {
                                val bannerObj = bannerView as? platform.darwin.NSObject
                                val setAdUnitSel = platform.Foundation.NSSelectorFromString("setAdUnitID:")
                                bannerObj?.performSelector(setAdUnitSel, withObject = adUnit as? platform.darwin.NSObject)

                                val window = UIApplication.sharedApplication.keyWindow
                                    ?: UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
                                val rootVC = window?.rootViewController
                                if (rootVC != null) {
                                    val setRootVcSel = platform.Foundation.NSSelectorFromString("setRootViewController:")
                                    bannerObj?.performSelector(setRootVcSel, withObject = rootVC as? platform.darwin.NSObject)
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
                    } catch (_: Throwable) {
                        UIView()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}
