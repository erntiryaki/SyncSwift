package com.getsyncdrop.app

actual object PlatformConfig {
    actual val supabaseUrl: String get() = ""
    actual val supabaseAnonKey: String get() = ""
    actual val supabaseServiceRoleKey: String get() = ""
    actual val googleWebClientId: String get() = ""
    actual val brevoApiKey: String get() = ""
}
