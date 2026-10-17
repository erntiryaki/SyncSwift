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
    val modelCode: String = ""
)

expect object DeviceDiscoveryManager {
    val devices: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>
    fun startDiscovery()
    fun stopDiscovery()
    fun getSelfDevice(): DiscoveredDevice?
    fun addManualDevice(host: String, port: Int = 52424, name: String = "SyncSwift Device", osType: String = "Other"): DiscoveredDevice
    fun removeManualDevice(host: String)
}

fun cleanDeviceDisplayName(rawName: String): String {
    var name = rawName.removePrefix("SyncSwift-").trim()
    val suffixRegex = Regex("""(\s*[\(\[]\s*\d+\s*[\)\]]|\s+-\s*\d+)+$""")
    while (suffixRegex.containsMatchIn(name)) {
        name = name.replace(suffixRegex, "").trim()
    }
    return name.ifBlank { "SyncSwift Device" }
}
