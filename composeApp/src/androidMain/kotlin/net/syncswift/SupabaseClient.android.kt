package net.syncswift

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.connection.ConnectionManager

actual object SupabaseNetwork {
    private val effectiveKey: String get() = SyncSwiftConfig.SUPABASE_ANON_KEY

    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val supabaseUrl = SyncSwiftConfig.SUPABASE_URL
        val anonKey = effectiveKey
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext false
        try {
            val providerString = when (provider) {
                IdentityProvider.Google -> "google"
                IdentityProvider.Apple -> "apple"
                else -> "email"
            }
            val cleanEmail = email.lowercase().trim()
            Log.d("SupabaseNetwork", "Registering user in Supabase for $cleanEmail ($providerString)...")

            // 1. Supabase standard auth signup using anon key
            val url = URL("$supabaseUrl/auth/v1/signup")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val signupPayload = org.json.JSONObject().apply {
                put("email", cleanEmail)
                put("password", password.ifBlank { "SyncSwift_${System.currentTimeMillis()}!" })
                val meta = org.json.JSONObject().apply {
                    put("first_name", firstName)
                    put("last_name", lastName)
                    put("provider", providerString)
                }
                put("data", meta)
            }

            conn.outputStream.use { os ->
                os.write(signupPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            Log.d("SupabaseNetwork", "signup response: code=$responseCode body=$responseText")

            var userId: String? = null
            if (responseCode in 200..299) {
                val idRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                userId = idRegex.find(responseText)?.groupValues?.getOrNull(1)
            }
            if (userId.isNullOrBlank()) {
                userId = getUserIdFromProfile(cleanEmail)
            }
            if (!userId.isNullOrBlank()) {
                SessionManager.saveLoggedInUserId(userId)
            }

            // 2. Upsert profile table
            try {
                val profPayload = org.json.JSONObject().apply {
                    if (!userId.isNullOrBlank()) put("id", userId)
                    put("email", cleanEmail)
                    if (firstName.isNotBlank()) put("first_name", firstName)
                    if (lastName.isNotBlank()) put("last_name", lastName)
                    put("auth_provider", providerString)
                }

                val postUrl = URL("$supabaseUrl/rest/v1/profiles")
                val postConn = postUrl.openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.connectTimeout = 8000
                postConn.readTimeout = 8000
                postConn.setRequestProperty("apikey", anonKey)
                postConn.setRequestProperty("Authorization", "Bearer $anonKey")
                postConn.setRequestProperty("Content-Type", "application/json")
                postConn.setRequestProperty("Prefer", "resolution=merge-duplicates")
                postConn.doOutput = true
                postConn.outputStream.use { os ->
                    os.write(profPayload.toString().toByteArray(Charsets.UTF_8))
                }
                val postCode = postConn.responseCode
                Log.d("SupabaseNetwork", "Profiles POST code: $postCode")
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
        val supabaseUrl = SyncSwiftConfig.SUPABASE_URL
        val anonKey = effectiveKey
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("$supabaseUrl/rest/v1/profiles?email=eq.$cleanEmail&select=id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText().trim()
                val exists = body.contains("\"id\"") && body != "[]"
                Log.d("SupabaseNetwork", "checkUserExists for $cleanEmail: $exists")
                return@withContext exists
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "checkUserExists failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun getUserProvider(email: String): IdentityProvider? = withContext(Dispatchers.IO) {
        val supabaseUrl = SyncSwiftConfig.SUPABASE_URL
        val anonKey = effectiveKey
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext null
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("$supabaseUrl/rest/v1/profiles?email=eq.$cleanEmail&select=auth_provider")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText().trim().lowercase()
                Log.d("SupabaseNetwork", "getUserProvider for $cleanEmail: $body")
                return@withContext when {
                    body.contains("google") -> IdentityProvider.Google
                    body.contains("apple") -> IdentityProvider.Apple
                    body.contains("email") -> IdentityProvider.EmailPassword
                    body.isNotBlank() && body != "[]" -> IdentityProvider.EmailPassword
                    else -> null
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
            val isSuccess = responseCode in 200..299 && body.contains("access_token")
            if (isSuccess) {
                val idRegex = "\"user\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val id = idRegex.find(body)?.groupValues?.getOrNull(1)
                    ?: "\"id\"\\s*:\\s*\"([a-f0-9\\-]{36})\"".toRegex().find(body)?.groupValues?.getOrNull(1)
                if (!id.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(id)
                }
                val isPro = body.contains("\"is_pro\":true") || body.contains("\"is_pro\": true")
                if (isPro) {
                    net.syncswift.subscription.SubscriptionManager.setProState(true)
                }
            }
            return@withContext isSuccess
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "signInWithPassword failed: ${e.message}")
            return@withContext false
        }
    }

    private fun getUserIdFromProfile(cleanEmail: String): String? {
        val cachedId = SessionManager.getLoggedInUserId()
        if (!cachedId.isNullOrBlank()) return cachedId
        if (cleanEmail.isBlank()) return null

        // 1. Check public.profiles table
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val match = regex.find(body)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(match)
                    return match
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "getUserIdFromProfile error: ${e.message}")
        }

        // 2. Fallback: Query Supabase Auth Admin Users API
        try {
            val adminUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users?per_page=1000")
            val adminConn = adminUrl.openConnection() as HttpURLConnection
            adminConn.requestMethod = "GET"
            adminConn.connectTimeout = 8000
            adminConn.readTimeout = 8000
            adminConn.setRequestProperty("apikey", effectiveKey)
            adminConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            adminConn.setRequestProperty("Accept", "application/json")
            if (adminConn.responseCode in 200..299) {
                val adminBody = adminConn.inputStream.bufferedReader().readText()
                val usersArr = org.json.JSONObject(adminBody).optJSONArray("users")
                if (usersArr != null) {
                    for (i in 0 until usersArr.length()) {
                        val u = usersArr.getJSONObject(i)
                        if (u.optString("email").equals(cleanEmail, ignoreCase = true)) {
                            val uid = u.optString("id").ifBlank { null }
                            if (uid != null) {
                                SessionManager.saveLoggedInUserId(uid)
                                return uid
                            }
                        }
                    }
                }
            }
        } catch (ae: Exception) {
            Log.w("SupabaseNetwork", "getUserIdFromProfile admin users fallback: ${ae.message}")
        }
        return null
    }

    actual suspend fun fetchUserProfile(email: String): UserProfileData? = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext null
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=*")
            var conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
            conn.setRequestProperty("Accept", "application/json")
            var code = conn.responseCode
            var body = if (code in 200..299) conn.inputStream.bufferedReader().readText() else ""
            
            // If the query failed, fall back to base fields with is_pro included
            if (code !in 200..299) {
                val fallbackUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail&select=email,first_name,last_name,auth_provider,is_pro")
                conn = fallbackUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("apikey", effectiveKey)
                conn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                conn.setRequestProperty("Accept", "application/json")
                code = conn.responseCode
                body = if (code in 200..299) conn.inputStream.bufferedReader().readText() else ""
            }

            // Fallback by userId if known and email search returned empty
            val savedUid = SessionManager.getLoggedInUserId()
            if ((body.isBlank() || body == "[]") && !savedUid.isNullOrBlank()) {
                try {
                    val uidUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$savedUid&select=*")
                    val uidConn = uidUrl.openConnection() as HttpURLConnection
                    uidConn.requestMethod = "GET"
                    uidConn.connectTimeout = 8000
                    uidConn.readTimeout = 8000
                    uidConn.setRequestProperty("apikey", effectiveKey)
                    uidConn.setRequestProperty("Authorization", "Bearer ${effectiveKey}")
                    uidConn.setRequestProperty("Accept", "application/json")
                    if (uidConn.responseCode in 200..299) {
                        val uidBody = uidConn.inputStream.bufferedReader().readText()
                        if (uidBody.isNotBlank() && uidBody != "[]") {
                            code = uidConn.responseCode
                            body = uidBody
                        }
                    }
                } catch (_: Exception) {}
            }

            if (code in 200..299 && body.isNotBlank() && body != "[]") {
                fun extractField(json: String, key: String): String {
                    val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                }
                fun extractLong(json: String, key: String): Long {
                    val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
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

                var isPro = body.contains("\"is_pro\":true") || body.contains("\"is_pro\": true")
                var proStartDate = extractLong(body, "pro_start_date")
                var proRenewalDate = extractLong(body, "pro_renewal_date")

                // Check Supabase auth.users user_metadata via admin
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
                            if (fn.isBlank() && ln.isBlank()) {
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
                            if (!isPro) {
                                isPro = userBody.contains("\"is_pro\":true") || userBody.contains("\"is_pro\": true")
                            }
                            if (proStartDate <= 0L) {
                                proStartDate = extractLong(userBody, "pro_start_date")
                            }
                            if (proRenewalDate <= 0L) {
                                proRenewalDate = extractLong(userBody, "pro_renewal_date")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("SupabaseNetwork", "auth.users fallback check: ${e.message}")
                    }
                }

                val provStr = extractField(body, "auth_provider")
                val provider = when (provStr.lowercase()) {
                    "google" -> IdentityProvider.Google
                    "apple" -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
                return@withContext UserProfileData(cleanEmail, fn, ln, provider, isPro, proStartDate, proRenewalDate)
            }

            // Direct Auth Admin fallback if profiles table didn't have this user row
            try {
                val uid = getUserIdFromProfile(cleanEmail)
                if (!uid.isNullOrBlank()) {
                    val userUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$uid")
                    val userConn = userUrl.openConnection() as HttpURLConnection
                    userConn.requestMethod = "GET"
                    userConn.connectTimeout = 8000
                    userConn.readTimeout = 8000
                    userConn.setRequestProperty("apikey", effectiveKey)
                    userConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
                    userConn.setRequestProperty("Accept", "application/json")
                    if (userConn.responseCode in 200..299) {
                        val userBody = userConn.inputStream.bufferedReader().readText()
                        fun extractField(json: String, key: String): String {
                            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                            return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                        }
                        fun extractLong(json: String, key: String): Long {
                            val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                            return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                        }
                        var fn = extractField(userBody, "first_name")
                        var ln = extractField(userBody, "last_name")
                        val isPro = userBody.contains("\"is_pro\":true") || userBody.contains("\"is_pro\": true")
                        val proStart = extractLong(userBody, "pro_start_date")
                        val proRenew = extractLong(userBody, "pro_renewal_date")
                        val provStr = extractField(userBody, "provider")
                        val prov = when (provStr.lowercase()) {
                            "google" -> IdentityProvider.Google
                            "apple" -> IdentityProvider.Apple
                            else -> IdentityProvider.EmailPassword
                        }
                        return@withContext UserProfileData(cleanEmail, fn, ln, prov, isPro, proStart, proRenew)
                    }
                }
            } catch (ex: Exception) {
                Log.w("SupabaseNetwork", "fetchUserProfile direct auth fallback: ${ex.message}")
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
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/rpc/update_user_password")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = org.json.JSONObject().apply {
                put("p_email", cleanEmail)
                put("p_new_password", newPassword)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "update_user_password RPC code=$responseCode body=$body")
            return@withContext responseCode in 200..299 && body.trim() == "true"
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

    actual suspend fun updateUserProStatus(
        email: String,
        isPro: Boolean,
        startDate: Long,
        renewalDate: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val userId = SessionManager.getLoggedInUserId() ?: getUserIdFromProfile(cleanEmail)

            // 1. Upsert / update profiles table
            try {
                val profPayload = org.json.JSONObject().apply {
                    if (!userId.isNullOrBlank()) put("id", userId)
                    put("email", cleanEmail)
                    put("is_pro", isPro)
                    if (startDate > 0L) put("pro_start_date", startDate)
                    if (renewalDate > 0L) put("pro_renewal_date", renewalDate)
                }

                // POST upsert (merge-duplicates)
                val postUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles")
                val postConn = postUrl.openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.connectTimeout = 8000
                postConn.readTimeout = 8000
                postConn.setRequestProperty("apikey", effectiveKey)
                postConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
                postConn.setRequestProperty("Content-Type", "application/json")
                postConn.setRequestProperty("Prefer", "resolution=merge-duplicates")
                postConn.doOutput = true
                postConn.outputStream.use { it.write(profPayload.toString().toByteArray(Charsets.UTF_8)) }
                val postCode = postConn.responseCode
                Log.d("SupabaseNetwork", "updateUserProStatus profiles POST code=$postCode")

                // Also try PATCH by email
                val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/profiles?email=eq.$cleanEmail")
                val patchConn = patchUrl.openConnection() as HttpURLConnection
                patchConn.requestMethod = "PATCH"
                patchConn.connectTimeout = 8000
                patchConn.readTimeout = 8000
                patchConn.setRequestProperty("apikey", effectiveKey)
                patchConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
                patchConn.setRequestProperty("Content-Type", "application/json")
                patchConn.setRequestProperty("Prefer", "return=minimal")
                patchConn.doOutput = true
                patchConn.outputStream.use { it.write(profPayload.toString().toByteArray(Charsets.UTF_8)) }
                val patchCode = patchConn.responseCode
                Log.d("SupabaseNetwork", "updateUserProStatus profiles PATCH code=$patchCode")
            } catch (pe: Exception) {
                Log.w("SupabaseNetwork", "updateUserProStatus profiles error: ${pe.message}")
            }

            // 2. ALWAYS update Supabase auth.users user_metadata so status syncs even if custom columns don't exist in profiles
            if (!userId.isNullOrBlank()) {
                try {
                    val authUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/auth/v1/admin/users/$userId")
                    val authConn = authUrl.openConnection() as HttpURLConnection
                    authConn.requestMethod = "PUT"
                    authConn.connectTimeout = 8000
                    authConn.readTimeout = 8000
                    authConn.setRequestProperty("apikey", effectiveKey)
                    authConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
                    authConn.setRequestProperty("Content-Type", "application/json")
                    authConn.doOutput = true

                    val authPayload = """{"user_metadata":{"is_pro":$isPro,"pro_start_date":$startDate,"pro_renewal_date":$renewalDate}}"""
                    authConn.outputStream.use { it.write(authPayload.toByteArray(Charsets.UTF_8)) }
                    val authCode = authConn.responseCode
                    Log.d("SupabaseNetwork", "updateUserProStatus auth.users code=$authCode")
                } catch (ae: Exception) {
                    Log.e("SupabaseNetwork", "updateUserProStatus auth.users error: ${ae.message}")
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "updateUserProStatus failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun getUserId(email: String): String? = withContext(Dispatchers.IO) {
        val clean = email.lowercase().trim()
        if (clean.isBlank()) return@withContext null
        return@withContext getUserIdFromProfile(clean)
    }

    actual suspend fun getActiveMobileDevices(email: String): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext emptyList()
        try {
            val cleanEmail = email.lowercase().trim().ifBlank { SessionManager.getLoggedInEmail()?.lowercase()?.trim() ?: "" }
            val userId = SessionManager.getLoggedInUserId() ?: if (cleanEmail.isNotBlank()) getUserIdFromProfile(cleanEmail) else null
            if (userId.isNullOrBlank()) {
                Log.d("SupabaseNetwork", "getActiveMobileDevices: no userId, returning emptyList")
                return@withContext emptyList()
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?user_id=eq.$userId&or=(device_type.eq.Android,device_type.eq.iOS,device_type.eq.Mobile)&select=id,device_name,device_type,push_token,local_ip,port")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body.isBlank() || body == "[]") return@withContext emptyList()
                val jsonArr = org.json.JSONArray(body)
                val list = mutableListOf<DiscoveredDevice>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        DiscoveredDevice(
                            id = obj.optString("id"),
                            name = obj.optString("device_name", "Mobile Device"),
                            host = obj.optString("local_ip", ""),
                            port = obj.optInt("port", 52424),
                            osType = obj.optString("device_type", "Mobile"),
                            pushToken = if (obj.isNull("push_token")) null else obj.optString("push_token").ifBlank { null },
                            isRemote = true
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "getActiveMobileDevices failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    actual suspend fun removeUserDevice(email: String, deviceId: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            // 1. First get device token and network info to send remote logout notification
            val queryUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?id=eq.$deviceId&select=push_token,device_name,local_ip,port")
            val queryConn = queryUrl.openConnection() as HttpURLConnection
            queryConn.requestMethod = "GET"
            queryConn.connectTimeout = 6000
            queryConn.readTimeout = 6000
            queryConn.setRequestProperty("apikey", effectiveKey)
            queryConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            var pushToken: String? = null
            var localIp: String? = null
            var port: Int = 52424
            if (queryConn.responseCode in 200..299) {
                val body = queryConn.inputStream.bufferedReader().readText()
                if (body.isNotBlank() && body != "[]") {
                    val arr = org.json.JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        pushToken = if (obj.isNull("push_token")) null else obj.optString("push_token").ifBlank { null }
                        localIp = if (obj.isNull("local_ip")) null else obj.optString("local_ip").ifBlank { null }
                        port = obj.optInt("port", 52424)
                    }
                }
            }

            // A. Direct local socket logout notification if device is reachable on network
            if (!localIp.isNullOrBlank() && !localIp.startsWith("remote-") && port > 0) {
                try {
                    val peerDev = net.syncswift.discovery.DiscoveredDevice(id = deviceId, name = "", host = localIp, port = port, osType = "")
                    ConnectionManager.notifyRemoteLogout(peerDev)
                } catch (_: Exception) {}
            }

            // B. Send remote FCM push notification
            if (!pushToken.isNullOrBlank()) {
                val copy = net.syncswift.currentAppCopy()
                ConnectionManager.sendFcmNotification(
                    token = pushToken,
                    title = "SyncSwift",
                    body = copy.remoteSignedOut,
                    data = mapOf(
                        "type" to "remote_logout",
                        "deviceId" to deviceId
                    )
                )
            }

            // 2. Delete device from Supabase devices table
            val delUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?id=eq.$deviceId")
            val delConn = delUrl.openConnection() as HttpURLConnection
            delConn.requestMethod = "DELETE"
            delConn.connectTimeout = 8000
            delConn.readTimeout = 8000
            delConn.setRequestProperty("apikey", effectiveKey)
            delConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            return@withContext delConn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "removeUserDevice failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun registerDeviceInSupabase(
        email: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
        pushToken: String,
        localIp: String,
        port: Int,
        userId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim().ifBlank { SessionManager.getLoggedInEmail()?.lowercase()?.trim() ?: "" }
            val cleanName = cleanDeviceDisplayName(deviceName)
            var resolvedUserId = userId?.trim()?.ifBlank { null }
                ?: SessionManager.getLoggedInUserId()

            if (resolvedUserId.isNullOrBlank() && cleanEmail.isNotBlank()) {
                resolvedUserId = getUserIdFromProfile(cleanEmail)
            }

            if (!resolvedUserId.isNullOrBlank()) {
                SessionManager.saveLoggedInUserId(resolvedUserId)
            }

            val isDesktop = deviceType.contains("win", ignoreCase = true) ||
                            deviceType.contains("mac", ignoreCase = true) ||
                            deviceType.contains("desk", ignoreCase = true) ||
                            deviceType.contains("linux", ignoreCase = true)
            val isCurrentSelf = deviceId == SessionManager.getPlatformDeviceId()

            val resolvedToken = when {
                pushToken.isNotBlank() -> pushToken
                isCurrentSelf && !isDesktop -> SessionManager.getMyPushToken()
                else -> ""
            }
            val resolvedIp = when {
                localIp.isNotBlank() -> localIp
                isCurrentSelf -> getLocalIpAddress() ?: ""
                else -> ""
            }
            val subnet = if (resolvedIp.contains(".")) resolvedIp.substringBeforeLast(".") else ""

            val payload = org.json.JSONObject().apply {
                put("id", deviceId)
                put("device_name", cleanName)
                put("device_type", deviceType)
                if (!resolvedUserId.isNullOrBlank()) {
                    put("user_id", resolvedUserId)
                } else {
                    put("user_id", org.json.JSONObject.NULL)
                }
                if (resolvedToken.isNotBlank()) {
                    put("push_token", resolvedToken)
                } else {
                    put("push_token", org.json.JSONObject.NULL)
                }
                if (resolvedIp.isNotBlank()) put("local_ip", resolvedIp)
                put("port", port)
                if (subnet.isNotBlank()) put("network_ssid", subnet)
                put("is_online", true)
            }

            // 1. Try POST with resolution=merge-duplicates (upsert)
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val postCode = conn.responseCode
            val postErr = if (postCode !in 200..299) conn.errorStream?.bufferedReader()?.readText() ?: "" else ""
            Log.d("SupabaseNetwork", "registerDeviceInSupabase POST code=$postCode err=$postErr")

            // 2. Also execute PATCH on the specific device row to ensure user_id is updated if row already existed
            try {
                val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?id=eq.$deviceId")
                val patchConn = patchUrl.openConnection() as HttpURLConnection
                patchConn.requestMethod = "PATCH"
                patchConn.connectTimeout = 8000
                patchConn.readTimeout = 8000
                patchConn.doOutput = true
                patchConn.setRequestProperty("Content-Type", "application/json")
                patchConn.setRequestProperty("apikey", effectiveKey)
                patchConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
                patchConn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val patchCode = patchConn.responseCode
                Log.d("SupabaseNetwork", "registerDeviceInSupabase PATCH code=$patchCode")
                if (patchCode in 200..299) return@withContext true
            } catch (ex: Exception) {
                Log.w("SupabaseNetwork", "PATCH device fallback error: ${ex.message}")
            }
            return@withContext postCode in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "registerDeviceInSupabase failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun fetchLinkedDesktopDevices(email: String): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext emptyList()
        try {
            val cleanEmail = email.lowercase().trim().ifBlank { SessionManager.getLoggedInEmail()?.lowercase()?.trim() ?: "" }
            val userId = SessionManager.getLoggedInUserId() ?: if (cleanEmail.isNotBlank()) getUserIdFromProfile(cleanEmail) else null
            if (userId.isNullOrBlank()) {
                Log.d("SupabaseNetwork", "fetchLinkedDesktopDevices: no userId, returning emptyList")
                return@withContext emptyList()
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?user_id=eq.$userId&or=(device_type.eq.Windows,device_type.eq.macOS,device_type.eq.Mac,device_type.eq.Desktop)&select=id,device_name,device_type,push_token,local_ip,port")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body.isBlank() || body == "[]") return@withContext emptyList()
                val jsonArr = org.json.JSONArray(body)
                val list = mutableListOf<DiscoveredDevice>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        DiscoveredDevice(
                            id = obj.optString("id"),
                            name = obj.optString("device_name", "Desktop"),
                            host = obj.optString("local_ip", ""),
                            port = obj.optInt("port", 52424),
                            osType = obj.optString("device_type", "Desktop"),
                            pushToken = if (obj.isNull("push_token")) null else obj.optString("push_token").ifBlank { null },
                            isRemote = true
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "fetchLinkedDesktopDevices failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    actual suspend fun fetchAllUserDevices(email: String): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext emptyList()
        try {
            val cleanEmail = email.lowercase().trim().ifBlank { SessionManager.getLoggedInEmail()?.lowercase()?.trim() ?: "" }
            val userId = SessionManager.getLoggedInUserId() ?: if (cleanEmail.isNotBlank()) getUserIdFromProfile(cleanEmail) else null
            if (userId.isNullOrBlank()) {
                Log.d("SupabaseNetwork", "fetchAllUserDevices: no userId, returning emptyList")
                return@withContext emptyList()
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?user_id=eq.$userId&select=id,device_name,device_type,push_token,local_ip,port,is_online")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body.isBlank() || body == "[]") return@withContext emptyList()
                val jsonArr = org.json.JSONArray(body)
                val list = mutableListOf<DiscoveredDevice>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        DiscoveredDevice(
                            id = obj.optString("id"),
                            name = obj.optString("device_name", "Device"),
                            host = obj.optString("local_ip", ""),
                            port = obj.optInt("port", 52424),
                            osType = obj.optString("device_type", "Unknown"),
                            pushToken = if (obj.isNull("push_token")) null else obj.optString("push_token").ifBlank { null },
                            isOnline = obj.optBoolean("is_online", true),
                            isRemote = true
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "fetchAllUserDevices failed: ${e.message}")
        }
        return@withContext emptyList()
    }

    actual suspend fun deleteUserAccount(email: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank()) return@withContext false
        try {
            val cleanEmail = email.lowercase().trim()
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/rpc/delete_user_account")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = org.json.JSONObject().apply { put("p_email", cleanEmail) }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("SupabaseNetwork", "delete_user_account RPC code=$responseCode body=$body")
            return@withContext responseCode in 200..299 && body.trim() == "true"
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "deleteUserAccount failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun clearDeviceUser(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank() || deviceId.isBlank()) return@withContext false
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?id=eq.$deviceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Prefer", "return=minimal")
            val payload = """{"user_id":null}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SupabaseNetwork", "clearDeviceUser code=$code")
            return@withContext code in 200..299
        } catch (e: Exception) {
            Log.e("SupabaseNetwork", "clearDeviceUser failed: ${e.message}")
            return@withContext false
        }
    }

    actual suspend fun isDeviceActiveInSupabase(deviceId: String, expectedUserId: String?): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.SUPABASE_URL.isBlank() || effectiveKey.isBlank() || deviceId.isBlank()) return@withContext true
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/devices?id=eq.$deviceId&select=id,user_id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText().trim()
                if (body.isBlank() || body == "[]") {
                    return@withContext false
                }
                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) return@withContext false
                val obj = arr.getJSONObject(0)
                val userIdInDb = if (obj.isNull("user_id")) null else obj.optString("user_id").trim().ifBlank { null }
                if (userIdInDb == null) return@withContext false
                if (!expectedUserId.isNullOrBlank() && !userIdInDb.equals(expectedUserId.trim(), ignoreCase = true)) {
                    return@withContext false
                }
                return@withContext true
            }
            return@withContext true
        } catch (_: Exception) {
            return@withContext true
        }
    }

    private fun getLocalIpAddress(): String? {
        return net.syncswift.discovery.DeviceDiscoveryManager.getLocalIpAddress()
    }

    actual suspend fun publishQuickAccessSession(
        code: String,
        sessionId: String,
        senderName: String,
        fileName: String,
        fileSize: Long,
        textContent: String?,
        host: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

            val safeDeviceId = try {
                java.util.UUID.fromString(sessionId).toString()
            } catch (_: Exception) {
                java.util.UUID.nameUUIDFromBytes(sessionId.toByteArray()).toString()
            }
            val safeText = (textContent ?: "").replace("\"", "\\\"").replace("\n", "\\n")
            val devName = "$senderName:::$fileName:::$fileSize:::$safeText"
            val expiresAt = java.time.Instant.now().plusSeconds(600).toString()
            val json = org.json.JSONObject().apply {
                put("code", code)
                put("device_id", safeDeviceId)
                put("device_name", devName)
                put("device_type", "QuickAccess")
                put("host", host)
                put("port", port)
                put("expires_at", expiresAt)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "publishQuickAccessSession error: ${e.message}")
            false
        }
    }

    actual suspend fun queryQuickAccessSession(code: String): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val clean = code.trim().replace(" ", "")
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean&select=*&order=created_at.desc")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val result = mutableMapOf<String, String>()
                    result["code"] = obj.optString("code", clean)
                    result["sessionId"] = obj.optString("device_id", "")
                    result["deviceName"] = obj.optString("device_name", "")
                    result["deviceType"] = obj.optString("device_type", "")
                    result["host"] = obj.optString("host", "")
                    result["port"] = obj.optInt("port", 52424).toString()
                    return@withContext result
                }
            }
            null
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "queryQuickAccessSession error: ${e.message}")
            null
        }
    }

    actual suspend fun publishWorkspaceInvite(
        code: String,
        workspaceId: String,
        workspaceName: String,
        workspaceDesc: String,
        ownerEmail: String,
        memberCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

            val safeWsId = try {
                java.util.UUID.fromString(workspaceId).toString()
            } catch (_: Exception) {
                java.util.UUID.nameUUIDFromBytes(workspaceId.toByteArray()).toString()
            }
            val expiresAt = java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS).toString()
            val safeName = workspaceName.replace("\"", "\\\"").replace("\n", " ")
            val devName = "$safeName:::$ownerEmail"

            val json = org.json.JSONObject().apply {
                put("code", code)
                put("device_id", safeWsId)
                put("device_name", devName)
                put("device_type", "WorkspaceInvite")
                put("host", workspaceDesc)
                put("port", memberCount)
                put("push_token", "")
                put("expires_at", expiresAt)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "publishWorkspaceInvite error: ${e.message}")
            false
        }
    }

    actual suspend fun queryWorkspaceInvite(code: String): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val clean = code.trim().replace(" ", "")
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean&device_type=eq.WorkspaceInvite&select=*&order=created_at.desc")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val rawName = obj.optString("device_name", "")
                    val wsName = if (rawName.contains(":::")) rawName.substringBefore(":::") else rawName
                    val ownerEmailFromDev = if (rawName.contains(":::")) rawName.substringAfter(":::") else obj.optString("user_id", "")
                    val result = mutableMapOf<String, String>()
                    result["code"] = obj.optString("code", clean)
                    result["workspaceId"] = obj.optString("device_id", "")
                    result["name"] = wsName
                    result["desc"] = obj.optString("host", "")
                    result["memberCount"] = obj.optInt("port", 1).toString()
                    result["ownerEmail"] = ownerEmailFromDev
                    result["pushToken"] = obj.optString("push_token", "")
                    return@withContext result
                }
            }
            null
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "queryWorkspaceInvite error: ${e.message}")
            null
        }
    }

    actual suspend fun notifyWorkspaceJoined(
        code: String,
        workspaceId: String,
        joinerEmail: String,
        joinerName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = code.trim().replace(" ", "")
            // Fetch current push_token string to append
            val getUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean&select=push_token,port")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.connectTimeout = 6000
            getConn.readTimeout = 6000
            getConn.setRequestProperty("apikey", effectiveKey)
            getConn.setRequestProperty("Authorization", "Bearer $effectiveKey")

            var existingToken = ""
            var currentCount = 1
            if (getConn.responseCode in 200..299) {
                val body = getConn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    existingToken = obj.optString("push_token", "")
                    currentCount = obj.optInt("port", 1)
                }
            }

            val newEntry = "${joinerEmail.trim()}:::${joinerName.trim()}"
            val updatedToken = if (existingToken.isBlank()) newEntry else "$existingToken,$newEntry"
            val newCount = currentCount + 1

            val patchUrl = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$clean")
            val patchConn = patchUrl.openConnection() as HttpURLConnection
            patchConn.requestMethod = "PATCH"
            patchConn.connectTimeout = 6000
            patchConn.readTimeout = 6000
            patchConn.doOutput = true
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.setRequestProperty("apikey", effectiveKey)
            patchConn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            patchConn.setRequestProperty("Prefer", "return=minimal")

            val json = org.json.JSONObject().apply {
                put("push_token", updatedToken)
                put("port", newCount)
            }
            patchConn.outputStream.use { it.write(json.toString().toByteArray()) }
            patchConn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "notifyWorkspaceJoined error: ${e.message}")
            false
        }
    }

    actual suspend fun fetchWorkspaceMembers(workspaceId: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        if (workspaceId.isBlank()) return@withContext emptyList()
        try {
            val safeWsId = try {
                java.util.UUID.fromString(workspaceId).toString()
            } catch (_: Exception) {
                java.util.UUID.nameUUIDFromBytes(workspaceId.toByteArray()).toString()
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?device_id=eq.$safeWsId&device_type=eq.WorkspaceInvite&select=push_token,port,device_name&order=created_at.desc")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(body)
                val results = mutableListOf<Map<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tokens = obj.optString("push_token", "")
                    if (tokens.isNotBlank()) {
                        tokens.split(",").forEach { entry ->
                            val parts = entry.split(":::")
                            val email = parts.getOrNull(0)?.trim() ?: ""
                            val name = parts.getOrNull(1)?.trim() ?: email
                            if (email.isNotBlank() && results.none { it["email"] == email }) {
                                results.add(mapOf("email" to email, "displayName" to name))
                            }
                        }
                    }
                }
                return@withContext results
            }
            emptyList()
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "fetchWorkspaceMembers error: ${e.message}")
            emptyList()
        }
    }

    actual suspend fun publishRemoteTransferSession(
        transferId: String,
        targetDeviceId: String,
        targetDeviceName: String,
        senderName: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        downloadUrlOrContent: String,
        isClipboard: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

            val cleanTargetId = if (targetDeviceId.isNotBlank() && !targetDeviceId.contains(".") && !targetDeviceId.startsWith("remote-")) {
                targetDeviceId.trim()
            } else {
                cleanDeviceDisplayName(targetDeviceName)
            }
            val meta = "$senderName:::$fileName:::$fileSize:::$mimeType"
            val devType = if (isClipboard) "RemoteClipboard" else "RemoteFile"
            val expiresAt = java.time.Instant.now().plusSeconds(900).toString()

            val json = org.json.JSONObject().apply {
                put("code", transferId)
                put("device_id", cleanTargetId)
                put("device_name", meta)
                put("device_type", devType)
                put("local_ip", "remote")
                put("port", 52424)
                put("push_token", downloadUrlOrContent)
                put("status", "PENDING")
                put("expires_at", expiresAt)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w("SupabaseNetwork", "publishRemoteTransferSession error: ${e.message}")
            false
        }
    }

    actual suspend fun pollPendingRemoteTransfers(
        myDeviceId: String,
        myDeviceName: String
    ): List<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanName = cleanDeviceDisplayName(myDeviceName)
            val encName = java.net.URLEncoder.encode(cleanName, "UTF-8").replace("+", "%20")
            val encId = java.net.URLEncoder.encode(myDeviceId, "UTF-8").replace("+", "%20")
            val filter = if (myDeviceId.isNotBlank() && cleanName.isNotBlank() && myDeviceId != cleanName) {
                "or=(device_id.eq.$encId,device_id.eq.$encName)"
            } else if (myDeviceId.isNotBlank()) {
                "device_id=eq.$encId"
            } else {
                "device_id=eq.$encName"
            }
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?$filter&status=eq.PENDING&order=created_at.desc&limit=10")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.isBlank() || body == "[]") return@withContext emptyList()
                val arr = org.json.JSONArray(body)
                val results = mutableListOf<Map<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val devType = obj.optString("device_type", "")
                    if (devType == "RemoteFile" || devType == "RemoteClipboard") {
                        val meta = obj.optString("device_name", "")
                        val parts = meta.split(":::")
                        val sName = parts.getOrNull(0) ?: "Remote Device"
                        val fName = parts.getOrNull(1) ?: "File"
                        val fSize = parts.getOrNull(2) ?: "0"
                        val mMime = parts.getOrNull(3) ?: "application/octet-stream"
                        val content = obj.optString("push_token", "")
                        results.add(mapOf(
                            "transferId" to obj.optString("code"),
                            "senderName" to sName,
                            "fileName" to fName,
                            "fileSize" to fSize,
                            "mimeType" to mMime,
                            "content" to content,
                            "deviceType" to devType,
                            "status" to obj.optString("status", "PENDING")
                        ))
                    }
                }
                return@withContext results
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun updateRemoteTransferStatus(transferId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$transferId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")

            val json = org.json.JSONObject().apply {
                put("status", status)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun checkRemoteTransferStatus(transferId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SyncSwiftConfig.SUPABASE_URL}/rest/v1/pairing_codes?code=eq.$transferId&select=status")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("apikey", effectiveKey)
            conn.setRequestProperty("Authorization", "Bearer $effectiveKey")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.isNotBlank() && body != "[]") {
                    val arr = org.json.JSONArray(body)
                    if (arr.length() > 0) {
                        return@withContext arr.getJSONObject(0).optString("status", null)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}


