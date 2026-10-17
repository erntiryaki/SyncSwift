package net.syncswift

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlin.coroutines.resume

private data class HttpResponse(val statusCode: Int, val body: String)

@OptIn(ExperimentalForeignApi::class)
private suspend fun executeRequest(
    urlString: String,
    method: String,
    headers: Map<String, String>,
    body: String? = null
): HttpResponse = withContext(Dispatchers.Default) {
    suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(urlString)
        if (url == null) {
            continuation.resume(HttpResponse(-1, ""))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(url)
        request.setHTTPMethod(method)
        request.setTimeoutInterval(10.0)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (body != null) {
            val nsStr = body as NSString
            val data = nsStr.dataUsingEncoding(NSUTF8StringEncoding)
            request.setHTTPBody(data)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val httpResp = response as? NSHTTPURLResponse
            val statusCode = httpResp?.statusCode?.toInt() ?: if (error != null) -1 else 200
            val bodyString = if (data != null) {
                NSString.create(data, NSUTF8StringEncoding)?.toString() ?: ""
            } else ""
            if (continuation.isActive) {
                continuation.resume(HttpResponse(statusCode, bodyString))
            }
        }
        task.resume()
        continuation.invokeOnCancellation { task.cancel() }
    }
}

actual object SupabaseNetwork {
    private fun cleanEmail(email: String): String = email.lowercase().trim()
    private val effectiveKey: String get() = PlatformConfig.supabaseServiceRoleKey.ifBlank { PlatformConfig.supabaseAnonKey }

    actual suspend fun registerUserInSupabase(
        email: String,
        firstName: String,
        lastName: String,
        provider: IdentityProvider,
        idToken: String?,
        password: String
    ): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false

        try {
            val clean = cleanEmail(email)
            val providerString = when (provider) {
                IdentityProvider.Google -> "google"
                IdentityProvider.Apple -> "apple"
                else -> "email"
            }

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
                    "email": "$clean",
                    "email_confirm": true,
                    ${if (password.isNotBlank()) "\"password\": \"$password\"," else ""}
                    $userMetaJson
                }
            """.trimIndent()

            val authResp = executeRequest(
                urlString = "$supabaseUrl/auth/v1/admin/users",
                method = "POST",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json"
                ),
                body = payload
            )

            // 2. Upsert profile in public.profiles table
            val profPayload = if (hasNames) {
                """
                    {
                        "email": "$clean",
                        "first_name": "$firstName",
                        "last_name": "$lastName",
                        "auth_provider": "$providerString"
                    }
                """.trimIndent()
            } else {
                """
                    {
                        "email": "$clean",
                        "auth_provider": "$providerString"
                    }
                """.trimIndent()
            }

            val patchResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean",
                method = "PATCH",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "return=representation"
                ),
                body = profPayload
            )

            if (patchResp.body == "[]" || patchResp.body.isBlank()) {
                executeRequest(
                    urlString = "$supabaseUrl/rest/v1/profiles",
                    method = "POST",
                    headers = mapOf(
                        "apikey" to serviceKey,
                        "Authorization" to "Bearer $serviceKey",
                        "Content-Type" to "application/json"
                    ),
                    body = profPayload
                )
            }

            return authResp.statusCode in 200..299 || authResp.statusCode == 422
        } catch (e: Exception) {
            println("[SyncSwift iOS] registerUserInSupabase error: ${e.message}")
            return false
        }
    }

    actual suspend fun checkUserExists(email: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean&select=id",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            return resp.statusCode in 200..299 && resp.body.contains("\"id\"") && resp.body != "[]"
        } catch (e: Exception) {
            return false
        }
    }

    actual suspend fun getUserProvider(email: String): IdentityProvider? {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return null
        try {
            val clean = cleanEmail(email)
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean&select=id,auth_provider",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299 && resp.body != "[]" && resp.body.contains("\"id\"")) {
                return when {
                    resp.body.contains("\"auth_provider\":\"google\"") -> IdentityProvider.Google
                    resp.body.contains("\"auth_provider\":\"apple\"") -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
            }
        } catch (e: Exception) {}
        return null
    }

    actual suspend fun signInWithPassword(email: String, password: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val anonKey = PlatformConfig.supabaseAnonKey
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val resp = executeRequest(
                urlString = "$supabaseUrl/auth/v1/token?grant_type=password",
                method = "POST",
                headers = mapOf(
                    "apikey" to anonKey,
                    "Content-Type" to "application/json"
                ),
                body = """{"email":"$clean","password":"$password"}"""
            )
            return resp.statusCode in 200..299 && resp.body.contains("access_token")
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun getUserIdFromProfile(cleanEmail: String): String? {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return null
        try {
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$cleanEmail&select=id",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299) {
                val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                return regex.find(resp.body)?.groupValues?.getOrNull(1)
            }
        } catch (e: Exception) {}
        return null
    }

    actual suspend fun fetchUserProfile(email: String): UserProfileData? {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return null
        try {
            val clean = cleanEmail(email)
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean&select=email,first_name,last_name,auth_provider",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299 && resp.body != "[]" && resp.body.isNotBlank()) {
                fun extractField(json: String, key: String): String {
                    val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                }
                var fn = extractField(resp.body, "first_name")
                var ln = extractField(resp.body, "last_name")
                if (fn.isBlank() && ln.isBlank()) {
                    val full = extractField(resp.body, "full_name").ifBlank { extractField(resp.body, "name") }
                    if (full.isNotBlank()) {
                        fn = full.split(" ").firstOrNull() ?: ""
                        ln = full.split(" ").drop(1).joinToString(" ")
                    }
                }
                val provStr = extractField(resp.body, "auth_provider")
                val provider = when (provStr.lowercase()) {
                    "google" -> IdentityProvider.Google
                    "apple" -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
                return UserProfileData(clean, fn, ln, provider)
            }
        } catch (e: Exception) {}
        return null
    }

    actual suspend fun updateUserPassword(email: String, newPassword: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val userId = getUserIdFromProfile(clean) ?: return false
            val resp = executeRequest(
                urlString = "$supabaseUrl/auth/v1/admin/users/$userId",
                method = "PUT",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json"
                ),
                body = """{"password":"$newPassword"}"""
            )
            return resp.statusCode in 200..299
        } catch (e: Exception) {
            return false
        }
    }

    actual suspend fun updateUserProfileData(
        oldEmail: String,
        newEmail: String,
        firstName: String,
        lastName: String
    ): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val cleanOld = cleanEmail(oldEmail)
            val cleanNew = cleanEmail(newEmail)
            val cleanFirst = firstName.trim()
            val cleanLast = lastName.trim()

            val profPayload = """{"first_name":"$cleanFirst","last_name":"$cleanLast","email":"$cleanNew"}"""
            val patchResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$cleanOld",
                method = "PATCH",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "return=representation"
                ),
                body = profPayload
            )

            val userId = getUserIdFromProfile(cleanNew) ?: getUserIdFromProfile(cleanOld)
            if (!userId.isNullOrBlank()) {
                val fullName = "$cleanFirst $cleanLast".trim()
                val authPayload = """
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
                executeRequest(
                    urlString = "$supabaseUrl/auth/v1/admin/users/$userId",
                    method = "PUT",
                    headers = mapOf(
                        "apikey" to serviceKey,
                        "Authorization" to "Bearer $serviceKey",
                        "Content-Type" to "application/json"
                    ),
                    body = authPayload
                )
            }

            return patchResp.statusCode in 200..299
        } catch (e: Exception) {
            return false
        }
    }

    actual suspend fun deleteUserAccount(email: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val userId = getUserIdFromProfile(clean)
            if (!userId.isNullOrBlank()) {
                executeRequest(
                    urlString = "$supabaseUrl/auth/v1/admin/users/$userId",
                    method = "DELETE",
                    headers = mapOf(
                        "apikey" to serviceKey,
                        "Authorization" to "Bearer $serviceKey"
                    )
                )
            }

            val profResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean",
                method = "DELETE",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey"
                )
            )

            return profResp.statusCode in 200..299
        } catch (e: Exception) {
            return false
        }
    }
}
