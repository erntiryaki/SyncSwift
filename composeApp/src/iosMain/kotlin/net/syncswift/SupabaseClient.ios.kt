package net.syncswift

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlin.coroutines.resume
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.connection.ConnectionManager

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

            var userId: String? = null
            if (authResp.statusCode in 200..299) {
                val idRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                userId = idRegex.find(authResp.body)?.groupValues?.getOrNull(1)
            }
            if (userId.isNullOrBlank()) {
                userId = getUserIdFromProfile(clean)
            }
            if (!userId.isNullOrBlank()) {
                SessionManager.saveLoggedInUserId(userId)
            }

            // 2. Upsert profile in public.profiles table
            val idField = if (!userId.isNullOrBlank()) "\"id\": \"$userId\"," else ""
            val profPayload = if (hasNames) {
                """
                    {
                        $idField
                        "email": "$clean",
                        "first_name": "$firstName",
                        "last_name": "$lastName",
                        "auth_provider": "$providerString"
                    }
                """.trimIndent()
            } else {
                """
                    {
                        $idField
                        "email": "$clean",
                        "auth_provider": "$providerString"
                    }
                """.trimIndent()
            }

            executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles",
                method = "POST",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "resolution=merge-duplicates"
                ),
                body = profPayload
            )

            executeRequest(
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
            val isSuccess = resp.statusCode in 200..299 && resp.body.contains("access_token")
            if (isSuccess) {
                val idRegex = "\"user\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val id = idRegex.find(resp.body)?.groupValues?.getOrNull(1)
                    ?: "\"id\"\\s*:\\s*\"([a-f0-9\\-]{36})\"".toRegex().find(resp.body)?.groupValues?.getOrNull(1)
                if (!id.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(id)
                }
                val isPro = resp.body.contains("\"is_pro\":true") || resp.body.contains("\"is_pro\": true")
                if (isPro) {
                    net.syncswift.subscription.SubscriptionManager.setProState(true)
                }
            }
            return isSuccess
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun getUserIdFromProfile(cleanEmail: String): String? {
        val cachedId = SessionManager.getLoggedInUserId()
        if (!cachedId.isNullOrBlank()) return cachedId

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
                val match = regex.find(resp.body)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(match)
                    return match
                }
            }
        } catch (_: Exception) {}

        // Fallback: Query Supabase Auth Admin Users API
        try {
            val adminResp = executeRequest(
                urlString = "$supabaseUrl/auth/v1/admin/users?per_page=1000",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (adminResp.statusCode in 200..299 && adminResp.body.contains(cleanEmail)) {
                val blockRegex = """\{[^{}]*"id"\s*:\s*"([a-f0-9\-]+)"[^{}]*"email"\s*:\s*"$cleanEmail"[^{}]*\}""".toRegex(RegexOption.IGNORE_CASE)
                val blockMatch = blockRegex.find(adminResp.body)?.groupValues?.getOrNull(1)
                if (!blockMatch.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(blockMatch)
                    return blockMatch
                }
                val altRegex = """\{[^{}]*"email"\s*:\s*"$cleanEmail"[^{}]*"id"\s*:\s*"([a-f0-9\-]+)"[^{}]*\}""".toRegex(RegexOption.IGNORE_CASE)
                val altMatch = altRegex.find(adminResp.body)?.groupValues?.getOrNull(1)
                if (!altMatch.isNullOrBlank()) {
                    SessionManager.saveLoggedInUserId(altMatch)
                    return altMatch
                }
            }
        } catch (_: Exception) {}
        return null
    }

    actual suspend fun fetchUserProfile(email: String): UserProfileData? {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return null
        try {
            val clean = cleanEmail(email)
            var resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean&select=email,first_name,last_name,auth_provider,is_pro,pro_start_date,pro_renewal_date",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            // Fallback if is_pro column not yet in profiles table
            if (resp.statusCode !in 200..299) {
                resp = executeRequest(
                    urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean&select=email,first_name,last_name,auth_provider",
                    method = "GET",
                    headers = mapOf(
                        "apikey" to serviceKey,
                        "Authorization" to "Bearer $serviceKey",
                        "Accept" to "application/json"
                    )
                )
            }
            if (resp.statusCode in 200..299 && resp.body != "[]" && resp.body.isNotBlank()) {
                fun extractField(json: String, key: String): String {
                    val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                }
                fun extractLong(json: String, key: String): Long {
                    val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                    return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
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
                var isPro = resp.body.contains("\"is_pro\":true") || resp.body.contains("\"is_pro\": true")
                var proStartDate = extractLong(resp.body, "pro_start_date")
                var proRenewalDate = extractLong(resp.body, "pro_renewal_date")

                // Check Supabase auth.users user_metadata fallback
                val userId = getUserIdFromProfile(clean)
                if (!userId.isNullOrBlank()) {
                    try {
                        val userResp = executeRequest(
                            urlString = "$supabaseUrl/auth/v1/admin/users/$userId",
                            method = "GET",
                            headers = mapOf(
                                "apikey" to serviceKey,
                                "Authorization" to "Bearer $serviceKey",
                                "Accept" to "application/json"
                            )
                        )
                        if (userResp.statusCode in 200..299) {
                            if (fn.isBlank() && ln.isBlank()) {
                                fn = extractField(userResp.body, "first_name")
                                ln = extractField(userResp.body, "last_name")
                                if (fn.isBlank() && ln.isBlank()) {
                                    val full = extractField(userResp.body, "full_name").ifBlank { extractField(userResp.body, "name") }
                                    if (full.isNotBlank()) {
                                        fn = full.split(" ").firstOrNull() ?: ""
                                        ln = full.split(" ").drop(1).joinToString(" ")
                                    }
                                }
                            }
                            if (!isPro) {
                                isPro = userResp.body.contains("\"is_pro\":true") || userResp.body.contains("\"is_pro\": true")
                            }
                            if (proStartDate <= 0L) {
                                proStartDate = extractLong(userResp.body, "pro_start_date")
                            }
                            if (proRenewalDate <= 0L) {
                                proRenewalDate = extractLong(userResp.body, "pro_renewal_date")
                            }
                        }
                    } catch (_: Exception) {}
                }

                val provStr = extractField(resp.body, "auth_provider")
                val provider = when (provStr.lowercase()) {
                    "google" -> IdentityProvider.Google
                    "apple" -> IdentityProvider.Apple
                    else -> IdentityProvider.EmailPassword
                }
                return UserProfileData(clean, fn, ln, provider, isPro, proStartDate, proRenewalDate)
            }

            // Direct Auth Admin fallback if profiles table didn't have this user row
            try {
                val uid = getUserIdFromProfile(clean)
                if (!uid.isNullOrBlank()) {
                    val userResp = executeRequest(
                        urlString = "$supabaseUrl/auth/v1/admin/users/$uid",
                        method = "GET",
                        headers = mapOf(
                            "apikey" to serviceKey,
                            "Authorization" to "Bearer $serviceKey",
                            "Accept" to "application/json"
                        )
                    )
                    if (userResp.statusCode in 200..299) {
                        fun extractField(json: String, key: String): String {
                            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                            return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
                        }
                        fun extractLong(json: String, key: String): Long {
                            val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                            return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                        }
                        var fn = extractField(userResp.body, "first_name")
                        var ln = extractField(userResp.body, "last_name")
                        val isPro = userResp.body.contains("\"is_pro\":true") || userResp.body.contains("\"is_pro\": true")
                        val proStart = extractLong(userResp.body, "pro_start_date")
                        val proRenew = extractLong(userResp.body, "pro_renewal_date")
                        val provStr = extractField(userResp.body, "provider")
                        val prov = when (provStr.lowercase()) {
                            "google" -> IdentityProvider.Google
                            "apple" -> IdentityProvider.Apple
                            else -> IdentityProvider.EmailPassword
                        }
                        return UserProfileData(clean, fn, ln, prov, isPro, proStart, proRenew)
                    }
                }
            } catch (_: Exception) {}
            return null
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

    actual suspend fun updateUserProStatus(
        email: String,
        isPro: Boolean,
        startDate: Long,
        renewalDate: Long
    ): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val userId = SessionManager.getLoggedInUserId() ?: getUserIdFromProfile(clean)

            // 1. Upsert / update profiles table
            val idField = if (!userId.isNullOrBlank()) "\"id\": \"$userId\"," else ""
            val profPayload = """
                {
                    $idField
                    "email": "$clean",
                    "is_pro": $isPro,
                    "pro_start_date": $startDate,
                    "pro_renewal_date": $renewalDate
                }
            """.trimIndent()

            executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles",
                method = "POST",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "resolution=merge-duplicates"
                ),
                body = profPayload
            )

            executeRequest(
                urlString = "$supabaseUrl/rest/v1/profiles?email=eq.$clean",
                method = "PATCH",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "return=minimal"
                ),
                body = profPayload
            )

            // 2. ALWAYS update auth.users metadata
            if (!userId.isNullOrBlank()) {
                val authPayload = """{"user_metadata":{"is_pro":$isPro,"pro_start_date":$startDate,"pro_renewal_date":$renewalDate}}"""
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
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun parseDeviceJsonArray(json: String): List<DiscoveredDevice> {
        if (json.isBlank() || json == "[]") return emptyList()
        val list = mutableListOf<DiscoveredDevice>()
        val clean = json.trim().removePrefix("[").removeSuffix("]").trim()
        if (clean.isBlank()) return emptyList()
        val regex = """\{([^{}]*)\}""".toRegex()
        regex.findAll(clean).forEach { match ->
            val block = match.groupValues[1]
            fun extract(key: String): String {
                val r = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return r.find(block)?.groupValues?.getOrNull(1) ?: ""
            }
            fun extractInt(key: String): Int {
                val r = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return r.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 52424
            }
            val id = extract("id")
            val name = extract("device_name").ifBlank { "Device" }
            val type = extract("device_type").ifBlank { "Mobile" }
            val host = extract("local_ip")
            val port = extractInt("port")
            val token = extract("push_token").ifBlank { null }
            if (id.isNotBlank()) {
                list.add(
                    DiscoveredDevice(
                        id = id,
                        name = name,
                        host = host,
                        port = port,
                        osType = type,
                        pushToken = token,
                        isRemote = true
                    )
                )
            }
        }
        return list
    }

    actual suspend fun getUserId(email: String): String? {
        val clean = cleanEmail(email)
        if (clean.isBlank()) return null
        return getUserIdFromProfile(clean)
    }

    actual suspend fun getActiveMobileDevices(email: String): List<DiscoveredDevice> {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return emptyList()
        try {
            val clean = cleanEmail(email)
            val userId = SessionManager.getLoggedInUserId() ?: if (clean.isNotBlank()) getUserIdFromProfile(clean) else null
            val userFilter = if (!userId.isNullOrBlank()) {
                "or=(user_email.eq.$clean,user_id.eq.$userId)"
            } else {
                "user_email=eq.$clean"
            }
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?$userFilter&or=(device_type.eq.Android,device_type.eq.iOS,device_type.eq.Mobile)&select=id,device_name,device_type,push_token,local_ip,port",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299) {
                return parseDeviceJsonArray(resp.body)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    actual suspend fun removeUserDevice(email: String, deviceId: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            // 1. Get token to notify remote logout
            val queryResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?id=eq.$deviceId&select=push_token",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            val devices = parseDeviceJsonArray(queryResp.body)
            val token = devices.firstOrNull()?.pushToken
            if (!token.isNullOrBlank()) {
                ConnectionManager.sendFcmNotification(
                    token = token,
                    title = "",
                    body = "",
                    data = mapOf(
                        "type" to "remote_logout",
                        "deviceId" to deviceId
                    )
                )
            }

            // 2. Delete device
            val delResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?id=eq.$deviceId",
                method = "DELETE",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey"
                )
            )
            return delResp.statusCode in 200..299
        } catch (_: Exception) {
            return false
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
    ): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return false
        try {
            val clean = cleanEmail(email)
            val cleanName = cleanDeviceDisplayName(deviceName)
            var resolvedUserId = userId?.trim()?.ifBlank { null }
                ?: SessionManager.getLoggedInUserId()

            if (resolvedUserId.isNullOrBlank() && clean.isNotBlank()) {
                resolvedUserId = getUserIdFromProfile(clean)
            }

            if (!resolvedUserId.isNullOrBlank()) {
                SessionManager.saveLoggedInUserId(resolvedUserId)
            }

            val currentLang = SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()
            val userField = if (clean.isNotBlank()) "\"user_email\": \"$clean\"," else ""
            val userIdField = if (!resolvedUserId.isNullOrBlank()) "\"user_id\": \"$resolvedUserId\"," else ""
            val body = """
                {
                    "id": "$deviceId",
                    "device_name": "$cleanName",
                    "device_type": "$deviceType",
                    $userField
                    $userIdField
                    "push_token": "$pushToken",
                    "local_ip": "$localIp",
                    "port": $port,
                    "language": "$currentLang",
                    "is_online": true
                }
            """.trimIndent()
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices",
                method = "POST",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "resolution=merge-duplicates"
                ),
                body = body
            )

            // Also execute PATCH on existing device row to ensure user_id is updated
            val patchResp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?id=eq.$deviceId",
                method = "PATCH",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json"
                ),
                body = body
            )
            return resp.statusCode in 200..299 || patchResp.statusCode in 200..299
        } catch (_: Exception) {
            return false
        }
    }

    actual suspend fun fetchLinkedDesktopDevices(email: String): List<DiscoveredDevice> {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return emptyList()
        try {
            val clean = cleanEmail(email)
            val userId = SessionManager.getLoggedInUserId() ?: if (clean.isNotBlank()) getUserIdFromProfile(clean) else null
            val userFilter = if (!userId.isNullOrBlank()) {
                "or=(user_email.eq.$clean,user_id.eq.$userId)"
            } else {
                "user_email=eq.$clean"
            }
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?$userFilter&or=(device_type.eq.Windows,device_type.eq.macOS,device_type.eq.Mac,device_type.eq.Desktop)&select=id,device_name,device_type,push_token,local_ip,port",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299) {
                return parseDeviceJsonArray(resp.body)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    actual suspend fun fetchAllUserDevices(email: String): List<DiscoveredDevice> {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return emptyList()
        try {
            val clean = cleanEmail(email)
            val userId = SessionManager.getLoggedInUserId() ?: if (clean.isNotBlank()) getUserIdFromProfile(clean) else null
            val userFilter = if (!userId.isNullOrBlank()) {
                "or=(user_email.eq.$clean,user_id.eq.$userId)"
            } else {
                "user_email=eq.$clean"
            }
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?$userFilter&select=id,device_name,device_type,push_token,local_ip,port,is_online",
                method = "GET",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Accept" to "application/json"
                )
            )
            if (resp.statusCode in 200..299) {
                return parseDeviceJsonArray(resp.body)
            }
        } catch (_: Exception) {}
        return emptyList()
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

    actual suspend fun clearDeviceUser(deviceId: String): Boolean {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = effectiveKey
        if (supabaseUrl.isBlank() || serviceKey.isBlank() || deviceId.isBlank()) return false
        try {
            val resp = executeRequest(
                urlString = "$supabaseUrl/rest/v1/devices?id=eq.$deviceId",
                method = "PATCH",
                headers = mapOf(
                    "apikey" to serviceKey,
                    "Authorization" to "Bearer $serviceKey",
                    "Content-Type" to "application/json",
                    "Prefer" to "return=minimal"
                ),
                body = """{"user_id":null,"user_email":null}"""
            )
            return resp.statusCode in 200..299
        } catch (_: Exception) {
            return false
        }
    }
}
