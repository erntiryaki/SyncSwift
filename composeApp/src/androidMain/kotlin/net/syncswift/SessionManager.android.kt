package net.syncswift

import android.content.Context
import android.content.SharedPreferences

actual object SessionManager {
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var currentActivityRef: java.lang.ref.WeakReference<android.app.Activity>? = null

    private fun getPrefs(): SharedPreferences? {
        if (prefs != null) return prefs
        val ctx = appContext
        if (ctx != null) {
            prefs = ctx.getSharedPreferences("syncswift_session_prefs", Context.MODE_PRIVATE)
        }
        return prefs
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (context is android.app.Activity) {
            currentActivityRef = java.lang.ref.WeakReference(context)
        }
        if (prefs == null) {
            prefs = context.getSharedPreferences("syncswift_session_prefs", Context.MODE_PRIVATE)
        }
    }

    fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivityRef = if (activity != null) java.lang.ref.WeakReference(activity) else null
    }

    fun getCurrentActivity(): android.app.Activity? = currentActivityRef?.get()

    fun getAppContext(): Context? = appContext

    actual fun saveSession(email: String, provider: IdentityProvider) {
        getPrefs()?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", email.lowercase().trim())
            ?.putString("logged_provider", provider.name)
            ?.apply()
    }

    actual fun saveSession(email: String, provider: IdentityProvider, firstName: String, lastName: String) {
        getPrefs()?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", email.lowercase().trim())
            ?.putString("logged_provider", provider.name)
            ?.putString("first_name", firstName)
            ?.putString("last_name", lastName)
            ?.apply()
    }

    actual fun saveLocalSession() {
        getPrefs()?.edit()
            ?.putBoolean("is_logged_in", true)
            ?.putString("logged_email", "")
            ?.putString("logged_provider", IdentityProvider.Local.name)
            ?.putString("first_name", "")
            ?.putString("last_name", "")
            ?.putBoolean("is_pro", false)
            ?.apply()
        net.syncswift.subscription.SubscriptionManager.refreshProState()
    }

    actual fun clearSession() {
        getPrefs()?.edit()
            ?.putBoolean("is_logged_in", false)
            ?.remove("logged_email")
            ?.remove("logged_provider")
            ?.remove("first_name")
            ?.remove("last_name")
            ?.putBoolean("is_pro", false)
            ?.apply()
        net.syncswift.subscription.SubscriptionManager.refreshProState()
    }

    actual fun isLoggedIn(): Boolean {
        return getPrefs()?.getBoolean("is_logged_in", false) ?: false
    }

    actual fun getLoggedInEmail(): String? {
        return getPrefs()?.getString("logged_email", null)
    }

    actual fun getLoggedInProvider(): IdentityProvider? {
        val name = getPrefs()?.getString("logged_provider", null) ?: return null
        return try { IdentityProvider.valueOf(name) } catch (e: Exception) { null }
    }

    actual fun getUserFirstName(): String {
        return getPrefs()?.getString("first_name", "") ?: ""
    }

    actual fun getUserLastName(): String {
        return getPrefs()?.getString("last_name", "") ?: ""
    }

    actual fun updateUserProfile(firstName: String, lastName: String) {
        getPrefs()?.edit()
            ?.putString("first_name", firstName)
            ?.putString("last_name", lastName)
            ?.apply()
    }

    actual fun saveActiveOtp(email: String, code: String, timestampMs: Long) {
        val key = email.lowercase().trim()
        getPrefs()?.edit()
            ?.putString("otp_code_$key", code)
            ?.putLong("otp_time_$key", timestampMs)
            ?.apply()
    }

    actual fun getActiveOtp(email: String, nowMs: Long): OtpData? {
        val key = email.lowercase().trim()
        val code = getPrefs()?.getString("otp_code_$key", null) ?: return null
        val time = getPrefs()?.getLong("otp_time_$key", 0L) ?: 0L

        val otp = OtpData(code, time)
        return if (otp.isExpired(nowMs)) {
            null
        } else {
            otp
        }
    }

    actual fun saveLanguage(languageCode: String) {
        getPrefs()?.edit()?.putString("app_language", languageCode)?.apply()
    }

    actual fun getSavedLanguage(): String? {
        return getPrefs()?.getString("app_language", null)
    }

    actual fun saveThemeMode(themeMode: String) {
        getPrefs()?.edit()?.putString("app_theme_mode", themeMode)?.apply()
    }

    actual fun getSavedThemeMode(): String? {
        return getPrefs()?.getString("app_theme_mode", null)
    }

    actual fun saveConnectedDeviceKeys(keys: Set<String>) {
        getPrefs()?.edit()?.remove("connected_devices_keys")?.putStringSet("connected_devices_keys", HashSet(keys))?.apply()
    }

    actual fun getSavedConnectedDeviceKeys(): Set<String> {
        return getPrefs()?.getStringSet("connected_devices_keys", emptySet())?.toSet() ?: emptySet()
    }

    actual fun saveRemoteConnectedDeviceKeys(keys: Set<String>) {
        getPrefs()?.edit()?.remove("remote_connected_devices_keys")?.putStringSet("remote_connected_devices_keys", HashSet(keys))?.apply()
    }

    actual fun getSavedRemoteConnectedDeviceKeys(): Set<String> {
        return getPrefs()?.getStringSet("remote_connected_devices_keys", emptySet())?.toSet() ?: emptySet()
    }

    actual fun saveManualDevicesJson(json: String) {
        getPrefs()?.edit()?.putString("manual_devices_json", json)?.apply()
    }

    actual fun getSavedManualDevicesJson(): String? {
        return getPrefs()?.getString("manual_devices_json", null)
    }

    actual fun saveTransfersJson(json: String) {
        getPrefs()?.edit()?.putString("transfers_history_json", json)?.apply()
    }

    actual fun getSavedTransfersJson(): String? {
        return getPrefs()?.getString("transfers_history_json", null)
    }

    actual fun saveNotificationsJson(json: String) {
        getPrefs()?.edit()?.putString("notifications_history_json", json)?.apply()
    }

    actual fun getSavedNotificationsJson(): String? {
        return getPrefs()?.getString("notifications_history_json", null)
    }

    actual fun saveClipboardHistoryJson(json: String) {
        getPrefs()?.edit()?.putString("clipboard_history_json", json)?.apply()
    }

    actual fun getSavedClipboardHistoryJson(): String? {
        return getPrefs()?.getString("clipboard_history_json", null)
    }

    actual fun saveProStatus(isPro: Boolean) {
        getPrefs()?.edit()?.putBoolean("is_pro_member", isPro)?.apply()
    }

    actual fun getSavedProStatus(): Boolean {
        return getPrefs()?.getBoolean("is_pro_member", false) ?: false
    }

    actual fun saveProStartDateMillis(millis: Long) {
        getPrefs()?.edit()?.putLong("pro_start_date_ms", millis)?.apply()
    }

    actual fun getProStartDateMillis(): Long {
        return getPrefs()?.getLong("pro_start_date_ms", 0L) ?: 0L
    }

    actual fun saveProRenewalDateMillis(millis: Long) {
        getPrefs()?.edit()?.putLong("pro_renewal_date_ms", millis)?.apply()
    }

    actual fun getProRenewalDateMillis(): Long {
        return getPrefs()?.getLong("pro_renewal_date_ms", 0L) ?: 0L
    }

    actual fun saveOnboardingSeen(seen: Boolean) {
        getPrefs()?.edit()?.putBoolean("onboarding_seen", seen)?.apply()
    }

    actual fun isOnboardingSeen(): Boolean {
        return getPrefs()?.getBoolean("onboarding_seen", false) ?: false
    }

    actual fun saveLastShownPaywallSlot(slot: String) {
        getPrefs()?.edit()?.putString("last_shown_paywall_slot", slot)?.apply()
    }

    actual fun getLastShownPaywallSlot(): String? {
        return getPrefs()?.getString("last_shown_paywall_slot", null)
    }

    actual fun getPlatformDeviceId(): String {
        return java.util.UUID.nameUUIDFromBytes("${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.ID}".toByteArray()).toString()
    }

    actual fun getMyDeviceName(): String {
        val model = android.os.Build.MODEL ?: "Android"
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    actual fun getMyPlatformName(): String = "Android"

    actual fun getMyPushToken(): String {
        val ctx = appContext
        return if (ctx != null) net.syncswift.fcm.SyncSwiftFirebaseMessagingService.getSavedToken(ctx) ?: "" else ""
    }
}
