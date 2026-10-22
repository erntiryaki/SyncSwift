package net.syncswift.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.transfer.LanDiscoveredDevice

object LanDiscoveryEngine {
    private val _devices = MutableStateFlow<List<LanDiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<LanDiscoveredDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    fun updateDevice(device: LanDiscoveredDevice) {
        val list = _devices.value.toMutableList()
        val index = list.indexOfFirst { it.deviceId == device.deviceId }
        if (index >= 0) {
            list[index] = device
        } else {
            list.add(device)
        }
        _devices.value = list
    }

    fun removeDevice(deviceId: String) {
        val list = _devices.value.toMutableList()
        list.removeAll { it.deviceId == deviceId }
        _devices.value = list
    }

    fun setDiscovering(active: Boolean) {
        _isDiscovering.value = active
    }

    fun clearExpired(maxAgeMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        val list = _devices.value.filter { (now - it.lastSeenMs) < maxAgeMs }
        _devices.value = list
    }
}
