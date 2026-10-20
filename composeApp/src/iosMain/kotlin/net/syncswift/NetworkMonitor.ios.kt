package net.syncswift

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.*

actual object NetworkMonitor {
    private val _isConnected = MutableStateFlow(true)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isStarted = false

    actual fun startMonitoring() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            while (isActive) {
                checkConnection()
                delay(4000)
            }
        }
    }

    private fun checkConnection() {
        val url = NSURL.URLWithString("https://api.syncswift.net") ?: return
        val req = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("HEAD")
            setTimeoutInterval(3.0)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, error ->
            val code = (resp as? NSHTTPURLResponse)?.statusCode?.toInt() ?: -1
            val available = (error == null && code > 0)
            _isConnected.value = available
        }
        task.resume()
    }
}
