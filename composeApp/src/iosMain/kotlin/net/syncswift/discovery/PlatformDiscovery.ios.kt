package net.syncswift.discovery

actual fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) {}
actual fun stopPlatformUdpDiscovery() {}
actual fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int) {}
actual fun sendDirectUdpPairRequest(targetHost: String, targetPort: Int) {}
actual fun sendDirectUdpPairResponse(targetHost: String, targetPort: Int, accepted: Boolean) {}
actual fun sendDirectUdpDisconnect(targetHost: String, targetPort: Int) {}
actual fun sendDirectUdpFileRequest(targetHost: String, targetPort: Int, fileName: String, fileSize: Long, transferId: String) {}
actual fun sendDirectUdpTransferResponse(targetHost: String, targetPort: Int, transferId: String, accepted: Boolean) {}
actual fun sendDirectUdpClipRequest(targetHost: String, targetPort: Int, previewText: String) {}

