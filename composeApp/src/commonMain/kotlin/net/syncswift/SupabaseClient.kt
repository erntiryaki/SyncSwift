package net.syncswift

data class UserProfileData(
    val email: String,
    val firstName: String,
    val lastName: String,
    val provider: IdentityProvider
)

expect object SupabaseNetwork {
    suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String? = null,
        password: String = ""
    ): Boolean

    suspend fun checkUserExists(email: String): Boolean

    /** Returns the registered identity provider for the email, or null if not found. */
    suspend fun getUserProvider(email: String): IdentityProvider?

    /** Returns true only when Supabase accepts the email+password combination. */
    suspend fun signInWithPassword(email: String, password: String): Boolean

    /** Fetches the first_name, last_name, and auth_provider from profiles table */
    suspend fun fetchUserProfile(email: String): UserProfileData?

    /** Updates user password via Supabase Admin API */
    suspend fun updateUserPassword(email: String, newPassword: String): Boolean

    /** Updates user profile (name, and optionally new email) in profiles and auth.users */
    suspend fun updateUserProfileData(
        oldEmail: String,
        newEmail: String,
        firstName: String,
        lastName: String
    ): Boolean

    /** Deletes user from auth.users and profiles table */
    suspend fun deleteUserAccount(email: String): Boolean
}

