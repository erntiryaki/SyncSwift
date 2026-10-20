package net.syncswift

data class UserProfileData(
    val email: String,
    val firstName: String,
    val lastName: String,
    val provider: IdentityProvider,
    val isPro: Boolean = false,
    val proStartDate: Long = 0L,
    val proRenewalDate: Long = 0L
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

    /** Fetches the first_name, last_name, auth_provider, and pro status from profiles/user_metadata */
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

    /** Updates Pro subscription status in Supabase profiles and user_metadata */
    suspend fun updateUserProStatus(
        email: String,
        isPro: Boolean,
        startDate: Long = 0L,
        renewalDate: Long = 0L
    ): Boolean

    /** Fetches active mobile devices registered under this user email in Supabase */
    suspend fun getActiveMobileDevices(email: String): List<net.syncswift.discovery.DiscoveredDevice>

    /** Removes a registered device from Supabase and sends remote logout notification */
    suspend fun removeUserDevice(email: String, deviceId: String): Boolean

    /** Returns the unique Supabase User ID (UUID) for this email */
    suspend fun getUserId(email: String): String?

    /** Registers the current device in Supabase with the logged-in email and user ID */
    suspend fun registerDeviceInSupabase(
        email: String,
        deviceId: String,
        deviceName: String,
        deviceType: String,
        pushToken: String = "",
        localIp: String = "",
        port: Int = 52424,
        userId: String? = null
    ): Boolean

    /** Fetches linked desktop devices for this user */
    suspend fun fetchLinkedDesktopDevices(email: String): List<net.syncswift.discovery.DiscoveredDevice>

    /** Fetches all devices (mobile and desktop) registered under this user email in Supabase */
    suspend fun fetchAllUserDevices(email: String): List<net.syncswift.discovery.DiscoveredDevice>

    /** Deletes user from auth.users and profiles table */
    suspend fun deleteUserAccount(email: String): Boolean

    /** Clears the user_id and user_email from the device in Supabase upon logout */
    suspend fun clearDeviceUser(deviceId: String): Boolean

    /** Verifies if this device ID is still active in the Supabase devices table for the user */
    suspend fun isDeviceActiveInSupabase(deviceId: String, expectedUserId: String? = null): Boolean

    /** Publishes a temporary 6-digit Quick Access file/snippet session */
    suspend fun publishQuickAccessSession(
        code: String,
        sessionId: String,
        senderName: String,
        fileName: String,
        fileSize: Long,
        textContent: String?,
        host: String,
        port: Int
    ): Boolean

    /** Queries an active Quick Access session by its 6-digit code */
    suspend fun queryQuickAccessSession(code: String): Map<String, String>?

    /** Publishes a 6-digit workspace invite to Supabase */
    suspend fun publishWorkspaceInvite(
        code: String,
        workspaceId: String,
        workspaceName: String,
        workspaceDesc: String,
        ownerEmail: String,
        memberCount: Int
    ): Boolean

    /** Queries workspace invite details by 6-digit code */
    suspend fun queryWorkspaceInvite(code: String): Map<String, String>?

    /** Records that a user joined the workspace using the invite code */
    suspend fun notifyWorkspaceJoined(
        code: String,
        workspaceId: String,
        joinerEmail: String,
        joinerName: String
    ): Boolean

    /** Fetches all joined members for a workspace from invite records */
    suspend fun fetchWorkspaceMembers(workspaceId: String): List<Map<String, String>>
}


