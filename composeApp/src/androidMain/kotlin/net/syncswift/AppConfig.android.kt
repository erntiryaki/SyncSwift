package net.syncswift

actual object PlatformConfig {
    actual val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    actual val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
    actual val supabaseServiceRoleKey: String get() = BuildConfig.SUPABASE_SERVICE_ROLE_KEY
    actual val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
    actual val brevoApiKey: String get() = BuildConfig.BREVO_API_KEY
    actual val brevoSenderEmail: String get() = BuildConfig.BREVO_SENDER_EMAIL
    actual val awsSesAccessKeyId: String get() = BuildConfig.AWS_SES_ACCESS_KEY_ID
    actual val awsSesSecretAccessKey: String get() = BuildConfig.AWS_SES_SECRET_ACCESS_KEY
    actual val awsSesRegion: String get() = BuildConfig.AWS_SES_REGION
    actual val awsSesSenderEmail: String get() = BuildConfig.AWS_SES_SENDER_EMAIL
    actual val revenueCatApiKey: String get() = BuildConfig.REVENUECAT_API_KEY
    actual val admobBannerId: String get() = BuildConfig.ADMOB_BANNER_ID
}
