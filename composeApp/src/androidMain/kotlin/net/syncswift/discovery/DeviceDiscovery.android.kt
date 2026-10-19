package net.syncswift.discovery

import android.content.Context
import net.syncswift.SessionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

actual object DeviceDiscoveryManager {
    private const val TAG = "SyncSwiftDiscovery"
    private const val SERVICE_TYPE = "_syncswift._tcp."
    private const val DEFAULT_PORT = 52424
    private const val UDP_DISCOVERY_PORT = 52428

    private var appContext: Context? = null
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var actualPort: Int = DEFAULT_PORT

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var selfServiceName: String = "SyncSwift-${Build.MODEL.replace(" ", "-")}"
    private var isRegistered = false
    private var isDiscovering = false

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    actual val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    actual val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Resolve queue to prevent Android NsdManager concurrent resolve failures
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private var isResolving = false

    // Fast UDP discovery & heartbeats
    private var udpSocket: DatagramSocket? = null
    private var udpListenJob: Job? = null
    private var udpHeartbeatJob: Job? = null
    private var livenessJob: Job? = null
    private val lastSeenTimestamps = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        nsdManager = appContext?.getSystemService(Context.NSD_SERVICE) as? NsdManager

        try {
            val wifiManager = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("SyncSwiftMulticastLock")?.apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock creation error: ${e.message}")
        }

        loadSavedManualDevices()
    }

    private fun loadSavedManualDevices() {
        val savedManual = SessionManager.getSavedManualDevicesJson()
        if (!savedManual.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(savedManual)
                val restored = mutableListOf<DiscoveredDevice>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val isRemote = obj.optBoolean("isRemote", false) || id.startsWith("remote-")
                    restored.add(
                        DiscoveredDevice(
                            id = id,
                            name = obj.optString("name", "Device"),
                            host = obj.optString("host", ""),
                            port = obj.optInt("port", 52424),
                            osType = obj.optString("osType", "Android"),
                            isSelf = false,
                            isOnline = false,
                            isRemote = isRemote,
                            pushToken = obj.optString("pushToken", "").ifBlank { null }
                        )
                    )
                }
                if (restored.isNotEmpty()) {
                    _devices.value = restored
                }
            } catch (_: Exception) {}
        }
    }

    fun saveManualDevices() {
        try {
            val array = org.json.JSONArray()
            for (dev in _devices.value) {
                if (dev.isSelf) continue
                val isConnected = net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
                if (!dev.id.startsWith("manual-") && !dev.id.startsWith("remote-") && !dev.isRemote && !isConnected) continue
                val obj = org.json.JSONObject().apply {
                    put("id", dev.id)
                    put("name", dev.name)
                    put("host", dev.host)
                    put("port", dev.port)
                    put("osType", dev.osType)
                    put("pushToken", dev.pushToken ?: "")
                    put("isRemote", dev.isRemote || net.syncswift.connection.ConnectionManager.isRemoteDevice(dev))
                }
                array.put(obj)
            }
            SessionManager.saveManualDevicesJson(array.toString())
        } catch (_: Exception) {}
    }

    fun markDeviceSeen(host: String, name: String? = null) {
        if (host.isBlank() && name.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        if (host.isNotBlank()) lastSeenTimestamps[host] = now
        if (!name.isNullOrBlank()) {
            lastSeenTimestamps[name] = now
            lastSeenTimestamps[cleanDeviceDisplayName(name)] = now
        }
        val current = _devices.value.toMutableList()
        var changed = false
        for (i in current.indices) {
            val dev = current[i]
            val matches = (host.isNotBlank() && dev.host == host) ||
                    (!name.isNullOrBlank() && (dev.name == name || cleanDeviceDisplayName(dev.name) == cleanDeviceDisplayName(name)))
            if (matches) {
                lastSeenTimestamps[dev.id] = now
                if (!dev.isOnline) {
                    current[i] = dev.copy(isOnline = true)
                    changed = true
                }
            }
        }
        if (changed) {
            _devices.value = current
            saveManualDevices()
        }
    }

    fun addOrUpdateConnectedDevice(device: DiscoveredDevice) {
        val current = _devices.value.toMutableList()
        val clean = cleanDeviceDisplayName(device.name)
        val now = System.currentTimeMillis()
        if (device.host.isNotBlank()) lastSeenTimestamps[device.host] = now
        lastSeenTimestamps[device.id] = now
        lastSeenTimestamps[clean] = now

        val idx = current.indexOfFirst {
            it.id == device.id ||
            (it.host.isNotBlank() && it.host == device.host) ||
            cleanDeviceDisplayName(it.name) == clean
        }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = device.copy(
                name = clean,
                isOnline = true,
                pushToken = existing.pushToken ?: device.pushToken
            )
        } else {
            current.add(0, device.copy(name = clean, isOnline = true))
        }
        _devices.value = current
        saveManualDevices()
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val b = addr.broadcast
                    if (b != null && !list.contains(b)) {
                        list.add(b)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun buildSelfAnnouncement(isPing: Boolean, isGoodbye: Boolean = false): String {
        val self = getSelfDevice()
        val id = self?.id ?: "self-${Build.MODEL}"
        val name = self?.name?.replace(":", "-") ?: Build.MODEL
        val port = actualPort
        val os = "Android"
        val model = Build.MODEL
        val prefix = when {
            isGoodbye -> "SS_BYE:v1"
            isPing -> "SS_DISC:v1"
            else -> "SS_BEAT:v1"
        }
        return "$prefix:$id:$name:$port:$os:$model"
    }

    private fun sendUdpPacket(message: String, targetAddress: InetAddress? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = message.toByteArray(Charsets.UTF_8)
                val socket = DatagramSocket()
                socket.broadcast = true
                if (targetAddress != null) {
                    val packet = DatagramPacket(bytes, bytes.size, targetAddress, UDP_DISCOVERY_PORT)
                    socket.send(packet)
                } else {
                    for (bcast in getBroadcastAddresses()) {
                        try {
                            val packet = DatagramPacket(bytes, bytes.size, bcast, UDP_DISCOVERY_PORT)
                            socket.send(packet)
                        } catch (_: Exception) {}
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "sendUdpPacket error: ${e.message}")
            }
        }
    }

    private fun handleIncomingUdpPacket(rawMessage: String, senderAddress: InetAddress) {
        val senderHost = senderAddress.hostAddress ?: return
        val localIp = getLocalIpAddress()
        if (senderHost == localIp || senderHost == "127.0.0.1") return

        val parts = rawMessage.split(":")
        if (parts.size < 6) return
        val tag = parts[0]
        val version = parts[1]
        if (version != "v1") return

        val id = parts[2]
        val name = parts[3]
        val port = parts[4].toIntOrNull() ?: DEFAULT_PORT
        val os = parts[5]
        val model = if (parts.size >= 7) parts[6] else ""

        val cleanName = cleanDeviceDisplayName(name)

        if (tag == "SS_BYE") {
            lastSeenTimestamps.remove(senderHost)
            lastSeenTimestamps.remove(id)
            lastSeenTimestamps.remove(name)
            lastSeenTimestamps.remove(cleanName)
            val current = _devices.value.toMutableList()
            val iterator = current.listIterator()
            var changed = false
            while (iterator.hasNext()) {
                val dev = iterator.next()
                val matches = (senderHost.isNotBlank() && dev.host == senderHost) ||
                        dev.id == id ||
                        cleanDeviceDisplayName(dev.name) == cleanName
                if (matches) {
                    val isConnected = net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
                    if (isConnected) {
                        iterator.set(dev.copy(isOnline = false))
                        changed = true
                    } else {
                        iterator.remove()
                        changed = true
                    }
                }
            }
            if (changed) {
                _devices.value = current
                saveManualDevices()
            }
            return
        }

        val now = System.currentTimeMillis()
        lastSeenTimestamps[senderHost] = now
        lastSeenTimestamps[id] = now
        lastSeenTimestamps[name] = now
        lastSeenTimestamps[cleanName] = now

        // If peer sent a discovery ping, reply immediately directly to them
        if (tag == "SS_DISC") {
            sendUdpPacket(buildSelfAnnouncement(isPing = false), senderAddress)
        }

        val device = DiscoveredDevice(
            id = id,
            name = cleanName,
            host = senderHost,
            port = port,
            osType = os,
            isSelf = false,
            isOnline = true,
            modelCode = model
        )

        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst {
            it.id == id ||
            (it.host.isNotBlank() && it.host == senderHost) ||
            cleanDeviceDisplayName(it.name) == cleanName
        }

        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = existing.copy(
                name = cleanName,
                host = senderHost,
                port = port,
                osType = os,
                isOnline = true,
                pushToken = existing.pushToken ?: device.pushToken
            )
        } else {
            current.add(0, device)
        }
        _devices.value = current
        saveManualDevices()
    }

    private fun startUdpDiscovery() {
        stopUdpDiscovery()

        // 1. Launch UDP Listener on UDP_DISCOVERY_PORT
        udpListenJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                }
                udpSocket = socket
                Log.d(TAG, "UDP Discovery listener active on port $UDP_DISCOVERY_PORT")

                val buffer = ByteArray(1024)
                while (isActive && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        handleIncomingUdpPacket(msg, packet.address)
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start UDP listener: ${e.message}")
            }
        }

        // 2. Launch UDP Heartbeat broadcaster (instant broadcast on start, then every 2.8s)
        udpHeartbeatJob = scope.launch(Dispatchers.IO) {
            // Instant discovery ping
            sendUdpPacket(buildSelfAnnouncement(isPing = true))
            delay(500)
            sendUdpPacket(buildSelfAnnouncement(isPing = false))

            while (isActive) {
                delay(2800)
                sendUdpPacket(buildSelfAnnouncement(isPing = false))
            }
        }
    }

    private fun stopUdpDiscovery() {
        udpHeartbeatJob?.cancel()
        udpHeartbeatJob = null

        udpListenJob?.cancel()
        udpListenJob = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
    }

    private fun startLivenessCheck() {
        livenessJob?.cancel()
        livenessJob = scope.launch {
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                val current = _devices.value.toMutableList()
                var changed = false
                val iterator = current.listIterator()
                while (iterator.hasNext()) {
                    val dev = iterator.next()
                    if (dev.isSelf || dev.isRemote || dev.host.isBlank() || dev.host.startsWith("remote-")) continue

                    val clean = cleanDeviceDisplayName(dev.name)
                    val lastSeen = lastSeenTimestamps[dev.id]
                        ?: lastSeenTimestamps[dev.host]
                        ?: lastSeenTimestamps[dev.name]
                        ?: lastSeenTimestamps[clean]
                        ?: 0L

                    val age = now - lastSeen
                    // Heartbeat is sent every 2.8s. If seen within 15s -> online
                    if (lastSeen > 0L && age < 15000L) {
                        if (!dev.isOnline) {
                            iterator.set(dev.copy(isOnline = true))
                            changed = true
                        }
                    } else if (lastSeen > 0L && age >= 15000L) {
                        val isConnected = net.syncswift.connection.ConnectionManager.isDeviceConnected(dev)
                        if (isConnected || age < 40000L) {
                            if (dev.isOnline) {
                                iterator.set(dev.copy(isOnline = false))
                                changed = true
                            }
                        } else {
                            // Non-connected nearby device not seen for 40s -> remove
                            iterator.remove()
                            changed = true
                        }
                    }
                }
                if (changed) {
                    _devices.value = current
                    saveManualDevices()
                }
            }
        }
    }

    private fun stopLivenessCheck() {
        livenessJob?.cancel()
        livenessJob = null
    }

    actual fun triggerDiscoveryPing() {
        scope.launch(Dispatchers.IO) {
            sendUdpPacket(buildSelfAnnouncement(isPing = true))
        }
    }

    actual fun startDiscovery() {
        if (appContext == null) return
        scope.launch {
            try {
                multicastLock?.acquire()
            } catch (e: Exception) {
                Log.w(TAG, "MulticastLock acquire failed: ${e.message}")
            }

            actualPort = net.syncswift.transfer.TransferEngine.getServerPort()
            startUdpDiscovery()
            registerService()
            startBrowsing()
            startLivenessCheck()
            _isScanning.value = true
        }
    }

    actual fun stopDiscovery() {
        scope.launch {
            try {
                sendUdpPacket(buildSelfAnnouncement(isPing = false, isGoodbye = true))
                delay(60)
            } catch (_: Exception) {}
            stopUdpDiscovery()
            stopLivenessCheck()
            stopBrowsing()
            unregisterService()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock?.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "MulticastLock release failed: ${e.message}")
            }
            _isScanning.value = false
        }
    }

    actual fun getSelfDevice(): DiscoveredDevice? {
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val ctx = appContext
        val customName = try {
            if (ctx != null) {
                android.provider.Settings.Global.getString(ctx.contentResolver, "device_name")
                    ?: android.provider.Settings.Secure.getString(ctx.contentResolver, "bluetooth_name")
                    ?: android.provider.Settings.System.getString(ctx.contentResolver, "device_name")
                    ?: try {
                        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        adapter?.name
                    } catch (_: Exception) { null }
            } else null
        } catch (_: Exception) { null }

        val mfg = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val displayName = when {
            !customName.isNullOrBlank() && customName != Build.MODEL -> customName
            Build.MODEL.startsWith(mfg, ignoreCase = true) -> Build.MODEL
            else -> "$mfg ${Build.MODEL}"
        }

        return DiscoveredDevice(
            id = "self-$selfServiceName",
            name = displayName,
            host = ip,
            port = actualPort,
            osType = "Android",
            isSelf = true,
            isOnline = true,
            modelCode = Build.MODEL
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
        val now = System.currentTimeMillis()
        lastSeenTimestamps[host] = now
        lastSeenTimestamps[device.id] = now
        lastSeenTimestamps[cleanDeviceDisplayName(name)] = now

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

    private fun registerService() {
        if (isRegistered || nsdManager == null) return
        try {
            actualPort = net.syncswift.transfer.TransferEngine.getServerPort()
            val selfDev = getSelfDevice()
            val preferredName = selfDev?.name?.ifBlank { Build.MODEL } ?: Build.MODEL
            selfServiceName = "SyncSwift-${preferredName.replace(" ", "-")}"

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = selfServiceName
                serviceType = SERVICE_TYPE
                port = actualPort
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    selfServiceName = NsdServiceInfo.serviceName
                    isRegistered = true
                    Log.d(TAG, "Service registered successfully as $selfServiceName on port $actualPort")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Service registration failed with error code: $errorCode")
                    isRegistered = false
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.d(TAG, "Service unregistered successfully")
                    isRegistered = false
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Service unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD service: ${e.message}")
        }
    }

    private fun unregisterService() {
        if (!isRegistered || nsdManager == null || registrationListener == null) return
        try {
            nsdManager?.unregisterService(registrationListener)
            registrationListener = null
            isRegistered = false
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD service: ${e.message}")
        }
    }

    private fun startBrowsing() {
        if (isDiscovering || nsdManager == null) return
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    isDiscovering = true
                    Log.d(TAG, "Service discovery started for type $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "Service found: ${service.serviceName} (${service.serviceType})")
                    if (service.serviceName == selfServiceName) {
                        return // Ignore self announcement
                    }
                    if (service.serviceType.contains("_syncswift") || service.serviceType.contains("_syncdrop")) {
                        enqueueResolve(service)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "NSD Service lost: ${service.serviceName}")
                    val lostName = service.serviceName
                    val cleanLost = cleanDeviceDisplayName(lostName)
                    val now = System.currentTimeMillis()
                    val lastSeen = lastSeenTimestamps[lostName] ?: lastSeenTimestamps[cleanLost] ?: 0L
                    // If UDP heartbeats have seen this device recently (<45s), ignore NSD's false service-lost event!
                    if (now - lastSeen < 45000L) {
                        Log.d(TAG, "Ignoring NSD service lost for $lostName - UDP heartbeats active")
                        return
                    }

                    val current = _devices.value.toMutableList()
                    var changed = false
                    val iterator = current.listIterator()
                    while (iterator.hasNext()) {
                        val dev = iterator.next()
                        val cleanDevName = cleanDeviceDisplayName(dev.name)
                        val matches = !dev.isRemote && (dev.id == lostName || dev.name == lostName || cleanDevName == cleanLost || lostName.contains(cleanDevName))
                        if (matches) {
                            changed = true
                            iterator.set(dev.copy(isOnline = false))
                        }
                    }
                    if (changed) {
                        _devices.value = current
                        saveManualDevices()
                    }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    isDiscovering = false
                    Log.d(TAG, "Discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Start discovery failed with code: $errorCode")
                    isDiscovering = false
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Stop discovery failed with code: $errorCode")
                }
            }

            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service discovery: ${e.message}")
        }
    }

    private fun stopBrowsing() {
        if (!isDiscovering || nsdManager == null || discoveryListener == null) return
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
            discoveryListener = null
            isDiscovering = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery: ${e.message}")
        }
    }

    private fun enqueueResolve(service: NsdServiceInfo) {
        resolveQueue.add(service)
        processNextResolve()
    }

    private fun processNextResolve() {
        if (isResolving) return
        val next = resolveQueue.poll() ?: return
        isResolving = true

        try {
            nsdManager?.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    isResolving = false
                    processNextResolve()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    isResolving = false
                    val hostAddress = serviceInfo.host?.hostAddress ?: ""
                    val localIp = getLocalIpAddress()
                    val port = serviceInfo.port
                    val rawName = serviceInfo.serviceName
                    val cleanName = cleanDeviceDisplayName(rawName)
                    val selfDev = getSelfDevice()
                    val myClean = cleanDeviceDisplayName(selfDev?.name ?: "")
                    val selfClean = cleanDeviceDisplayName(selfServiceName)
                    val isSelfService = rawName == selfServiceName ||
                            rawName.startsWith("self-") ||
                            cleanName.equals(selfClean, ignoreCase = true) ||
                            (myClean.isNotBlank() && cleanName.equals(myClean, ignoreCase = true)) ||
                            (hostAddress.isNotBlank() && (hostAddress == localIp || hostAddress == "127.0.0.1" || (selfDev != null && hostAddress == selfDev.host)))
                    if (isSelfService) {
                        Log.d(TAG, "Ignoring self device announcement: $rawName ($hostAddress)")
                        processNextResolve()
                        return
                    }

                    val now = System.currentTimeMillis()
                    if (hostAddress.isNotBlank()) lastSeenTimestamps[hostAddress] = now
                    lastSeenTimestamps[rawName] = now
                    lastSeenTimestamps[cleanName] = now

                    // Deduce OS type from name or attributes
                    val os = when {
                        cleanName.contains("Mac", ignoreCase = true) || rawName.contains("macOS", ignoreCase = true) -> "macOS"
                        cleanName.contains("iPhone", ignoreCase = true) || cleanName.contains("iPad", ignoreCase = true) -> "iOS"
                        cleanName.contains("Windows", ignoreCase = true) || cleanName.contains("PC", ignoreCase = true) -> "Windows"
                        cleanName.contains("Linux", ignoreCase = true) -> "Linux"
                        else -> "Android"
                    }

                    val device = DiscoveredDevice(
                        id = rawName,
                        name = cleanName,
                        host = hostAddress,
                        port = port,
                        osType = os,
                        isSelf = false,
                        isOnline = true
                    )

                    Log.d(TAG, "NSD Device resolved: $device")

                    // Add or update in list, preserving pushToken
                    val current = _devices.value.toMutableList()
                    val existingIdx = current.indexOfFirst {
                        it.id == device.id ||
                        (it.host == device.host && it.port == device.port) ||
                        (it.host.isNotBlank() && it.host == device.host) ||
                        cleanDeviceDisplayName(it.name) == cleanName
                    }
                    if (existingIdx >= 0) {
                        val existing = current[existingIdx]
                        current[existingIdx] = device.copy(
                            name = cleanName,
                            pushToken = existing.pushToken ?: device.pushToken
                        )
                    } else {
                        current.add(device)
                    }
                    _devices.value = current
                    saveManualDevices()

                    processNextResolve()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving service: ${e.message}")
            isResolving = false
            processNextResolve()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IP: ${e.message}")
        }
        return null
    }
}
