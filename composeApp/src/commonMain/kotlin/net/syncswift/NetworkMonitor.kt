package net.syncswift

import kotlinx.coroutines.flow.StateFlow

expect object NetworkMonitor {
    val isConnected: StateFlow<Boolean>
    fun startMonitoring()
}
