package net.syncswift

import platform.Foundation.NSUserDefaults

actual object SessionManager {
    actual fun saveSession(email: String, provider: IdentityProvider) {
        NSUserDefaults.standardUserDefaults.setBool(true, "is_logged_in")
        NSUserDefaults.standardUserDefaults.setObject(email, "logged_email")
        NSUserDefaults.standardUserDefaults.setObject(provider.name, "logged_provider")
    }

    actual fun saveSession(email: String, provider: IdentityProvider, firstName: String, lastName: String) {
        NSUserDefaults.standardUserDefaults.setBool(true, "is_logged_in")
        NSUserDefaults.standardUserDefaults.setObject(email, "logged_email")
        NSUserDefaults.standardUserDefaults.setObject(provider.name, "logged_provider")
        NSUserDefaults.standardUserDefaults.setObject(firstName, "first_name")
        NSUserDefaults.standardUserDefaults.setObject(lastName, "last_name")
    }

    actual fun saveLocalSession() {
        NSUserDefaults.standardUserDefaults.setBool(true, "is_logged_in")
        NSUserDefaults.standardUserDefaults.setObject("", "logged_email")
        NSUserDefaults.standardUserDefaults.setObject("", "logged_user_id")
        NSUserDefaults.standardUserDefaults.setObject(IdentityProvider.Local.name, "logged_provider")
        NSUserDefaults.standardUserDefaults.setObject("", "first_name")
        NSUserDefaults.standardUserDefaults.setObject("", "last_name")
        NSUserDefaults.standardUserDefaults.setBool(false, "is_pro")
        net.syncswift.subscription.SubscriptionManager.refreshProState()
    }

    actual fun saveLoggedInUserId(userId: String) {
        NSUserDefaults.standardUserDefaults.setObject(userId.trim(), forKey = "logged_user_id")
    }

    actual fun getLoggedInUserId(): String? {
        val uid = NSUserDefaults.standardUserDefaults.stringForKey("logged_user_id")?.trim()
        return if (uid.isNullOrBlank()) null else uid
    }

    actual fun clearSession() {
        NSUserDefaults.standardUserDefaults.setBool(false, "is_logged_in")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("logged_email")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("logged_user_id")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("logged_provider")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("first_name")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("last_name")
        NSUserDefaults.standardUserDefaults.setBool(false, "is_pro")
        NSUserDefaults.standardUserDefaults.setBool(false, "is_pro_member")
        NSUserDefaults.standardUserDefaults.setInteger(0L, "pro_start_date_ms")
        NSUserDefaults.standardUserDefaults.setInteger(0L, "pro_renewal_date_ms")
        net.syncswift.subscription.SubscriptionManager.refreshProState()
    }

    actual fun isLoggedIn(): Boolean {
        return NSUserDefaults.standardUserDefaults.boolForKey("is_logged_in")
    }

    actual fun getLoggedInEmail(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("logged_email")
    }

    actual fun getLoggedInProvider(): IdentityProvider? {
        val name = NSUserDefaults.standardUserDefaults.stringForKey("logged_provider") ?: return null
        return try { IdentityProvider.valueOf(name) } catch (e: Exception) { null }
    }

    actual fun getUserFirstName(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey("first_name") ?: ""
    }

    actual fun getUserLastName(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey("last_name") ?: ""
    }

    actual fun updateUserProfile(firstName: String, lastName: String) {
        NSUserDefaults.standardUserDefaults.setObject(firstName, "first_name")
        NSUserDefaults.standardUserDefaults.setObject(lastName, "last_name")
    }

    actual fun saveActiveOtp(email: String, code: String, timestampMs: Long) {
        val key = email.lowercase().trim()
        NSUserDefaults.standardUserDefaults.setObject(code, "otp_code_$key")
        NSUserDefaults.standardUserDefaults.setDouble(timestampMs.toDouble(), "otp_time_$key")
    }

    actual fun getActiveOtp(email: String, nowMs: Long): OtpData? {
        val key = email.lowercase().trim()
        val code = NSUserDefaults.standardUserDefaults.stringForKey("otp_code_$key") ?: return null
        val time = NSUserDefaults.standardUserDefaults.doubleForKey("otp_time_$key").toLong()

        val otp = OtpData(code, time)
        return if (otp.isExpired(nowMs)) null else otp
    }

    actual fun saveLanguage(languageCode: String) {
        NSUserDefaults.standardUserDefaults.setObject(languageCode, "app_language")
    }

    actual fun getSavedLanguage(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("app_language")
    }

    actual fun saveThemeMode(themeMode: String) {
        NSUserDefaults.standardUserDefaults.setObject(themeMode, "app_theme_mode")
    }

    actual fun getSavedThemeMode(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("app_theme_mode")
    }

    actual fun saveConnectedDeviceKeys(keys: Set<String>) {
        NSUserDefaults.standardUserDefaults.setObject(keys.joinToString(";;;"), "connected_devices_keys")
    }

    actual fun getSavedConnectedDeviceKeys(): Set<String> {
        val str = NSUserDefaults.standardUserDefaults.stringForKey("connected_devices_keys")
        if (!str.isNullOrBlank()) {
            return str.split(";;;").filter { it.isNotBlank() }.toSet()
        }
        val list = NSUserDefaults.standardUserDefaults.stringArrayForKey("connected_devices_keys")
        return list?.mapNotNull { it as? String }?.toSet() ?: emptySet()
    }

    actual fun saveRemoteConnectedDeviceKeys(keys: Set<String>) {
        NSUserDefaults.standardUserDefaults.setObject(keys.joinToString(";;;"), "remote_connected_devices_keys")
    }

    actual fun getSavedRemoteConnectedDeviceKeys(): Set<String> {
        val str = NSUserDefaults.standardUserDefaults.stringForKey("remote_connected_devices_keys")
        if (!str.isNullOrBlank()) {
            return str.split(";;;").filter { it.isNotBlank() }.toSet()
        }
        val list = NSUserDefaults.standardUserDefaults.stringArrayForKey("remote_connected_devices_keys")
        return list?.mapNotNull { it as? String }?.toSet() ?: emptySet()
    }

    actual fun saveManualDevicesJson(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, "manual_devices_json")
    }

    actual fun getSavedManualDevicesJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("manual_devices_json")
    }

    actual fun saveTransfersJson(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, "transfers_history_json")
    }

    actual fun getSavedTransfersJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("transfers_history_json")
    }

    actual fun saveNotificationsJson(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, "notifications_history_json")
    }

    actual fun getSavedNotificationsJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("notifications_history_json")
    }

    actual fun saveClipboardHistoryJson(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, "clipboard_history_json")
    }

    actual fun getSavedClipboardHistoryJson(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("clipboard_history_json")
    }

    actual fun saveProStatus(isPro: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(isPro, "is_pro_member")
    }

    actual fun getSavedProStatus(): Boolean {
        return NSUserDefaults.standardUserDefaults.boolForKey("is_pro_member")
    }

    actual fun saveProStartDateMillis(millis: Long) {
        NSUserDefaults.standardUserDefaults.setInteger(millis, "pro_start_date_ms")
    }

    actual fun getProStartDateMillis(): Long {
        return NSUserDefaults.standardUserDefaults.integerForKey("pro_start_date_ms")
    }

    actual fun saveProRenewalDateMillis(millis: Long) {
        NSUserDefaults.standardUserDefaults.setInteger(millis, "pro_renewal_date_ms")
    }

    actual fun getProRenewalDateMillis(): Long {
        return NSUserDefaults.standardUserDefaults.integerForKey("pro_renewal_date_ms")
    }

    actual fun saveOnboardingSeen(seen: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(seen, "onboarding_seen")
    }

    actual fun isOnboardingSeen(): Boolean {
        return NSUserDefaults.standardUserDefaults.boolForKey("onboarding_seen")
    }

    actual fun saveLastShownPaywallSlot(slot: String) {
        NSUserDefaults.standardUserDefaults.setObject(slot, forKey = "last_shown_paywall_slot")
    }

    actual fun getLastShownPaywallSlot(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("last_shown_paywall_slot")
    }

    actual fun getPlatformDeviceId(): String {
        return platform.UIKit.UIDevice.currentDevice.identifierForVendor?.UUIDString ?: platform.Foundation.NSUUID().UUIDString
    }

    actual fun getMyDeviceName(): String {
        return platform.UIKit.UIDevice.currentDevice.name
    }

    actual fun getMyPlatformName(): String = "iOS"

    actual fun getMyPushToken(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey("syncswift_push_token")
            ?.ifBlank { null }
            ?: NSUserDefaults.standardUserDefaults.stringForKey("apns_or_fcm_token")
            ?: ""
    }
}
