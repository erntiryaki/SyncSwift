package net.syncswift.subscription

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import net.syncswift.PlatformConfig
import net.syncswift.SessionManager

actual object SubscriptionPlatform {
    private const val TAG = "SyncSwiftPurchases"
    private const val ENTITLEMENT_ID = "pro"

    actual fun init() {
        val ctx = SessionManager.getAppContext() ?: return

        // 1. Initialize Google Mobile Ads (AdMob)
        try {
            MobileAds.initialize(ctx) { status ->
                Log.d(TAG, "MobileAds initialized: $status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds: ${e.message}")
        }

        // 2. Initialize RevenueCat Purchases
        val apiKey = PlatformConfig.revenueCatApiKey
        if (apiKey.isBlank() || apiKey == "goog_placeholder_api_key") {
            Log.w(TAG, "RevenueCat API key is empty or placeholder. Running in test/mock mode.")
            return
        }

        try {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(ctx, apiKey).build())

            // Check current customer entitlement
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val isProActive = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    SubscriptionManager.setProState(isProActive)
                    Log.d(TAG, "Customer info received. Pro active: $isProActive")
                }

                override fun onError(error: PurchasesError) {
                    Log.e(TAG, "Error fetching customer info: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RevenueCat: ${e.message}")
        }
    }

    actual fun purchasePro(activity: Any?, onResult: (Boolean, String?) -> Unit) {
        val act = activity as? Activity
        val apiKey = PlatformConfig.revenueCatApiKey

        if (apiKey.isBlank() || apiKey == "goog_placeholder_api_key") {
            // Test Mode: instantly unlock Pro for developer testing
            SubscriptionManager.setProState(true)
            onResult(true, null)
            return
        }

        if (act == null) {
            onResult(false, "Activity context is required for purchase")
            return
        }

        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    val currentOffering = offerings.current
                    val packageToPurchase = currentOffering?.availablePackages?.firstOrNull()

                    if (packageToPurchase == null) {
                        onResult(false, "Aktif Pro abonelik paketi bulunamadı")
                        return
                    }

                    val params = PurchaseParams.Builder(act, packageToPurchase).build()
                    Purchases.sharedInstance.purchase(params, object : com.revenuecat.purchases.interfaces.PurchaseCallback {
                        override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                            val isProActive = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                            SubscriptionManager.setProState(isProActive)
                            onResult(isProActive, null)
                        }

                        override fun onError(error: PurchasesError, userCancelled: Boolean) {
                            if (userCancelled) {
                                onResult(false, "Satın alma iptal edildi")
                            } else {
                                onResult(false, error.message)
                            }
                        }
                    })
                }

                override fun onError(error: PurchasesError) {
                    onResult(false, "Paketler alınamadı: ${error.message}")
                }
            })
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    actual fun restorePurchases(onResult: (Boolean, String?) -> Unit) {
        val apiKey = PlatformConfig.revenueCatApiKey

        if (apiKey.isBlank() || apiKey == "goog_placeholder_api_key") {
            val isPro = SessionManager.getSavedProStatus()
            onResult(isPro, if (isPro) null else "Aktif abonelik bulunamadı")
            return
        }

        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val isProActive = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                    SubscriptionManager.setProState(isProActive)
                    onResult(isProActive, if (isProActive) null else "Aktif abonelik bulunamadı")
                }

                override fun onError(error: PurchasesError) {
                    onResult(false, error.message)
                }
            })
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }
}
