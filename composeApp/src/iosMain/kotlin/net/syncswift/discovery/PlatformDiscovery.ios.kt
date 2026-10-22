package net.syncswift.discovery

actual fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) {}
actual fun stopPlatformUdpDiscovery() {}
actual fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int) {}
