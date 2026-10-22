package net.syncswift

actual object PlatformConfig {
    actual val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    actual val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
    actual val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
    actual val awsSesRegion: String get() = "eu-central-1"
    actual val awsSesSenderEmail: String get() = "noreply@syncswift.net"
    actual val revenueCatApiKey: String get() = BuildConfig.REVENUECAT_API_KEY
    actual val admobBannerId: String get() = BuildConfig.ADMOB_BANNER_ID
}
