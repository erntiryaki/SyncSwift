package net.syncswift.subscription

actual object SubscriptionPlatform {
    actual fun init() {}
    actual fun purchasePro(activity: Any?, onResult: (Boolean, String?) -> Unit) {
        onResult(false, "iOS RevenueCat implementation pending")
    }
    actual fun restorePurchases(onResult: (Boolean, String?) -> Unit) {
        onResult(false, "iOS RevenueCat implementation pending")
    }
}
