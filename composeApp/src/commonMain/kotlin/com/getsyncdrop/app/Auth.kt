package com.getsyncdrop.app

/** SyncDrop Production Configuration loaded securely from PlatformConfig (.env.local) */
object SyncDropConfig {
    val SUPABASE_URL: String get() = PlatformConfig.supabaseUrl
    val SUPABASE_ANON_KEY: String get() = PlatformConfig.supabaseAnonKey
    val SUPABASE_SERVICE_ROLE_KEY: String get() = PlatformConfig.supabaseServiceRoleKey
    val GOOGLE_WEB_CLIENT_ID: String get() = PlatformConfig.googleWebClientId
    val BREVO_API_KEY: String get() = PlatformConfig.brevoApiKey
}

/** A provider is permanently attached to an account after its first successful sign-in. */
enum class IdentityProvider { EmailPassword, Google, Apple }
enum class AuthStep { Onboarding, Email, Password, Otp, Profile, Home }

data class AuthSession(
    val email: String,
    val provider: IdentityProvider,
    val firstName: String = "",
    val lastName: String = ""
)

interface EmailOtpGateway {
    suspend fun sendCode(email: String, code: String): Boolean
}

expect object BrevoMailer {
    suspend fun sendOtpEmail(email: String, code: String): Boolean
}

/** Brevo Transactional Email Gateway */
class BrevoOtpGateway : EmailOtpGateway {
    override suspend fun sendCode(email: String, code: String): Boolean {
        return BrevoMailer.sendOtpEmail(email, code)
    }
}

/** Amazon SES Gateway for future cutover */
class AmazonSesOtpGateway : EmailOtpGateway {
    override suspend fun sendCode(email: String, code: String): Boolean {
        return true
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
