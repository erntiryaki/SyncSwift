package net.syncswift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual object NetworkMonitor {
    private val _isConnected = MutableStateFlow(true)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private var isStarted = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var offlineDebounceJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null

    private fun checkActiveNetworkOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val act = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(act) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private fun handleNetworkAvailable() {
        offlineDebounceJob?.cancel()
        offlineDebounceJob = null
        if (checkActiveNetworkOnline()) {
            _isConnected.value = true
        }
    }

    private fun handleNetworkLost() {
        // If another network is active (e.g. switched to mobile data or secondary interface), stay online
        if (checkActiveNetworkOnline()) {
            offlineDebounceJob?.cancel()
            offlineDebounceJob = null
            _isConnected.value = true
            return
        }

        // Give a 2.5s debounce grace period before reporting offline (handles screen off/on radio sleeps & Wi-Fi re-handshake)
        if (offlineDebounceJob?.isActive != true) {
            offlineDebounceJob = scope.launch {
                delay(2500L)
                val online = checkActiveNetworkOnline()
                _isConnected.value = online
            }
        }
    }

    fun init(context: Context) {
        if (isStarted) return
        isStarted = true
        try {
            val appCtx = context.applicationContext
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager = cm

            if (cm != null) {
                _isConnected.value = checkActiveNetworkOnline()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            handleNetworkAvailable()
                        }
                        override fun onLost(network: Network) {
                            handleNetworkLost()
                        }
                        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                            handleNetworkAvailable()
                        }
                    })
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                    cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            handleNetworkAvailable()
                        }
                        override fun onLost(network: Network) {
                            handleNetworkLost()
                        }
                        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                            handleNetworkAvailable()
                        }
                    })
                }

                // Screen on / wake listener to cancel false offline drops during screen sleep
                val screenReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_SCREEN_ON) {
                            offlineDebounceJob?.cancel()
                            offlineDebounceJob = null
                            scope.launch {
                                delay(600L)
                                if (checkActiveNetworkOnline()) {
                                    _isConnected.value = true
                                }
                            }
                        }
                    }
                }
                val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
                appCtx.registerReceiver(screenReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    actual fun startMonitoring() {
        // Initialized via Application or MainActivity
    }
}
