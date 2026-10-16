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

@OptIn(ExperimentalForeignApi::class)
private class ServiceBrowserDelegate(
    private val onFound: (NSNetService) -> Unit,
    private val onLost: (NSNetService) -> Unit
) : NSObject(), NSNetServiceBrowserDelegateProtocol {
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        onFound(didFindService)
    }

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
                    val buf = ByteArray(INET_ADDRSTRLEN)
                    buf.usePinned { pinned ->
                        inet_ntop(AF_INET, sa.sin_addr.ptr, pinned.addressOf(0), INET_ADDRSTRLEN.convert())
                    }
                    val ip = buf.toKString()
                    if (ip.isNotBlank() && ip != "127.0.0.1") {
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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
        // Parse simple json records
        try {
            val regex = Regex("""\{"id":"([^"]*)","name":"([^"]*)","host":"([^"]*)","port":(\d+),"osType":"([^"]*)"""")
            val restored = regex.findAll(saved).map { match ->
                val (id, name, host, port, osType) = match.destructured
                DiscoveredDevice(
                    id = id,
                    name = name,
                    host = host,
                    port = port.toIntOrNull() ?: DEFAULT_PORT,
                    osType = osType,
                    isSelf = false,
                    isOnline = false
                )
            }.toList()
            if (restored.isNotEmpty()) {
                _devices.value = restored
            }
        } catch (_: Exception) {}
    }

    private fun saveManualDevices() {
        try {
            val list = _devices.value.filter { !it.isSelf }
            val sb = StringBuilder("[")
            list.forEachIndexed { idx, dev ->
                sb.append("""{"id":"${dev.id}","name":"${dev.name}","host":"${dev.host}","port":${dev.port},"osType":"${dev.osType}"}""")
                if (idx < list.size - 1) sb.append(",")
            }
            sb.append("]")
            SessionManager.saveManualDevicesJson(sb.toString())
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true

        scope.launch(Dispatchers.Default) {
            try {
                // 1. Publish our own Bonjour service
                val service = NSNetService(
                    domain = "local.",
                    type = SERVICE_TYPE,
                    name = selfServiceName,
                    port = DEFAULT_PORT
                )
                netService = service
                service.publish()

                // 2. Start browsing for other devices
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
                        foundService.resolveWithTimeout(6.0)
                    },
                    onLost = { lostService ->
                        resolvingServices.remove(lostService.name)
                        val lostName = cleanDeviceDisplayName(lostService.name)
                        _devices.value = _devices.value.map { dev ->
                            if (dev.name == lostName || dev.id == lostService.name) {
                                dev.copy(isOnline = false)
                            } else dev
                        }
                    }
                )
                browserDelegate = bDelegate
                b.delegate = bDelegate
                b.searchForServicesOfType(SERVICE_TYPE, inDomain = "local.")

                // 3. Fallback: Sync with Supabase on same subnet
                syncOfflineDevicesFromCloud()
            } catch (e: Exception) {
                println("[SyncSwift iOS Discovery] startDiscovery error: ${e.message}")
            }
        }
    }

    actual fun stopDiscovery() {
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
            isOnline = true
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

    @OptIn(ExperimentalForeignApi::class)
    private fun getLocalWifiIp(): String? {
        memScoped {
            val ifap = alloc<CPointerVar<ifaddrs>>()
            if (getifaddrs(ifap.ptr) != 0) return null
            var curr = ifap.value
            var ip: String? = null
            while (curr != null) {
                val ifa = curr.pointed
                val name = ifa.ifa_name?.toKString() ?: ""
                val family = ifa.ifa_addr?.pointed?.sa_family?.toInt()
                if (name == "en0" && family == AF_INET) {
                    val saIn = ifa.ifa_addr!!.reinterpret<sockaddr_in>().pointed
                    val addrBuf = ByteArray(INET_ADDRSTRLEN)
                    addrBuf.usePinned { pinned ->
                        inet_ntop(AF_INET, saIn.sin_addr.ptr, pinned.addressOf(0), INET_ADDRSTRLEN.convert())
                    }
                    val found = addrBuf.toKString()
                    if (found.isNotBlank() && found != "0.0.0.0") {
                        ip = found
                        break
                    }
                }
                curr = ifa.ifa_next
            }
            freeifaddrs(ifap.value)
            return ip
        }
    }

    private fun syncOfflineDevicesFromCloud() {
        val ip = getLocalWifiIp() ?: return
        val subnet = if (ip.contains(".")) ip.substringBeforeLast(".") else return
        val supabaseUrl = PlatformConfig.supabaseUrl
        val anonKey = PlatformConfig.supabaseAnonKey
        val url = NSURL.URLWithString("$supabaseUrl/rest/v1/devices?local_ip=like.${subnet}.*&select=*") ?: return

        val req = NSMutableURLRequest.requestWithURL(url)
        req.setHTTPMethod("GET")
        req.setValue(anonKey, forHTTPHeaderField = "apikey")
        req.setValue("Bearer $anonKey", forHTTPHeaderField = "Authorization")

        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, error ->
            if (error == null && data != null) {
                val jsonString = NSString.create(data, NSUTF8StringEncoding).toString()
                try {
                    val deviceRegex = Regex("""\{"id":"([^"]*)","device_name":"([^"]*)","local_ip":"([^"]*)","port":(\d+),"device_type":"([^"]*)"""")
                    val matches = deviceRegex.findAll(jsonString)
                    val current = _devices.value.toMutableList()
                    val selfName = UIDevice.currentDevice.name

                    for (m in matches) {
                        val (devId, devName, devHost, devPort, devType) = m.destructured
                        if (devHost == ip || devName == selfName) continue
                        val clean = cleanDeviceDisplayName(devName)
                        val portInt = devPort.toIntOrNull() ?: DEFAULT_PORT

                        val exists = current.indexOfFirst { it.host == devHost && it.port == portInt }
                        if (exists < 0) {
                            current.add(
                                DiscoveredDevice(
                                    id = devId,
                                    name = clean,
                                    host = devHost,
                                    port = portInt,
                                    osType = devType,
                                    isSelf = false,
                                    isOnline = false
                                )
                            )
                        }
                    }
                    _devices.value = current
                    saveManualDevices()
                } catch (_: Exception) {}
            }
        }
        task.resume()
    }
}
