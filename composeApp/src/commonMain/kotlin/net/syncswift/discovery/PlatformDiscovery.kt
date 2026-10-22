package net.syncswift.discovery

expect fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit)
expect fun stopPlatformUdpDiscovery()
