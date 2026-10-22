package net.syncswift

/** SyncSwift Production Configuration loaded securely from PlatformConfig (.env.local) */
object SyncSwiftConfig {
    val SUPABASE_URL: String get() = PlatformConfig.supabaseUrl
    val SUPABASE_ANON_KEY: String get() = PlatformConfig.supabaseAnonKey
    val GOOGLE_WEB_CLIENT_ID: String get() = PlatformConfig.googleWebClientId
}

/** A provider is permanently attached to an account after its first successful sign-in. */
enum class IdentityProvider { EmailPassword, Google, Apple, Local }
enum class AuthStep { Onboarding, Email, Password, Otp, Profile, ResetPassword, Home }

data class AuthSession(
    val email: String,
    val provider: IdentityProvider,
    val firstName: String = "",
    val lastName: String = ""
)

interface EmailOtpGateway {
    suspend fun sendCode(email: String, code: String): Boolean
}

/** Amazon SES Gateway with fallback */
class AmazonSesOtpGateway : EmailOtpGateway {
    override suspend fun sendCode(email: String, code: String): Boolean {
        return AmazonSesMailer.sendOtpEmail(email, code)
    }
}

/** Strict Cross-Provider Policy: Email/Password vs Google cross-entry prevention */
class AuthPolicy {
    private val registeredAccounts = mutableMapOf<String, IdentityProvider>()

    fun getRegisteredProvider(email: String): IdentityProvider? {
        return registeredAccounts[email.lowercase().trim()]
    }

    fun registerAccount(email: String, provider: IdentityProvider) {
        registeredAccounts[email.lowercase().trim()] = provider
    }

    fun allows(email: String, attempted: IdentityProvider): Boolean {
        val existing = getRegisteredProvider(email) ?: return true
        return existing == attempted
    }
}

/** Password rule: Minimum 8 characters, at least 1 uppercase letter, 1 lowercase letter, and 1 special character. */
fun isValidPassword(password: String): Boolean {
    return password.length >= 8 &&
        password.any { it.isUpperCase() } &&
        password.any { it.isLowerCase() } &&
        password.any { !it.isLetterOrDigit() }
}

/** Basic multiplatform email validation */
fun isValidEmail(email: String): Boolean {
    val clean = email.trim()
    val atIndex = clean.indexOf('@')
    val lastDotIndex = clean.lastIndexOf('.')
    return atIndex > 0 && lastDotIndex > atIndex + 1 && lastDotIndex < clean.length - 1
}

