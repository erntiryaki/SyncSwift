package net.syncswift

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

actual object PlatformConfig {
    private fun getEnvOrBundle(key: String, default: String = ""): String {
        val envVal = NSProcessInfo.processInfo.environment[key] as? String
        if (!envVal.isNullOrBlank()) return envVal
        val infoVal = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
        if (!infoVal.isNullOrBlank()) return infoVal
        return default
    }

    actual val supabaseUrl: String get() = getEnvOrBundle("SUPABASE_URL", "https://api.syncswift.net")
    actual val supabaseAnonKey: String get() = getEnvOrBundle("SUPABASE_ANON_KEY", "")
    actual val googleWebClientId: String get() = getEnvOrBundle("GOOGLE_WEB_CLIENT_ID", "911753763379-cbm16jd1nhg1rjtrjvfuvakn34racuen.apps.googleusercontent.com")
    actual val awsSesRegion: String get() = getEnvOrBundle("AWS_SES_REGION", "eu-central-1")
    actual val awsSesSenderEmail: String get() = getEnvOrBundle("AWS_SES_SENDER_EMAIL", "noreply@syncswift.net")
    actual val revenueCatApiKey: String get() = getEnvOrBundle("REVENUECAT_API_KEY", "")
    actual val admobBannerId: String get() = getEnvOrBundle("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/2934735716")
}
