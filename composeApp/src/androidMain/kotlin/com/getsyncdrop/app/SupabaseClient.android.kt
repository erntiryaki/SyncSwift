package com.getsyncdrop.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual object SupabaseNetwork {
    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val providerString = when (provider) {
                IdentityProvider.Google -> "google"
                IdentityProvider.Apple -> "apple"
                else -> "email"
            }

            Log.d("SupabaseNetwork", "Creating Supabase Auth user for $email ($providerString)...")

            // 1. Create User in auth.users via Admin Users API
            val url = URL("${SyncDropConfig.SUPABASE_URL}/auth/v1/admin/users")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", SyncDropConfig.SUPABASE_SERVICE_ROLE_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${SyncDropConfig.SUPABASE_SERVICE_ROLE_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val cleanEmail = email.lowercase().trim()
            val payload = """
                {
                    "email": "$cleanEmail",
                    "email_confirm": true,
                    "user_metadata": {
                        "first_name": "$firstName",
                        "last_name": "$lastName",
                        "provider": "$providerString"
                    }
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "Supabase Admin Auth User Code: $responseCode, Body: $responseText")

            // 2. Also Upsert Profile if needed
            val profUrl = URL("${SyncDropConfig.SUPABASE_URL}/rest/v1/profiles")
            val profConn = profUrl.openConnection() as HttpURLConnection
            profConn.requestMethod = "POST"
            profConn.setRequestProperty("apikey", SyncDropConfig.SUPABASE_SERVICE_ROLE_KEY)
            profConn.setRequestProperty("Authorization", "Bearer ${SyncDropConfig.SUPABASE_SERVICE_ROLE_KEY}")
            profConn.setRequestProperty("Content-Type", "application/json")
            profConn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            profConn.doOutput = true

            val profPayload = """
                {
                    "email": "$cleanEmail",
                    "first_name": "$firstName",
                    "last_name": "$lastName",
                    "auth_provider": "$providerString"
                }
            """.trimIndent()

            profConn.outputStream.use { os ->
                os.write(profPayload.toByteArray(Charsets.UTF_8))
            }

            val profCode = profConn.responseCode
            Log.d("SupabaseNetwork", "Profiles REST Code: $profCode")

            return@withContext responseCode in 200..299 || responseCode == 422
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "Failed to register user in Supabase: ${e.message}", e)
            return@withContext false
        }
    }
}
