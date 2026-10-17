package net.syncswift.discovery

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.PlatformConfig
import net.syncswift.SessionManager
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import platform.posix.*

private fun htons(s: UShort): UShort = (((s.toInt() and 0xFF) shl 8) or ((s.toInt() ushr 8) and 0xFF)).toUShort()

private fun formatIpv4(s_addr: UInt): String {
    val b0 = (s_addr and 0xFFu).toLong()
    val b1 = ((s_addr shr 8) and 0xFFu).toLong()
    val b2 = ((s_addr shr 16) and 0xFFu).toLong()
    val b3 = ((s_addr shr 24) and 0xFFu).toLong()
    return "$b0.$b1.$b2.$b3"
}

private fun parseIpv4(ip: String): UInt? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    val b0 = parts[0].toUIntOrNull() ?: return null
    val b1 = parts[1].toUIntOrNull() ?: return null
    val b2 = parts[2].toUIntOrNull() ?: return null
    val b3 = parts[3].toUIntOrNull() ?: return null
    if (b0 > 255u || b1 > 255u || b2 > 255u || b3 > 255u) return null
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

@OptIn(ExperimentalForeignApi::class)
private class ServiceBrowserDelegate(
    private val onFound: (NSNetService) -> Unit,
    private val onLost: (NSNetService) -> Unit
) : NSObject(), NSNetServiceBrowserDelegateProtocol {
    @ObjCSignatureOverride
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        onFound(didFindService)
    }

    @ObjCSignatureOverride
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
        onLost(didRemoveService)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ServiceResolverDelegate(
    private val service: NSNetService,
    private val onResolved: (DiscoveredDevice) -> Unit
) : NSObject(), NSNetServiceDelegateProtocol {
    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val rawName = sender.name
        val cleanName = cleanDeviceDisplayName(rawName)
        val port = sender.port.toInt()
        val addresses = sender.addresses ?: return

        var hostIp: String? = null
        for (addrData in addresses) {
            val data = addrData as? NSData ?: continue
            if (data.length.toLong() >= sizeOf<sockaddr_in>().toLong()) {
                val bytes = data.bytes ?: continue
                val sa = bytes.reinterpret<sockaddr_in>().pointed
                if (sa.sin_family.toInt() == AF_INET) {
                    val ip = formatIpv4(sa.sin_addr.s_addr.toLong().toUInt())
                    if (ip.isNotBlank() && ip != "127.0.0.1" && ip != "0.0.0.0") {
                        hostIp = ip
                        break
                    }
                }
            }
        }

        if (!hostIp.isNullOrBlank()) {
            val os = when {
                cleanName.contains("Mac", ignoreCase = true) || rawName.contains("macOS", ignoreCase = true) -> "macOS"
                cleanName.contains("iPhone", ignoreCase = true) || cleanName.contains("iPad", ignoreCase = true) -> "iOS"
                cleanName.contains("Windows", ignoreCase = true) || cleanName.contains("PC", ignoreCase = true) -> "Windows"
                cleanName.contains("Linux", ignoreCase = true) -> "Linux"
                else -> "Android"
            }
            val dev = DiscoveredDevice(
                id = rawName,
                name = cleanName,
                host = hostIp,
                port = if (port > 0) port else 52424,
                osType = os,
                isSelf = false,
                isOnline = true
            )
            onResolved(dev)
        }
    }
}

actual object DeviceDiscoveryManager {
    private const val SERVICE_TYPE = "_syncswift._tcp."
    private const val DEFAULT_PORT = 52424

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("[SyncSwift iOS Discovery] Coroutine error caught: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var browserDelegate: ServiceBrowserDelegate? = null
    private val resolvingServices = mutableMapOf<String, Pair<NSNetService, ServiceResolverDelegate>>()

    private val selfServiceName: String
        get() = "SyncSwift-${UIDevice.currentDevice.name.replace(" ", "-")}"

    init {
        loadSavedManualDevices()
    }

    private fun loadSavedManualDevices() {
        val saved = SessionManager.getSavedManualDevicesJson() ?: return
        try {
            val regex = Regex("""\{"id":"([^"]*)","name":"([^"]*)","host":"([^"]*)","port":(\d+),"osType":"([^"]*)"(?:,"isRemote":(true|false))?""")
            val restored = regex.findAll(saved).mapNotNull { match ->
                val (id, name, host, port, osType, isRemoteStr) = match.destructured
                val isRemote = isRemoteStr == "true" || id.startsWith("remote-")
                DiscoveredDevice(
                    id = id,
                    name = name,
                    host = host,
                    port = port.toIntOrNull() ?: DEFAULT_PORT,
                    osType = osType,
                    isSelf = false,
                    isOnline = false,
                    isRemote = isRemote
                )
            }.toList()
            if (restored.isNotEmpty()) {
                _devices.value = restored
            }
        } catch (_: Exception) {}
    }

    fun saveManualDevices() {
        try {
            val list = _devices.value.filter { dev ->
                !dev.isSelf && (dev.id.startsWith("manual-") || dev.id.startsWith("remote-") || dev.isRemote || net.syncswift.connection.ConnectionManager.isDeviceConnected(dev))
            }
            val sb = StringBuilder("[")
            list.forEachIndexed { idx, dev ->
                val isRemote = dev.isRemote || net.syncswift.connection.ConnectionManager.isRemoteDevice(dev)
                sb.append("""{"id":"${dev.id}","name":"${dev.name}","host":"${dev.host}","port":${dev.port},"osType":"${dev.osType}","isRemote":$isRemote}""")
                if (idx < list.size - 1) sb.append(",")
            }
            sb.append("]")
            SessionManager.saveManualDevicesJson(sb.toString())
        } catch (_: Exception) {}
    }

    fun addOrUpdateConnectedDevice(device: DiscoveredDevice) {
        val current = _devices.value.toMutableList()
        val clean = cleanDeviceDisplayName(device.name)
        val idx = current.indexOfFirst {
            it.id == device.id ||
            (it.host.isNotBlank() && it.host == device.host) ||
            cleanDeviceDisplayName(it.name) == clean
        }
        if (idx >= 0) {
            current[idx] = device.copy(name = clean)
        } else {
            current.add(0, device.copy(name = clean))
        }
        _devices.value = current
        saveManualDevices()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true

        // Ensure foreground transfer server is listening
        net.syncswift.transfer.TransferEngine.startForegroundServer()

        // Sync local iOS device presence & push token to Supabase
        val savedPushToken = net.syncswift.getSavedIosPushToken()
        if (!savedPushToken.isNullOrBlank()) {
            net.syncswift.connection.ConnectionManager.syncDeviceWithSupabase(savedPushToken)
        }

        scope.launch(Dispatchers.Main) {
            try {
                // 1. Publish our own Bonjour service on main runloop
                val service = NSNetService(
                    domain = "local.",
                    type = SERVICE_TYPE,
                    name = selfServiceName,
                    port = DEFAULT_PORT
                )
                netService = service
                service.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
                service.publish()

                // 2. Start browsing for other devices on main runloop
                val b = NSNetServiceBrowser()
                browser = b
                val bDelegate = ServiceBrowserDelegate(
                    onFound = { foundService ->
                        if (foundService.name == selfServiceName) return@ServiceBrowserDelegate
                        val resolverDelegate = ServiceResolverDelegate(foundService) { resolvedDev ->
                            addOrUpdateDevice(resolvedDev)
                        }
                        resolvingServices[foundService.name] = foundService to resolverDelegate
                        foundService.delegate = resolverDelegate
                        foundService.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
                        foundService.resolveWithTimeout(6.0)
                    },
                    onLost = { lostService ->
                        resolvingServices.remove(lostService.name)
                        val lostName = cleanDeviceDisplayName(lostService.name)
                        val current = _devices.value.toMutableList()
                        var changed = false
                        val iterator = current.listIterator()
                        while (iterator.hasNext()) {
                            val dev = iterator.next()
                            val cleanDevName = cleanDeviceDisplayName(dev.name)
                            val matches = !dev.isRemote && (dev.name == lostName || dev.id == lostService.name || cleanDevName == lostName)
                            if (matches) {
                                changed = true
                                val isConnected = net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
                                if (isConnected) {
                                    iterator.set(dev.copy(isOnline = false))
                                } else {
                                    iterator.remove()
                                }
                            }
                        }
                        if (changed) {
                            _devices.value = current
                            saveManualDevices()
                        }
                    }
                )
                browserDelegate = bDelegate
                b.delegate = bDelegate
                b.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
                b.searchForServicesOfType(SERVICE_TYPE, inDomain = "local.")
            } catch (e: Exception) {
                println("[SyncSwift iOS Discovery] startDiscovery error: ${e.message}")
            }
        }
    }

    actual fun stopDiscovery() {
        scope.launch(Dispatchers.Main) {
            try {
                browser?.stop()
                browser = null
                browserDelegate = null

                netService?.stop()
                netService = null

                resolvingServices.clear()
                _isScanning.value = false
            } catch (e: Exception) {
                println("[SyncSwift iOS Discovery] stopDiscovery error: ${e.message}")
            }
        }
    }

    private fun addOrUpdateDevice(device: DiscoveredDevice) {
        val selfIp = getLocalWifiIp()
        if (device.host == selfIp || device.host == "127.0.0.1") return

        val current = _devices.value.toMutableList()
        val clean = cleanDeviceDisplayName(device.name)
        val idx = current.indexOfFirst {
            it.id == device.id ||
            it.host == device.host ||
            cleanDeviceDisplayName(it.name) == clean
        }
        if (idx >= 0) {
            current[idx] = device.copy(name = clean, isOnline = true)
        } else {
            current.add(device.copy(name = clean, isOnline = true))
        }
        _devices.value = current
        saveManualDevices()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getSelfDevice(): DiscoveredDevice? {
        val ip = getLocalWifiIp() ?: "127.0.0.1"
        return DiscoveredDevice(
            id = "self-$selfServiceName",
            name = UIDevice.currentDevice.name,
            host = ip,
            port = DEFAULT_PORT,
            osType = "iOS",
            isSelf = true,
            isOnline = true,
            pushToken = net.syncswift.getSavedIosPushToken(),
            modelCode = UIDevice.currentDevice.model
        )
    }

    actual fun addManualDevice(host: String, port: Int, name: String, osType: String): DiscoveredDevice {
        val device = DiscoveredDevice(
            id = "manual-$host:$port",
            name = name,
            host = host,
            port = port,
            osType = osType,
            isSelf = false,
            isOnline = true
        )
        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst { it.host == host && it.port == port }
        if (idx >= 0) {
            current[idx] = device
        } else {
            current.add(0, device)
        }
        _devices.value = current
        saveManualDevices()
        return device
    }

    actual fun removeManualDevice(host: String) {
        if (host.isBlank()) return
        val clean = cleanDeviceDisplayName(host)
        val current = _devices.value.toMutableList()
        current.removeAll {
            it.host == host ||
            it.id == host ||
            it.id == "manual-$host" ||
            it.id.contains(host) ||
            it.name == host ||
            (clean.isNotBlank() && cleanDeviceDisplayName(it.name) == clean)
        }
        _devices.value = current
        saveManualDevices()
    }

    actual fun triggerDiscoveryPing() {
        // iOS Bonjour browser continuously scans and resolves in background
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getLocalWifiIp(): String? {
        val sock = socket(AF_INET, SOCK_DGRAM, 0)
        if (sock < 0) return null
        return try {
            memScoped {
                val destAddr = alloc<sockaddr_in>()
                destAddr.sin_family = AF_INET.convert()
                destAddr.sin_port = htons(53.toUShort())
                destAddr.sin_addr.s_addr = 0x08080808u.convert()

                if (connect(sock, destAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                    close(sock)
                    return@memScoped null
                }

                val localAddr = alloc<sockaddr_in>()
                val addrLen = alloc<socklen_tVar>()
                addrLen.value = sizeOf<sockaddr_in>().convert()

                if (getsockname(sock, localAddr.ptr.reinterpret(), addrLen.ptr) != 0) {
                    close(sock)
                    return@memScoped null
                }

                close(sock)
                val ipStr = formatIpv4(localAddr.sin_addr.s_addr.toLong().toUInt())
                if (ipStr.isBlank() || ipStr == "0.0.0.0" || ipStr == "127.0.0.1") null else ipStr
            }
        } catch (_: Exception) {
            close(sock)
            null
        }
    }
}

