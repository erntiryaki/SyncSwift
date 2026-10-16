package net.syncswift

actual object PlatformConfig {
    actual val supabaseUrl: String get() = "https://api.syncswift.net"
    actual val supabaseAnonKey: String get() = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc5MTczOTg2MCwiZXhwIjo0OTQ3NDEzNDYwLCJyb2xlIjoiYW5vbiJ9.6nHKuAleaHOw1EEtvzManKuQCp8CD2VbrIzsmGTK1-w"
    actual val supabaseServiceRoleKey: String get() = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc5MTczOTg2MCwiZXhwIjo0OTQ3NDEzNDYwLCJyb2xlIjoic2VydmljZV9yb2xlIn0.XXrNIQtvsSQcy8xcqTmZSmuytN_6fJBZWVqkrJUgQqA"
    actual val googleWebClientId: String get() = "911753763379-cbm16jd1nhg1rjtrjvfuvakn34racuen.apps.googleusercontent.com"
    actual val brevoApiKey: String get() = ""
    actual val brevoSenderEmail: String get() = "noreply@syncswift.net"
    actual val awsSesAccessKeyId: String get() = "***REMOVED***"
    actual val awsSesSecretAccessKey: String get() = "***REMOVED***"
    actual val awsSesRegion: String get() = "eu-central-1"
    actual val awsSesSenderEmail: String get() = "noreply@syncswift.net"
    actual val revenueCatApiKey: String get() = "appl_QkmHgAuGhbLOJWUKBqsyluIiFxT"
    actual val admobBannerId: String get() = "ca-app-pub-3940256099942544/6300978111"
}
