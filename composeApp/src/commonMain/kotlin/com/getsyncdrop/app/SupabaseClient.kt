package com.getsyncdrop.app

expect object SupabaseNetwork {
    suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String? = null
    ): Boolean
}
