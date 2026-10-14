package com.getsyncdrop.app

expect object PlatformConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
    val supabaseServiceRoleKey: String
    val googleWebClientId: String
    val brevoApiKey: String
}
