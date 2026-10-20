package net.syncswift

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object NetworkMonitor {
    private val _isConnected = MutableStateFlow(true)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private var isStarted = false

    fun init(context: Context) {
        if (isStarted) return
        isStarted = true
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                fun evaluate() {
                    val act = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(act)
                    val online = caps != null &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                             caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                             caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                             caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    _isConnected.value = online
                }

                evaluate()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            evaluate()
                        }
                        override fun onLost(network: Network) {
                            _isConnected.value = false
                        }
                        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                            evaluate()
                        }
                    })
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                    cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            evaluate()
                        }
                        override fun onLost(network: Network) {
                            evaluate()
                        }
                        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                            evaluate()
                        }
                    })
                }
            }
        } catch (_: Exception) {}
    }

    actual fun startMonitoring() {
        // Initialized via Application or MainActivity
    }
}
