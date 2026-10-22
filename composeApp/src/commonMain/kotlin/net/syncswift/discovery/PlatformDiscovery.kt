package net.syncswift.discovery

expect fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit)
expect fun stopPlatformUdpDiscovery()
expect fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int = 52424)
expect fun sendDirectUdpPairRequest(targetHost: String, targetPort: Int = 52424)
expect fun sendDirectUdpFileRequest(targetHost: String, targetPort: Int = 52424, fileName: String, fileSize: Long, transferId: String)
expect fun sendDirectUdpClipRequest(targetHost: String, targetPort: Int = 52424, previewText: String)
