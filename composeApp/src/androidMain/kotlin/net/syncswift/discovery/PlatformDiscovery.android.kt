package net.syncswift.discovery

import android.util.Log
import kotlinx.coroutines.*
import net.syncswift.SessionManager
import net.syncswift.getPlatformLocalIpAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

private var discoveryJob: Job? = null
private var activeSocket: DatagramSocket? = null

actual fun startPlatformUdpDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) {
    stopPlatformUdpDiscovery()

    discoveryJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            val s = try {
                DatagramSocket(52424).apply {
                    broadcast = true
                    soTimeout = 2000
                    reuseAddress = true
                }
            } catch (e: Exception) {
                // If 52424 is busy, bind to ephemeral port for sending
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }
            }
            activeSocket = s
            val buffer = ByteArray(1024)

            // Background broadcaster
            launch {
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                while (isActive) {
                    try {
                        val myIp = getPlatformLocalIpAddress() ?: "127.0.0.1"
                        val myName = SessionManager.getMyDeviceName()
                        val myId = SessionManager.getPlatformDeviceId()
                        val msg = "SYNCSWIFT:ANNOUNCE|$myId|$myName|Android|52424"
                        val data = msg.toByteArray()
                        val packet = DatagramPacket(data, data.size, broadcastAddr, 52424)
                        s.send(packet)
                    } catch (_: Exception) {}
                    delay(3000)
                }
            }

            // Receiver loop
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    val senderIp = packet.address?.hostAddress ?: continue
                    val myIp = getPlatformLocalIpAddress()
                    if (senderIp == myIp || senderIp == "127.0.0.1") continue

                    val msg = String(packet.data, 0, packet.length)
                    if (msg.startsWith("SYNCSWIFT:ANNOUNCE|") || msg.startsWith("SYNCSWIFT:PAIR_ACCEPT|")) {
                        val parts = msg.split("|")
                        if (parts.size >= 5) {
                            val peerId = parts[1]
                            val peerName = parts[2]
                            val peerOs = parts[3]
                            val peerPort = parts[4].toIntOrNull() ?: 52424
                            val dev = DiscoveredDevice(
                                id = peerId,
                                name = peerName,
                                host = senderIp,
                                port = peerPort,
                                osType = peerOs,
                                isSelf = false,
                                isOnline = true
                            )
                            withContext(Dispatchers.Main) {
                                onDeviceFound(dev)
                            }
                        }
                    } else if (msg.startsWith("SYNCSWIFT:PAIR_REQ|")) {
                        val parts = msg.split("|")
                        val senderId = parts.getOrNull(1) ?: senderIp
                        val senderName = parts.getOrNull(2) ?: "SyncSwift Device"
                        val senderOs = parts.getOrNull(3) ?: "Other"
                        val senderPort = parts.getOrNull(4)?.toIntOrNull() ?: 52424
                        withContext(Dispatchers.Main) {
                            net.syncswift.connection.ConnectionManager.dispatchIncomingPairRequest(
                                net.syncswift.connection.IncomingPairRequest(
                                    senderId = senderId,
                                    senderName = senderName,
                                    senderHost = senderIp,
                                    senderPort = senderPort,
                                    senderOsType = senderOs
                                )
                            )
                        }
                    } else if (msg.startsWith("SYNCSWIFT:PAIR_RESP|")) {
                        val parts = msg.split("|")
                        val senderId = parts.getOrNull(1) ?: senderIp
                        val accepted = parts.getOrNull(2) == "1" || parts.getOrNull(2)?.equals("true", ignoreCase = true) == true
                        withContext(Dispatchers.Main) {
                            net.syncswift.connection.ConnectionManager.handlePairResponse(senderIp, senderId, accepted)
                        }
                    } else if (msg.startsWith("SYNCSWIFT:DISCONNECT|")) {
                        val parts = msg.split("|")
                        val senderId = parts.getOrNull(1) ?: senderIp
                        withContext(Dispatchers.Main) {
                            net.syncswift.connection.ConnectionManager.handlePeerDisconnected(senderIp, senderId)
                        }
                    } else if (msg.startsWith("SYNCSWIFT:FILE_REQ|")) {
                        val parts = msg.split("|")
                        val senderId = parts.getOrNull(1) ?: senderIp
                        val senderName = parts.getOrNull(2) ?: "SyncSwift Device"
                        val fileName = parts.getOrNull(3) ?: "Dosya"
                        val fileSize = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                        val transferId = parts.getOrNull(5) ?: "tr_${System.currentTimeMillis()}"
                        withContext(Dispatchers.Main) {
                            net.syncswift.connection.ConnectionManager.dispatchIncomingTransferRequest(
                                net.syncswift.connection.IncomingTransferRequest(
                                    senderName = senderName,
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    mimeType = "application/octet-stream",
                                    senderHost = senderIp,
                                    senderPort = 52424,
                                    transferId = transferId
                                )
                            )
                        }
                    } else if (msg.startsWith("SYNCSWIFT:TRANSFER_RESP|")) {
                        val parts = msg.split("|")
                        val transferId = parts.getOrNull(1) ?: ""
                        val accepted = parts.getOrNull(2) == "1" || parts.getOrNull(2)?.equals("true", ignoreCase = true) == true
                        withContext(Dispatchers.Main) {
                            net.syncswift.transfer.TransferEngine.handleTransferResponse(transferId, accepted)
                        }
                    } else if (msg.startsWith("SYNCSWIFT:CLIP_REQ|")) {
                        val parts = msg.split("|")
                        val senderId = parts.getOrNull(1) ?: senderIp
                        val senderName = parts.getOrNull(2) ?: "SyncSwift Device"
                        val clipText = parts.getOrNull(3) ?: ""
                        withContext(Dispatchers.Main) {
                            net.syncswift.connection.ConnectionManager.dispatchIncomingTransferRequest(
                                net.syncswift.connection.IncomingTransferRequest(
                                    senderName = senderName,
                                    fileName = "Pano: ${clipText.take(30)}",
                                    fileSize = clipText.length.toLong(),
                                    mimeType = "text/plain",
                                    senderHost = senderIp,
                                    senderPort = 52424,
                                    downloadUrl = clipText
                                )
                            )
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal timeout, continue loop
                } catch (e: SocketException) {
                    if (!isActive) break
                } catch (e: Exception) {
                    if (!isActive) break
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            Log.e("UdpDiscovery", "Socket init error: ${e.message}")
        }
    }
}

actual fun stopPlatformUdpDiscovery() {
    discoveryJob?.cancel()
    discoveryJob = null
    try {
        activeSocket?.close()
    } catch (_: Exception) {}
    activeSocket = null
}

actual fun sendDirectUdpAnnouncement(targetHost: String, targetPort: Int) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myName = SessionManager.getMyDeviceName()
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:PAIR_ACCEPT|$myId|$myName|Android|52424"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpPairRequest(targetHost: String, targetPort: Int) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myName = SessionManager.getMyDeviceName()
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:PAIR_REQ|$myId|$myName|Android|52424"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpPairResponse(targetHost: String, targetPort: Int, accepted: Boolean) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:PAIR_RESP|$myId|${if (accepted) "1" else "0"}"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpDisconnect(targetHost: String, targetPort: Int) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:DISCONNECT|$myId"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpFileRequest(targetHost: String, targetPort: Int, fileName: String, fileSize: Long, transferId: String) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myName = SessionManager.getMyDeviceName()
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:FILE_REQ|$myId|$myName|$fileName|$fileSize|$transferId"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpTransferResponse(targetHost: String, targetPort: Int, transferId: String, accepted: Boolean) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val msg = "SYNCSWIFT:TRANSFER_RESP|$transferId|${if (accepted) "1" else "0"}"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

actual fun sendDirectUdpClipRequest(targetHost: String, targetPort: Int, previewText: String) {
    if (targetHost.isBlank() || targetHost == "127.0.0.1") return
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val myName = SessionManager.getMyDeviceName()
            val myId = SessionManager.getPlatformDeviceId()
            val msg = "SYNCSWIFT:CLIP_REQ|$myId|$myName|${previewText.replace('|', ' ').take(60)}"
            val data = msg.toByteArray()
            val addr = InetAddress.getByName(targetHost)
            val packet = DatagramPacket(data, data.size, addr, targetPort)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (_: Exception) {}
    }
}

