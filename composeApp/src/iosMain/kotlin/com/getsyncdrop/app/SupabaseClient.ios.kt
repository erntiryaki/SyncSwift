package com.getsyncdrop.app

actual object SupabaseNetwork {
    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?
    ): Boolean {
        return true
    }
}
