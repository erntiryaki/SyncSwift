package net.syncswift.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.SessionManager

enum class PaywallReason(val title: String, val description: String) {
    DEVICE_LIMIT(
        "Cihaz Sınırına Ulaşıldı",
        "Temel planda aynı anda en fazla 3 cihaz eşleştirilebilir. Sınırsız cihaz bağlamak için SyncSwift Pro'ya geçin."
    ),
    REMOTE_TRANSFER(
        "Uzaktan Transfer Özelliği",
        "Farklı ağlar ve mobil veri üzerinden uzaktan transfer SyncSwift Pro üyelerine özeldir."
    ),
    BACKGROUND_WAKE(
        "Arka Planda Uyandırma",
        "Alıcı uygulama kapalı olsa bile arka planda bildirimle uyandırma ve otomatik transfer SyncSwift Pro özelliğidir."
    ),
    FILE_SIZE_LIMIT(
        "5 GB Transfer Sınırı",
        "Temel planda aktarım başı maksimum 5 GB dosya gönderilebilir. Sınırsız boyut için SyncSwift Pro'ya geçin."
    ),
    CLIPBOARD_SYNC(
        "Canlı Pano Senkronizasyonu",
        "Cihazlar arası anlık metin ve pano senkronizasyonu SyncSwift Pro üyelerine özeldir."
    ),
    UPGRADE_BUTTON(
        "SyncSwift Pro'ya Yükseltin",
        "Sınırsız aktarım boyutu, uzaktan transfer, arka planda teslimat ve reklamsız deneyimin kilidini açın."
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
