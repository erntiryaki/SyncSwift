package net.syncswift.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object DeviceDiscoveryManager {
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    actual fun startDiscovery() {}
    actual fun stopDiscovery() {}
    actual fun getSelfDevice(): DiscoveredDevice? = null
    actual fun addManualDevice(host: String, port: Int, name: String, osType: String): DiscoveredDevice {
        return DiscoveredDevice("manual-$host:$port", name, host, port, osType, false, true)
    }
}
