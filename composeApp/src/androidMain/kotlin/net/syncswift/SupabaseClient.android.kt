package net.syncswift

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual object SupabaseNetwork {
    private val effectiveKey: String get() = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }

    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val providerString = when (provider) {
                IdentityProvider.Google -> "google"
                IdentityProvider.Apple -> "apple"
                else -> "email"
            }

            Log.d("SupabaseNetwork", "Creating Supabase Auth user for $email ($providerString)...")

            // 1. Create User in auth.users via Admin Users API
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val cleanEmail = email.lowercase().trim()
            val hasNames = firstName.isNotBlank() || lastName.isNotBlank()
            val userMetaJson = if (hasNames) {
                """
                    "user_metadata": {
                        "first_name": "$firstName",
                        "last_name": "$lastName",
                        "full_name": "${"$firstName $lastName".trim()}",
                        "provider": "$providerString"
                    }
                """.trimIndent()
            } else {
                """
                    "user_metadata": {
                        "provider": "$providerString"
                    }
                """.trimIndent()
            }

            val payload = """
                {
                    "email": "$cleanEmail",
                    "email_confirm": true,
                    ${if (password.isNotBlank()) "\"password\": \"$password\"," else ""}
                    $userMetaJson
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "Supabase Admin Auth User Code: $responseCode, Body: $responseText")

            // 2. Update existing profile or insert new profile with first_name and last_name
            try {
                // Try PATCH first for existing profile
                val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail")
                val patchConn = patchUrl.openConnection() as HttpURLConnection
                patchConn.requestMethod = "PATCH"
                patchConn.connectTimeout = 8000
                patchConn.readTimeout = 8000
                patchConn.setRequestProperty("apikey", effectiveKey)
                patchConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                patchConn.setRequestProperty("Content-Type", "application/json")
                patchConn.setRequestProperty("Prefer", "return=representation")
                patchConn.doOutput = true

                val profPayload = if (hasNames) {
                    """
                        {
                            "email": "$cleanEmail",
                            "first_name": "$firstName",
                            "last_name": "$lastName",
                            "auth_provider": "$providerString"
                        }
                    """.trimIndent()
                } else {
                    """
                        {
                            "email": "$cleanEmail",
                            "auth_provider": "$providerString"
                        }
                    """.trimIndent()
                }

                patchConn.outputStream.use { os ->
                    os.write(profPayload.toByteArray(Charsets.UTF_8))
                }

                val patchCode = patchConn.responseCode
                val patchBody = if (patchCode in 200..299) patchConn.inputStream.bufferedReader().readText() else ""
                Log.d("SupabaseNetwork", "Profiles PATCH Code: $patchCode, Body: $patchBody")

                // If PATCH didn't update any row (empty array "[]"), do POST
                if (patchBody == "[]" || patchBody.isBlank()) {
                    val postUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles")
                    val postConn = postUrl.openConnection() as HttpURLConnection
                    postConn.requestMethod = "POST"
                    postConn.connectTimeout = 8000
                    postConn.readTimeout = 8000
                    postConn.setRequestProperty("apikey", effectiveKey)
                    postConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                    postConn.setRequestProperty("Content-Type", "application/json")
                    postConn.doOutput = true
                    postConn.outputStream.use { os ->
                        os.write(profPayload.toByteArray(Charsets.UTF_8))
                    }
                    val postCode = postConn.responseCode
                    Log.d("SupabaseNetwork", "Profiles POST Code: $postCode")
                }
            } catch (ex: Exception) {
                Log.w("SupabaseNetwork", "Profile upsert error: ${ex.message}")
            }

            return@withContext responseCode in 200..299 || responseCode == 422
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "Failed to register user in Supabase: ${e.message}", e)
            return@withContext false
        }
    }

    actual suspend fun checkUserExists(email: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val exists = body.contains("\"id\"") && body != "[]"
                Log.d("SupabaseNetwork", "checkUserExists for $cleanEmail: $exists (body: $body)")
                return@withContext exists
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "checkUserExists failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun getUserProvider(email: String): IdentityProvider? = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext null
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=id,auth_provider")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.d("SupabaseNetwork", "getUserProvider for $cleanEmail: $body")
                if (body == "[]" || !body.contains("\"id\"")) return@withContext null
                return@withContext when {
                    body.contains("\"auth_provider\":\"google\"") -> IdentityProvider.Google
                    body.contains("\"auth_provider\":\"apple\"") -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "getUserProvider failed: ${e.message}")
            return@withContext null
        }
    }

    actual suspend fun signInWithPassword(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || SyncSwiftConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/token?grant_type=password")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", SyncSwiftConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val payload = """{"email":"$cleanEmail","password":"$password"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299)
                conn.inputStream.bufferedReader().readText()
            else
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "signInWithPassword code=$responseCode body=$body")
            return@withContext responseCode in 200..299 && body.contains("access_token")
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "signInWithPassword failed: ${e.message}")
            return@withContext false
        }
    }

    private fun getUserIdFromProfile(cleanEmail: String): String? {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                return regex.find(body)?.groupValues?.getOrNull(1)
            }
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "getUserIdFromProfile error: ${e.message}")
        }
        return null
    }

    actual suspend fun fetchUserProfile(email: String): UserProfileData? = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext null
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=email,first_name,last_name,auth_provider")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body == "[]" || body.isBlank()) return@withContext null
                fun extractField(json: String, key: String): String {
                    val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                }
                var fn = extractField(body, "first_name")
                var ln = extractField(body, "last_name")
                if (fn.isBlank() && ln.isBlank()) {
                    val full = extractField(body, "full_name").ifBlank { extractField(body, "name") }
                    if (full.isNotBlank()) {
                        fn = full.split(" ").firstOrNull() ?: ""
                        ln = full.split(" ").drop(1).joinToString(" ")
                    }
                }

                // If still blank, check Supabase auth.users user_metadata via admin
                if (fn.isBlank() && ln.isBlank()) {
                    val userId = getUserIdFromProfile(cleanEmail)
                    if (!userId.isNullOrBlank()) {
                        try {
                            val userUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$userId")
                            val userConn = userUrl.openConnection() as HttpURLConnection
                            userConn.requestMethod = "GET"
                            userConn.connectTimeout = 8000
                            userConn.readTimeout = 8000
                            userConn.setRequestProperty("apikey", effectiveKey)
                            userConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                            userConn.setRequestProperty("Accept", "application/json")
                            if (userConn.responseCode in 200..299) {
                                val userBody = userConn.inputStream.bufferedReader().readText()
                                fn = extractField(userBody, "first_name")
                                ln = extractField(userBody, "last_name")
                                if (fn.isBlank() && ln.isBlank()) {
                                    val full = extractField(userBody, "full_name").ifBlank { extractField(userBody, "name") }
                                    if (full.isNotBlank()) {
                                        fn = full.split(" ").firstOrNull() ?: ""
                                        ln = full.split(" ").drop(1).joinToString(" ")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("SupabaseNetwork", "auth.users fallback check: ${e.message}")
                        }
                    }
                }

                val provStr = extractField(body, "auth_provider")
                val provider = when (provStr.lowercase()) {
                    "google" -> IdentityProvider.Google
                    "apple" -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
                return@withContext UserProfileData(cleanEmail, fn, ln, provider)
            }
            return@withContext null

        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "fetchUserProfile failed: ${e.message}")
            return@withContext null
        }
    }

    actual suspend fun updateUserPassword(email: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val userId = getUserIdFromProfile(cleanEmail)
            if (userId.isNullOrBlank()) {
                Log.e("SupabaseNetwork", "updateUserPassword: could not find user ID for $cleanEmail")
                return@withContext false
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$userId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val payload = """{"password":"$newPassword"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "updateUserPassword response=$responseCode body=$body")
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "updateUserPassword failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun updateUserProfileData(
        oldEmail: String,
        newEmail: String,
        firstName: String,
        lastName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanOld = oldEmail.lowercase().trim()
            val cleanNew = newEmail.lowercase().trim()
            val cleanFirst = firstName.trim()
            val cleanLast = lastName.trim()
            val fullName = "$cleanFirst $cleanLast".trim()

            var userId = getUserIdFromProfile(cleanOld) ?: getUserIdFromProfile(cleanNew)

            // 1. Update public.profiles table
            val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanOld")
            val patchConn = patchUrl.openConnection() as HttpURLConnection
            patchConn.requestMethod = "PATCH"
            patchConn.connectTimeout = 8000
            patchConn.readTimeout = 8000
            patchConn.setRequestProperty("apikey", effectiveKey)
            patchConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.setRequestProperty("Prefer", "return=representation")
            patchConn.doOutput = true

            val profPayload = """{"first_name":"$cleanFirst","last_name":"$cleanLast","email":"$cleanNew"}"""
            patchConn.outputStream.use { it.write(profPayload.toByteArray(Charsets.UTF_8)) }
            val patchCode = patchConn.responseCode
            val patchBody = if (patchCode in 200..299) patchConn.inputStream.bufferedReader().readText() else ""
            Log.d("SupabaseNetwork", "updateUserProfileData profiles by email code=$patchCode body=$patchBody")

            // If updating by email didn't match any row and userId is known, try updating by id
            if ((patchBody == "[]" || patchBody.isBlank()) && !userId.isNullOrBlank()) {
                try {
                    val patchIdUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$userId")
                    val patchIdConn = patchIdUrl.openConnection() as HttpURLConnection
                    patchIdConn.requestMethod = "PATCH"
                    patchIdConn.connectTimeout = 8000
                    patchIdConn.readTimeout = 8000
                    patchIdConn.setRequestProperty("apikey", effectiveKey)
                    patchIdConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                    patchIdConn.setRequestProperty("Content-Type", "application/json")
                    patchIdConn.setRequestProperty("Prefer", "return=representation")
                    patchIdConn.doOutput = true
                    patchIdConn.outputStream.use { it.write(profPayload.toByteArray(Charsets.UTF_8)) }
                    Log.d("SupabaseNetwork", "updateUserProfileData profiles by id code=${patchIdConn.responseCode}")
                } catch (pe: Exception) {
                    Log.w("SupabaseNetwork", "updateUserProfileData fallback by id error: ${pe.message}")
                }
            }

            // 2. ALWAYS update auth.users with user_metadata in Supabase Auth
            if (userId.isNullOrBlank()) {
                userId = getUserIdFromProfile(cleanNew) ?: getUserIdFromProfile(cleanOld)
            }
            if (!userId.isNullOrBlank()) {
                try {
                    val authUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$userId")
                    val authConn = authUrl.openConnection() as HttpURLConnection
                    authConn.requestMethod = "PUT"
                    authConn.connectTimeout = 10000
                    authConn.readTimeout = 10000
                    authConn.setRequestProperty("apikey", effectiveKey)
                    authConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                    authConn.setRequestProperty("Content-Type", "application/json")
                    authConn.doOutput = true

                    val authPayload = if (cleanNew != cleanOld) {
                        """
                        {
                            "email": "$cleanNew",
                            "email_confirm": true,
                            "user_metadata": {
                                "first_name": "$cleanFirst",
                                "last_name": "$cleanLast",
                                "full_name": "$fullName"
                            }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                            "user_metadata": {
                                "first_name": "$cleanFirst",
                                "last_name": "$cleanLast",
                                "full_name": "$fullName"
                            }
                        }
                        """.trimIndent()
                    }
                    authConn.outputStream.use { it.write(authPayload.toByteArray(Charsets.UTF_8)) }
                    val authCode = authConn.responseCode
                    Log.d("SupabaseNetwork", "updateUserProfileData auth.users code=$authCode")
                } catch (ae: Exception) {
                    Log.e("SupabaseNetwork", "updateUserProfileData auth.users error: ${ae.message}")
                }
            }

            return@withContext patchCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "updateUserProfileData failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun deleteUserAccount(email: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val userId = getUserIdFromProfile(cleanEmail)
            var authDeleted = false
            if (!userId.isNullOrBlank()) {
                val url = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("apikey", effectiveKey)
                conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                val responseCode = conn.responseCode
                authDeleted = responseCode in 200..299
                Log.d("SupabaseNetwork", "deleteUserAccount auth.users code=$responseCode")
            }

            // Also delete from public.profiles table
            val profUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail")
            val profConn = profUrl.openConnection() as HttpURLConnection
            profConn.requestMethod = "DELETE"
            profConn.connectTimeout = 8000
            profConn.readTimeout = 8000
            profConn.setRequestProperty("apikey", effectiveKey)
            profConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            val profCode = profConn.responseCode
            Log.d("SupabaseNetwork", "deleteUserAccount profiles table code=$profCode")

            return@withContext authDeleted || (profCode in 200..299)
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "deleteUserAccount failed: ${e.message}")
            return@withContext false
        }
    }
}

