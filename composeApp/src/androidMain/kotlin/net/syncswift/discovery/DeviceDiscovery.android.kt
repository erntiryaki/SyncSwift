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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue

actual object DeviceDiscoveryManager {
    private const val TAG = "SyncSwiftDiscovery"
    private const val SERVICE_TYPE = "_syncswift._tcp."
    private const val DEFAULT_PORT = 52424

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
                    // Only restore manual / remote devices, never local ghosts
                    if (id.startsWith("manual-") || id.startsWith("remote-")) {
                        restored.add(
                            DiscoveredDevice(
                                id = id,
                                name = obj.optString("name", "Device"),
                                host = obj.optString("host", ""),
                                port = obj.optInt("port", 52424),
                                osType = obj.optString("osType", "Android"),
                                isSelf = false,
                                isOnline = false,
                                isRemote = id.startsWith("remote-"),
                                pushToken = obj.optString("pushToken", "").ifBlank { null }
                            )
                        )
                    }
                }
                if (restored.isNotEmpty()) {
                    _devices.value = restored
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveManualDevices() {
        try {
            val array = org.json.JSONArray()
            for (dev in _devices.value) {
                if (dev.isSelf) continue
                // Only save manually added or remote devices
                if (!dev.id.startsWith("manual-") && !dev.id.startsWith("remote-")) continue
                val obj = org.json.JSONObject().apply {
                    put("id", dev.id)
                    put("name", dev.name)
                    put("host", dev.host)
                    put("port", dev.port)
                    put("osType", dev.osType)
                    put("pushToken", dev.pushToken ?: "")
                }
                array.put(obj)
            }
            SessionManager.saveManualDevicesJson(array.toString())
        } catch (_: Exception) {}
    }

    actual fun startDiscovery() {
        if (appContext == null) return
        scope.launch {
            try {
                multicastLock?.acquire()
            } catch (e: Exception) {
                Log.w(TAG, "MulticastLock acquire failed: ${e.message}")
            }

            registerService()
            startBrowsing()
            _isScanning.value = true
        }
    }

    actual fun stopDiscovery() {
        scope.launch {
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
        val current = _devices.value.toMutableList()
        current.removeAll { it.host == host || it.id.contains(host) }
        _devices.value = current
        saveManualDevices()
    }

    private fun registerService() {
        if (isRegistered || nsdManager == null) return
        try {
            actualPort = net.syncswift.transfer.TransferEngine.getServerPort()

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
                    Log.d(TAG, "Service lost: ${service.serviceName}")
                    val lostName = service.serviceName
                    val cleanLost = cleanDeviceDisplayName(lostName)
                    _devices.value = _devices.value.filterNot { dev ->
                        val cleanDevName = cleanDeviceDisplayName(dev.name)
                        !dev.isRemote && (dev.id == lostName || dev.name == lostName || cleanDevName == cleanLost || lostName.contains(cleanDevName))
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
                    if (hostAddress.isNotBlank() && (hostAddress == localIp || hostAddress == "127.0.0.1")) {
                        Log.d(TAG, "Ignoring self device announcement from $hostAddress")
                        processNextResolve()
                        return
                    }

                    val port = serviceInfo.port
                    val rawName = serviceInfo.serviceName
                    val cleanName = cleanDeviceDisplayName(rawName)

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

                    Log.d(TAG, "Device resolved: $device")

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
