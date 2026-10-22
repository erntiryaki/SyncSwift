package net.syncswift.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.syncswift.SessionManager
import net.syncswift.connection.ConnectionManager
import net.syncswift.currentAppCopy
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.transfer.TransferEngine

object AndroidClipboardWatcher {
    private const val TAG = "ClipboardWatcher"
    private var clipboardManager: ClipboardManager? = null
    private var isListening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastObservedClipHash: Int = 0
    private var lastObservedTime: Long = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    fun init(context: Context) {
        if (clipboardManager == null) {
            clipboardManager = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        }
    }

    fun startListening() {
        if (isListening || clipboardManager == null) return
        try {
            clipboardManager?.addPrimaryClipChangedListener(clipListener)
            isListening = true
            Log.d(TAG, "Started clipboard change listener")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register clip listener: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isListening || clipboardManager == null) return
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipListener)
            isListening = false
            Log.d(TAG, "Stopped clipboard change listener")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister clip listener: ${e.message}")
        }
    }

    fun markClipHandled(text: String) {
        lastObservedClipHash = text.hashCode()
        lastObservedTime = System.currentTimeMillis()
    }

    private fun handleClipboardChange() {
        if (!SessionManager.isAutoTransferEnabled()) return

        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0)?.text?.toString() ?: return
        if (text.isBlank()) return

        val hash = text.hashCode()
        val now = System.currentTimeMillis()
        if (hash == lastObservedClipHash && (now - lastObservedTime < 6000L)) {
            return
        }
        lastObservedClipHash = hash
        lastObservedTime = now

        scope.launch(Dispatchers.IO) {
            val connectedKeys = ConnectionManager.connectedDeviceKeys.value + ConnectionManager.remoteConnectedDeviceKeys.value
            if (connectedKeys.isEmpty()) return@launch

            val devices = DeviceDiscoveryManager.devices.value
            val targetDevices = devices.filter { dev ->
                !dev.isSelf && ConnectionManager.isSenderMyDevice(dev.host, dev.port, dev.name, dev.id)
            }

            if (targetDevices.isEmpty()) return@launch

            for (dev in targetDevices) {
                TransferEngine.sendClipboard(
                    targetHost = dev.host,
                    targetPort = dev.port,
                    targetDeviceName = dev.name,
                    text = text,
                    onSuccess = {
                        Log.d(TAG, "Auto-sent clipboard to ${dev.name}")
                    },
                    onError = { err ->
                        Log.w(TAG, "Failed auto-sending clipboard to ${dev.name}: $err")
                    }
                )
            }
        }
    }
}
