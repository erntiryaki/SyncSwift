package net.syncswift.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager

enum class PaywallReason(val title: String, val description: String) {
    DEVICE_LIMIT(
        "Device Limit Reached",
        "Up to 3 devices can be paired in the free plan. Upgrade to SyncSwift Pro for unlimited devices."
    ),
    REMOTE_TRANSFER(
        "Remote Transfer Feature",
        "Transferring across different networks and mobile data is exclusive to SyncSwift Pro."
    ),
    BACKGROUND_WAKE(
        "Background Delivery",
        "Delivering files automatically even when the app is closed is exclusive to SyncSwift Pro."
    ),
    FILE_SIZE_LIMIT(
        "5 GB Transfer Cap",
        "Free plan supports up to 5 GB per transfer. Upgrade to SyncSwift Pro for unlimited file size."
    ),
    CLIPBOARD_SYNC(
        "Live Clipboard Sync",
        "Instant real-time clipboard sync across devices is exclusive to SyncSwift Pro."
    ),
    UPGRADE_BUTTON(
        "Upgrade to SyncSwift Pro",
        "Unlock unlimited file sizes, remote transfers, background delivery, and an ad-free experience."
    )
}

fun PaywallReason.getTitle(copy: net.syncswift.Copy): String = when (this) {
    PaywallReason.DEVICE_LIMIT -> copy.paywallDeviceLimitTitle
    PaywallReason.REMOTE_TRANSFER -> copy.paywallRemoteTransferTitle
    PaywallReason.BACKGROUND_WAKE -> copy.paywallBackgroundWakeTitle
    PaywallReason.FILE_SIZE_LIMIT -> copy.paywallFileSizeLimitTitle
    PaywallReason.CLIPBOARD_SYNC -> copy.paywallClipboardSyncTitle
    PaywallReason.UPGRADE_BUTTON -> copy.paywallUpgradeTitle
}

fun PaywallReason.getDescription(copy: net.syncswift.Copy): String = when (this) {
    PaywallReason.DEVICE_LIMIT -> copy.paywallDeviceLimitDesc
    PaywallReason.REMOTE_TRANSFER -> copy.paywallRemoteTransferDesc
    PaywallReason.BACKGROUND_WAKE -> copy.paywallBackgroundWakeDesc
    PaywallReason.FILE_SIZE_LIMIT -> copy.paywallFileSizeLimitDesc
    PaywallReason.CLIPBOARD_SYNC -> copy.paywallClipboardSyncDesc
    PaywallReason.UPGRADE_BUTTON -> copy.paywallUpgradeDesc
}

object SubscriptionManager {
    const val FREE_MAX_DEVICES = 3
    const val FREE_MAX_FILE_BYTES = 5L * 1024 * 1024 * 1024 // 5 GB

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    private val _paywallReason = MutableStateFlow(PaywallReason.UPGRADE_BUTTON)
    val paywallReason: StateFlow<PaywallReason> = _paywallReason.asStateFlow()

    fun init() {
        // Load initial pro state from local session / persistence
        val cachedPro = SessionManager.getSavedProStatus()
        _isPro.value = cachedPro
        SubscriptionPlatform.init()
    }

    fun setProState(pro: Boolean) {
        _isPro.value = pro
        SessionManager.saveProStatus(pro)
    }

    fun triggerPaywall(reason: PaywallReason) {
        _paywallReason.value = reason
        _showPaywall.value = true
    }

    fun dismissPaywall() {
        _showPaywall.value = false
    }

    // Restriction checks
    fun canAddDevice(currentConnectedCount: Int): Boolean {
        if (_isPro.value) return true
        return currentConnectedCount < FREE_MAX_DEVICES
    }

    fun canTransferFileSize(sizeBytes: Long): Boolean {
        if (_isPro.value) return true
        return sizeBytes <= FREE_MAX_FILE_BYTES
    }

    fun canRemoteTransfer(): Boolean {
        return _isPro.value
    }

    fun canBackgroundWake(): Boolean {
        return _isPro.value
    }

    fun canSyncClipboard(): Boolean {
        return _isPro.value
    }

    fun shouldShowAds(): Boolean {
        return !_isPro.value
    }

    fun purchasePro(activity: Any?, onResult: (Boolean, String?) -> Unit) {
        SubscriptionPlatform.purchasePro(activity, onResult)
    }

    fun restorePurchases(onResult: (Boolean, String?) -> Unit) {
        SubscriptionPlatform.restorePurchases(onResult)
    }
}

expect object SubscriptionPlatform {
    fun init()
    fun purchasePro(activity: Any?, onResult: (Boolean, String?) -> Unit)
    fun restorePurchases(onResult: (Boolean, String?) -> Unit)
}
