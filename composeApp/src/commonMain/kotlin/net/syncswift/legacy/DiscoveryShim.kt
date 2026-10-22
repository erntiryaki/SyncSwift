package net.syncswift.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.transfer.LanDiscoveredDevice

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 52424,
    val osType: String = "Mobile",
    val isSelf: Boolean = false,
    val isOnline: Boolean = true,
    val isRemote: Boolean = false,
    val pushToken: String? = null,
    val language: String? = null,
    val modelCode: String = "",
    val isLoggedIn: Boolean = true
)

/**
 * DeviceDiscoveryManager Bridge.
 * Live-bridges to :shared LanDiscoveryEngine for mDNS LAN discovery.
 */
object DeviceDiscoveryManager {
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startDiscovery() {
        _isScanning.value = true
        LanDiscoveryEngine.setDiscovering(true)
        startPlatformUdpDiscovery { dev ->
            addOrUpdateConnectedDevice(dev)
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        LanDiscoveryEngine.setDiscovering(false)
        stopPlatformUdpDiscovery()
    }

    fun getSelfDevice(): DiscoveredDevice? {
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        return DiscoveredDevice(
            id = net.syncswift.SessionManager.getPlatformDeviceId(),
            name = net.syncswift.SessionManager.getMyDeviceName(),
            host = ip,
            isSelf = true
        )
    }

    fun addManualDevice(host: String, port: Int = 52424, name: String = "SyncSwift Device", osType: String = "Other"): DiscoveredDevice {
        val dev = DiscoveredDevice(id = host, name = name, host = host, port = port, osType = osType)
        addOrUpdateConnectedDevice(dev)
        return dev
    }

    fun removeManualDevice(host: String) {
        val list = _devices.value.toMutableList()
        list.removeAll { it.host == host }
        _devices.value = list
        LanDiscoveryEngine.removeDevice(host)
    }

    fun triggerDiscoveryPing() {
        LanDiscoveryEngine.clearExpired()
    }

    fun broadcastGoodbye() {}
    fun getLocalIpAddress(): String? = net.syncswift.getPlatformLocalIpAddress() ?: "127.0.0.1"

    fun addOrUpdateConnectedDevice(device: DiscoveredDevice) {
        val list = _devices.value.toMutableList()
        val idx = list.indexOfFirst { it.id == device.id || (it.host.isNotBlank() && it.host == device.host) }
        if (idx >= 0) {
            list[idx] = device
        } else {
            list.add(device)
        }
        _devices.value = list
        LanDiscoveryEngine.updateDevice(
            LanDiscoveredDevice(
                deviceId = device.id,
                deviceName = device.name,
                osType = device.osType,
                host = device.host,
                port = device.port,
                signingPubKeyBase64 = "",
                agreementPubKeyBase64 = "",
                lastSeenMs = System.currentTimeMillis()
            )
        )
    }

    fun saveManualDevices() {}
    fun init(context: Any) {}
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
