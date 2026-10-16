package net.syncswift

import android.content.Context
import android.content.SharedPreferences

actual object SessionManager {
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.getSharedPreferences("syncswift_session_prefs", Context.MODE_PRIVATE)
        }
    }

    fun getAppContext(): Context? = appContext

    actual fun saveSession(email: String, provider: IdentityProvider) {
        prefs?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", email.lowercase().trim())
            ?.putString("logged_provider", provider.name)
            ?.apply()
    }

    actual fun saveSession(email: String, provider: IdentityProvider, firstName: String, lastName: String) {
        prefs?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", email.lowercase().trim())
            ?.putString("logged_provider", provider.name)
            ?.putString("first_name", firstName)
            ?.putString("last_name", lastName)
            ?.apply()
    }

    actual fun saveLocalSession() {
        prefs?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", "")
            ?.putString("logged_provider", IdentityProvider.Local.name)
            ?.putString("first_name", "")
            ?.putString("last_name", "")
            ?.apply()
    }

    actual fun clearSession() {
        prefs?.edit()
            ?.putBoolean("is_logged_in", false)
            ?.remove("logged_email")
            ?.remove("logged_provider")
            ?.remove("first_name")
            ?.remove("last_name")
            ?.apply()
    }

    actual fun isLoggedIn(): Boolean {
        return prefs?.getBoolean("is_logged_in", false) ?: false
    }

    actual fun getLoggedInEmail(): String? {
        return prefs?.getString("logged_email", null)
    }

    actual fun getLoggedInProvider(): IdentityProvider? {
        val name = prefs?.getString("logged_provider", null) ?: return null
        return try { IdentityProvider.valueOf(name) } catch (e: Exception) { null }
    }

    actual fun getUserFirstName(): String {
        return prefs?.getString("first_name", "") ?: ""
    }

    actual fun getUserLastName(): String {
        return prefs?.getString("last_name", "") ?: ""
    }

    actual fun updateUserProfile(firstName: String, lastName: String) {
        prefs?.edit()
            ?.putString("first_name", firstName)
            ?.putString("last_name", lastName)
            ?.apply()
    }

    actual fun saveActiveOtp(email: String, code: String, timestampMs: Long) {
        val key = email.lowercase().trim()
        prefs?.edit()
            ?.putString("otp_code_$key", code)
            ?.putLong("otp_time_$key", timestampMs)
            ?.apply()
    }

    actual fun getActiveOtp(email: String, nowMs: Long): OtpData? {
        val key = email.lowercase().trim()
        val code = prefs?.getString("otp_code_$key", null) ?: return null
        val time = prefs?.getLong("otp_time_$key", 0L) ?: 0L

        val otp = OtpData(code, time)
        return if (otp.isExpired(nowMs)) {
            null
        } else {
            otp
        }
    }

    actual fun saveLanguage(languageCode: String) {
        prefs?.edit()?.putString("app_language", languageCode)?.apply()
    }

    actual fun getSavedLanguage(): String? {
        return prefs?.getString("app_language", null)
    }

    actual fun saveThemeMode(themeMode: String) {
        prefs?.edit()?.putString("app_theme_mode", themeMode)?.apply()
    }

    actual fun getSavedThemeMode(): String? {
        return prefs?.getString("app_theme_mode", null)
    }

    actual fun saveConnectedDeviceKeys(keys: Set<String>) {
        prefs?.edit()?.putStringSet("connected_devices_keys", keys)?.apply()
    }

    actual fun getSavedConnectedDeviceKeys(): Set<String> {
        return prefs?.getStringSet("connected_devices_keys", emptySet())?.toSet() ?: emptySet()
    }

    actual fun saveRemoteConnectedDeviceKeys(keys: Set<String>) {
        prefs?.edit()?.putStringSet("remote_connected_devices_keys", keys)?.apply()
    }

    actual fun getSavedRemoteConnectedDeviceKeys(): Set<String> {
        return prefs?.getStringSet("remote_connected_devices_keys", emptySet())?.toSet() ?: emptySet()
    }

    actual fun saveManualDevicesJson(json: String) {
        prefs?.edit()?.putString("manual_devices_json", json)?.apply()
    }

    actual fun getSavedManualDevicesJson(): String? {
        return prefs?.getString("manual_devices_json", null)
    }

    actual fun saveTransfersJson(json: String) {
        prefs?.edit()?.putString("transfers_history_json", json)?.apply()
    }

    actual fun getSavedTransfersJson(): String? {
        return prefs?.getString("transfers_history_json", null)
    }

    actual fun saveProStatus(isPro: Boolean) {
        prefs?.edit()?.putBoolean("is_pro_member", isPro)?.apply()
    }

    actual fun getSavedProStatus(): Boolean {
        return prefs?.getBoolean("is_pro_member", false) ?: false
    }
}
