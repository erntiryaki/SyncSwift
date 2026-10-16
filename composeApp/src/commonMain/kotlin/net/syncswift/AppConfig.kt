package net.syncswift

expect object PlatformConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
    val supabaseServiceRoleKey: String
    val googleWebClientId: String
    val brevoApiKey: String
    val brevoSenderEmail: String
    val awsSesAccessKeyId: String
    val awsSesSecretAccessKey: String
    val awsSesRegion: String
    val awsSesSenderEmail: String
    val revenueCatApiKey: String
    val admobBannerId: String
}
