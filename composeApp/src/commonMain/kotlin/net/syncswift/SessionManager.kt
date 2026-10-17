package net.syncswift

data class OtpData(
    val code: String,
    val createdAtMs: Long
) {
    fun isExpired(nowMs: Long): Boolean {
        return (nowMs - createdAtMs) > 5 * 60 * 1000
    }

    fun remainingSeconds(nowMs: Long): Int {
        val remainingMs = (5 * 60 * 1000) - (nowMs - createdAtMs)
        return (remainingMs / 1000).coerceAtLeast(0).toInt()
    }
}

expect object SessionManager {
    fun saveSession(email: String, provider: IdentityProvider)
    fun saveSession(email: String, provider: IdentityProvider, firstName: String, lastName: String)
    fun saveLocalSession()
    fun clearSession()
    fun isLoggedIn(): Boolean
    fun getLoggedInEmail(): String?
    fun getLoggedInProvider(): IdentityProvider?
    fun getUserFirstName(): String
    fun getUserLastName(): String
    fun updateUserProfile(firstName: String, lastName: String)

    fun saveActiveOtp(email: String, code: String, timestampMs: Long)
    fun getActiveOtp(email: String, nowMs: Long): OtpData?

    fun saveLanguage(languageCode: String)
    fun getSavedLanguage(): String?
    fun saveThemeMode(themeMode: String)
    fun getSavedThemeMode(): String?

    fun saveConnectedDeviceKeys(keys: Set<String>)
    fun getSavedConnectedDeviceKeys(): Set<String>
    fun saveRemoteConnectedDeviceKeys(keys: Set<String>)
    fun getSavedRemoteConnectedDeviceKeys(): Set<String>
    fun saveManualDevicesJson(json: String)
    fun getSavedManualDevicesJson(): String?
    fun saveTransfersJson(json: String)
    fun getSavedTransfersJson(): String?
    fun saveNotificationsJson(json: String)
    fun getSavedNotificationsJson(): String?

    fun saveProStatus(isPro: Boolean)
    fun getSavedProStatus(): Boolean
    fun saveProStartDateMillis(millis: Long)
    fun getProStartDateMillis(): Long
    fun saveProRenewalDateMillis(millis: Long)
    fun getProRenewalDateMillis(): Long

    fun saveOnboardingSeen(seen: Boolean)
    fun isOnboardingSeen(): Boolean

    fun saveLastShownPaywallSlot(slot: String)
    fun getLastShownPaywallSlot(): String?
}

