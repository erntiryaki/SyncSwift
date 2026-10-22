package net.syncswift.discovery

actual fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) {}
actual fun stopPlatformUdpDiscovery() {}
actual fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int) {}
actual fun sendDirectUdpPairRequest(targetHost: String, targetPort: Int) {}
actual fun sendDirectUdpFileRequest(targetHost: String, targetPort: Int, fileName: String, fileSize: Long, transferId: String) {}
actual fun sendDirectUdpClipRequest(targetHost: String, targetPort: Int, previewText: String) {}
