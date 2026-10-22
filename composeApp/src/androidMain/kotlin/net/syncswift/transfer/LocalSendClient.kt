package net.syncswift.transfer

import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import net.syncswift.SessionManager
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.currentAppCopy
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.showNativeToast
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * HTTP REST Client for LocalSend architecture.
 * Directly sends files, clipboard, pairing, and unpairing over LAN at full Wi-Fi speeds with ZERO approval prompts.
 */
object LocalSendClient {
    private const val TAG = "LocalSendClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun cleanHost(rawHost: String): String {
        return rawHost.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
            .trim()
    }

    fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val transferId = UUID.randomUUID().toString()
        val copy = currentAppCopy()

        // 1. Check Pro file size limit
        if (!net.syncswift.subscription.SubscriptionManager.canTransferFileSize(fileBytes.size.toLong())) {
            net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                net.syncswift.subscription.PaywallReason.FILE_SIZE_LIMIT
            )
            val msg = copy.fileSizeCapExceeded
            showNativeToast(msg)
            onError(msg)
            return
        }

        // 2. Immediately register in transfers list (ZERO confirmation wait)
        val item = TransferItem(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = fileBytes.size.toLong(),
            mimeType = mimeType,
            bytesTransferred = 0,
            isSent = true,
            peerName = targetDeviceName,
            status = TransferStatus.IN_PROGRESS,
            timestamp = System.currentTimeMillis()
        )
        TransferEngine.recordTransfer(item)
        showNativeToast("${copy.transferringFile} ($fileName)")

        scope.launch {
            var conn: HttpURLConnection? = null
            try {
                val clean = cleanHost(targetHost)
                val effectivePort = if (targetPort > 0) targetPort else LocalSendServer.DEFAULT_PORT
                val url = URL("http://$clean:$effectivePort/api/file")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 180_000 // 3 minutes for huge files
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("X-File-Name", URLEncoder.encode(fileName, "UTF-8"))
                    setRequestProperty("X-File-Size", "${fileBytes.size}")
                    setRequestProperty("X-Mime-Type", mimeType)
                    val selfName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())
                    setRequestProperty("X-Sender-Name", URLEncoder.encode(selfName, "UTF-8"))
                    setFixedLengthStreamingMode(fileBytes.size)
                }

                val out: OutputStream = conn.outputStream
                // 256 KB streaming buffer for 100+ MB/s Wi-Fi transfer
                val chunkSize = 256 * 1024
                var bytesSent = 0
                val totalBytes = fileBytes.size

                while (bytesSent < totalBytes && isActive) {
                    val toSend = minOf(chunkSize, totalBytes - bytesSent)
                    out.write(fileBytes, bytesSent, toSend)
                    bytesSent += toSend
                    TransferEngine.updateTransferProgress(transferId, bytesSent.toLong(), TransferStatus.IN_PROGRESS)
                }
                out.flush()
                out.close()

                val code = conn.responseCode
                if (code == 200) {
                    TransferEngine.updateTransferProgress(transferId, totalBytes.toLong(), TransferStatus.COMPLETED)
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.fileSentSuccessfully.replace("%s", fileName))
                        onSuccess()
                    }
                } else {
                    TransferEngine.updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.transferFailed)
                        onError("HTTP $code")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "File transfer error to $targetHost:$targetPort: ${e.message}")
                TransferEngine.updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.transferFailed)
                    onError(e.message ?: "Transfer error")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val copy = currentAppCopy()
        showNativeToast(copy.syncClipboardAction)

        scope.launch {
            var conn: HttpURLConnection? = null
            try {
                val clean = cleanHost(targetHost)
                val effectivePort = if (targetPort > 0) targetPort else LocalSendServer.DEFAULT_PORT
                val url = URL("http://$clean:$effectivePort/api/clipboard")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 6000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val selfName = cleanDeviceDisplayName(SessionManager.getMyDeviceName())
                    setRequestProperty("X-Sender-Name", URLEncoder.encode(selfName, "UTF-8"))
                }

                val json = JSONObject().apply { put("text", text) }
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(bytes); it.flush() }

                val code = conn.responseCode
                if (code == 200) {
                    val item = TransferItem(
                        id = UUID.randomUUID().toString(),
                        fileName = "${copy.clipboard}: ${text.take(30).replace("\n", " ")}",
                        fileSizeBytes = text.toByteArray().size.toLong(),
                        mimeType = "text/plain",
                        bytesTransferred = text.toByteArray().size.toLong(),
                        isSent = true,
                        peerName = targetDeviceName,
                        status = TransferStatus.COMPLETED,
                        timestamp = System.currentTimeMillis()
                    )
                    TransferEngine.recordTransfer(item)
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.operationRejectedOrTimeout)
                        onError("HTTP $code")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending clipboard: ${e.message}")
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.operationRejectedOrTimeout)
                    onError(e.message ?: "Clipboard error")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun sendPair(
        targetHost: String,
        targetPort: Int,
        targetDevice: DiscoveredDevice,
        onResult: (PairResult) -> Unit
    ) {
        scope.launch {
            var conn: HttpURLConnection? = null
            try {
                val clean = cleanHost(targetHost)
                val effectivePort = if (targetPort > 0) targetPort else LocalSendServer.DEFAULT_PORT
                val url = URL("http://$clean:$effectivePort/api/pair")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 3500
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val self = DeviceDiscoveryManager.getSelfDevice()
                val json = JSONObject().apply {
                    put("id", self?.id ?: SessionManager.getPlatformDeviceId())
                    put("name", cleanDeviceDisplayName(self?.name ?: Build.MODEL))
                    put("host", self?.host ?: "")
                    put("port", LocalSendServer.getPort())
                    put("os", "Android")
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.flush() }

                val code = conn.responseCode
                if (code == 200) {
                    val key = "$targetHost:$effectivePort"
                    ConnectionManager.markDeviceConnected(key)
                    ConnectionManager.markDeviceConnected(targetHost)
                    ConnectionManager.markDeviceConnected(targetDevice.id)
                    ConnectionManager.markDeviceConnected(targetDevice.name)
                    ConnectionManager.markDeviceConnected(cleanDeviceDisplayName(targetDevice.name))
                    DeviceDiscoveryManager.addOrUpdateConnectedDevice(targetDevice.copy(isOnline = true))
                    DeviceDiscoveryManager.saveManualDevices()

                    withContext(Dispatchers.Main) {
                        showNativeToast(currentAppCopy().connectionAccepted)
                        onResult(PairResult.ACCEPTED)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(PairResult.UNAVAILABLE)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pairing via HTTP with $targetHost: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(PairResult.UNAVAILABLE)
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun sendUnpair(targetHost: String, targetPort: Int, targetDevice: DiscoveredDevice) {
        scope.launch {
            var conn: HttpURLConnection? = null
            try {
                val clean = cleanHost(targetHost)
                val effectivePort = if (targetPort > 0) targetPort else LocalSendServer.DEFAULT_PORT
                val url = URL("http://$clean:$effectivePort/api/unpair")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 2500
                    readTimeout = 3000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val self = DeviceDiscoveryManager.getSelfDevice()
                val json = JSONObject().apply {
                    put("id", self?.id ?: SessionManager.getPlatformDeviceId())
                    put("host", self?.host ?: "")
                    put("name", cleanDeviceDisplayName(self?.name ?: Build.MODEL))
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.flush() }
                conn.responseCode
            } catch (e: Exception) {
                Log.d(TAG, "Unpair notification ignored/offline: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
    }

    suspend fun ping(targetHost: String, targetPort: Int): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val clean = cleanHost(targetHost)
            val effectivePort = if (targetPort > 0) targetPort else LocalSendServer.DEFAULT_PORT
            val url = URL("http://$clean:$effectivePort/api/ping")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
