package net.syncswift.discovery

import kotlinx.coroutines.flow.StateFlow

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val osType: String,
    val isSelf: Boolean = false,
    val isOnline: Boolean = true,
    val isRemote: Boolean = false,
    val pushToken: String? = null,
    val language: String? = null,
    val modelCode: String = "",
    val isLoggedIn: Boolean = true
)

expect object DeviceDiscoveryManager {
    val devices: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>
    fun startDiscovery()
    fun stopDiscovery()
    fun getSelfDevice(): DiscoveredDevice?
    fun addManualDevice(host: String, port: Int = 52424, name: String = "SyncSwift Device", osType: String = "Other"): DiscoveredDevice
    fun removeManualDevice(host: String)
    fun triggerDiscoveryPing()
    fun broadcastGoodbye()
}

fun cleanDeviceDisplayName(rawName: String, fallbackOs: String = ""): String {
    var name = rawName
        .removePrefix("SyncSwift-")
        .removePrefix("self-")
        .removePrefix("remote-")
        .removePrefix("manual-")
        .trim()
    val suffixRegex = Regex("""(\s*[\(\[]\s*\d+\s*[\)\]]|\s+-\s*\d+)+$""")
    while (suffixRegex.containsMatchIn(name)) {
        name = name.replace(suffixRegex, "").trim()
    }
    val isUuidOrSerial = name.matches(Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")) ||
                         name.matches(Regex("""^[0-9a-fA-F]{24,40}$""")) ||
                         (name.length in 30..45 && name.count { it.isDigit() } > 10 && name.contains("-"))
    if (isUuidOrSerial) {
        val osLower = fallbackOs.lowercase()
        return when {
            osLower.contains("win") -> "Windows PC"
            osLower.contains("mac") -> "Mac"
            osLower.contains("ios") || osLower.contains("iphone") -> "iPhone"
            osLower.contains("android") -> "Android"
            else -> "Cihaz"
        }
    }
    return name.ifBlank { "Cihaz" }
}
