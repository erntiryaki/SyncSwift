package net.syncswift.subscription

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    const val FREE_MAX_DEVICES = 3 // Free plan supports up to 3 registered devices
    const val PRO_MAX_DEVICES = 10 // Pro plan supports up to 10 registered devices
    const val FREE_CLOUD_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB Cloud
    const val PRO_CLOUD_BYTES = 20L * 1024 * 1024 * 1024 // 20 GB Cloud
    const val FREE_MONTHLY_RELAY_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB Monthly Relay
    const val PRO_MONTHLY_RELAY_BYTES = 50L * 1024 * 1024 * 1024 // 50 GB Monthly Relay

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    private val _paywallReason = MutableStateFlow(PaywallReason.UPGRADE_BUTTON)
    val paywallReason: StateFlow<PaywallReason> = _paywallReason.asStateFlow()

    fun hasActiveAccount(): Boolean {
        val loggedIn = SessionManager.isLoggedIn()
        val provider = SessionManager.getLoggedInProvider()
        val email = SessionManager.getLoggedInEmail()
        return loggedIn && provider != net.syncswift.IdentityProvider.Local && !email.isNullOrBlank()
    }

    fun isProActive(): Boolean {
        return _isPro.value || SessionManager.getSavedProStatus()
    }

    fun refreshProState() {
        val cachedPro = SessionManager.getSavedProStatus()
        _isPro.value = cachedPro
    }

    fun init() {
        refreshProState()
        SubscriptionPlatform.init()
    }

    fun setProState(pro: Boolean) {
        SessionManager.saveProStatus(pro)
        _isPro.value = pro
        if (pro) {
            if (SessionManager.getProStartDateMillis() <= 0L) {
                SessionManager.saveProStartDateMillis(net.syncswift.currentTimeMs())
            }
            if (SessionManager.getProRenewalDateMillis() <= 0L) {
                SessionManager.saveProRenewalDateMillis(net.syncswift.currentTimeMs() + 365L * 24 * 60 * 60 * 1000L)
            }
        }
        val email = SessionManager.getLoggedInEmail()
        if (!email.isNullOrBlank()) {
            val start = SessionManager.getProStartDateMillis()
            val renew = SessionManager.getProRenewalDateMillis()
            CoroutineScope(Dispatchers.Default).launch {
                net.syncswift.SupabaseNetwork.updateUserProStatus(email, pro, start, renew)
            }
        }
    }

    fun checkTimedPaywall() {
        if (isProActive() || _isPro.value || SessionManager.getSavedProStatus()) return
        val currentSlot = net.syncswift.getCurrentPaywallSlotKey()
        val lastSlot = SessionManager.getLastShownPaywallSlot()
        if (currentSlot != lastSlot) {
            SessionManager.saveLastShownPaywallSlot(currentSlot)
            triggerPaywall(PaywallReason.UPGRADE_BUTTON)
        }
    }

    data class ProSubscriptionDates(
        val startDateFormatted: String,
        val renewalDateFormatted: String,
        val daysLeft: Int
    )

    fun getProSubscriptionDates(): ProSubscriptionDates {
        var startMs = SessionManager.getProStartDateMillis()
        if (startMs <= 0L) {
            startMs = net.syncswift.currentTimeMs()
            SessionManager.saveProStartDateMillis(startMs)
        }
        var renewalMs = SessionManager.getProRenewalDateMillis()
        if (renewalMs <= 0L) {
            renewalMs = startMs + 365L * 24 * 60 * 60 * 1000L
            SessionManager.saveProRenewalDateMillis(renewalMs)
        }
        val now = net.syncswift.currentTimeMs()
        val diff = (renewalMs - now).coerceAtLeast(0)
        val days = (diff / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(1)

        return ProSubscriptionDates(
            startDateFormatted = net.syncswift.formatEpochToDate(startMs),
            renewalDateFormatted = net.syncswift.formatEpochToDate(renewalMs),
            daysLeft = days
        )
    }

    fun triggerPaywall(reason: PaywallReason) {
        if (isProActive() || _isPro.value || SessionManager.getSavedProStatus()) {
            return
        }
        _paywallReason.value = reason
        _showPaywall.value = true
    }

    fun dismissPaywall() {
        _showPaywall.value = false
    }

    // Restriction checks
    fun canAddDevice(currentConnectedCount: Int): Boolean {
        if (isProActive()) return true
        return currentConnectedCount < FREE_MAX_DEVICES
    }

    fun canTransferFileSize(sizeBytes: Long): Boolean {
        // Section 20: Free & Pro LAN file size is unlimited
        return true
    }

    fun canRemoteTransfer(): Boolean {
        return true
    }

    fun canBackgroundWake(): Boolean {
        return isProActive()
    }

    fun canSyncClipboard(): Boolean {
        return true
    }

    fun shouldShowAds(): Boolean {
        return !isProActive()
    }

    fun purchasePro(activity: Any? = null, isAnnual: Boolean = true, onResult: (Boolean, String?) -> Unit) {
        SubscriptionPlatform.purchasePro(activity, isAnnual, onResult)
    }

    fun restorePurchases(onResult: (Boolean, String?) -> Unit) {
        SubscriptionPlatform.restorePurchases(onResult)
    }
}

expect object SubscriptionPlatform {
    fun init()
    fun purchasePro(activity: Any?, isAnnual: Boolean = true, onResult: (Boolean, String?) -> Unit)
    fun restorePurchases(onResult: (Boolean, String?) -> Unit)
}
