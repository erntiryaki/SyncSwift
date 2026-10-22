package net.syncswift

expect object PlatformConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
    val googleWebClientId: String
    val awsSesRegion: String
    val awsSesSenderEmail: String
    val revenueCatApiKey: String
    val admobBannerId: String
}
