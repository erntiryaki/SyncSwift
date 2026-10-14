package com.getsyncdrop.app

actual object PlatformConfig {
    actual val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    actual val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
    actual val supabaseServiceRoleKey: String get() = BuildConfig.SUPABASE_SERVICE_ROLE_KEY
    actual val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
    actual val brevoApiKey: String get() = BuildConfig.BREVO_API_KEY
}
