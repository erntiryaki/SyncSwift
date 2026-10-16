package net.syncswift

actual object SupabaseNetwork {
    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?,
        password: String
    ): Boolean {
        return true
    }

    actual suspend fun checkUserExists(email: String): Boolean {
        return false
    }

    actual suspend fun getUserProvider(email: String): IdentityProvider? {
        return null
    }

    actual suspend fun signInWithPassword(email: String, password: String): Boolean {
        return false
    }

    actual suspend fun fetchUserProfile(email: String): UserProfileData? {
        return null
    }

    actual suspend fun updateUserPassword(email: String, newPassword: String): Boolean {
        return true
    }

    actual suspend fun updateUserProfileData(
        oldEmail: String,
        newEmail: String,
        firstName: String,
        lastName: String
    ): Boolean {
        return true
    }

    actual suspend fun deleteUserAccount(email: String): Boolean {
        return true
    }
}

