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
        NSUserDefaults.standardUserDefaults.setObject(IdentityProvider.Local.name, "logged_provider")
        NSUserDefaults.standardUserDefaults.setObject("", "first_name")
        NSUserDefaults.standardUserDefaults.setObject("", "last_name")
    }

    actual fun clearSession() {
        NSUserDefaults.standardUserDefaults.setBool(false, "is_logged_in")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("logged_email")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("logged_provider")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("first_name")
        NSUserDefaults.standardUserDefaults.removeObjectForKey("last_name")
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
}
