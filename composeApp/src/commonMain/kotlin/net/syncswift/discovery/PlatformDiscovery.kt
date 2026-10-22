package net.syncswift.discovery

expect fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit)
expect fun stopPlatformUdpDiscovery()
expect fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int = 52424)
