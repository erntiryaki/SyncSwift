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
                    if (msg.startsWith("SYNCSWIFT:ANNOUNCE|")) {
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
