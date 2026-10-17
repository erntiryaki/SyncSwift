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
    actual val supabaseAnonKey: String get() = getEnvOrBundle("SUPABASE_ANON_KEY", "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc5MjI0NTA5MiwiZXhwIjo0OTQ3NDEzNDYwLCJyb2xlIjoiYW5vbiJ9.W4EgdJGRf2-pBcLOp8Ofh4KYgcD8j2EvFhJu3jfz1Vs")
    actual val supabaseServiceRoleKey: String get() = getEnvOrBundle("SUPABASE_SERVICE_ROLE_KEY", "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc5MjI0NTA5MiwiZXhwIjo0OTQ3NDEzNDYwLCJyb2xlIjoic2VydmljZV9yb2xlIn0.4VNgkF89h0znar_-2p4rKaqeS4vfq3SywWOxDVluhDY")
    actual val googleWebClientId: String get() = getEnvOrBundle("GOOGLE_WEB_CLIENT_ID", "911753763379-cbm16jd1nhg1rjtrjvfuvakn34racuen.apps.googleusercontent.com")
    actual val brevoApiKey: String get() = getEnvOrBundle("BREVO_API_KEY", "")
    actual val brevoSenderEmail: String get() = getEnvOrBundle("BREVO_SENDER_EMAIL", "noreply@syncswift.net")
    actual val awsSesAccessKeyId: String get() = getEnvOrBundle("AWS_SES_ACCESS_KEY_ID", "")
    actual val awsSesSecretAccessKey: String get() = getEnvOrBundle("AWS_SES_SECRET_ACCESS_KEY", "")
    actual val awsSesRegion: String get() = getEnvOrBundle("AWS_SES_REGION", "eu-central-1")
    actual val awsSesSenderEmail: String get() = getEnvOrBundle("AWS_SES_SENDER_EMAIL", "noreply@syncswift.net")
    actual val revenueCatApiKey: String get() = getEnvOrBundle("REVENUECAT_API_KEY", "")
    actual val admobBannerId: String get() = getEnvOrBundle("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/2934735716")
}
