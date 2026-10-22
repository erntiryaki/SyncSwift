package net.syncswift.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import net.syncswift.SessionManager
import net.syncswift.connection.ConnectionManager
import net.syncswift.currentAppCopy
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.DiscoveredDevice
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.showNativeToast
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

/**
 * Embedded HTTP REST Server for high-speed zero-prompt LAN transfers (LocalSend architecture).
 * Listens on port 52424 (or 52425 as fallback).
 */
object LocalSendServer {
    private const val TAG = "LocalSendServer"
    const val DEFAULT_PORT = 52424
    private const val FALLBACK_PORT = 52425

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var actualPort = DEFAULT_PORT

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = try {
                    ServerSocket(DEFAULT_PORT).also { actualPort = DEFAULT_PORT }
                } catch (e: Exception) {
                    Log.w(TAG, "Port $DEFAULT_PORT busy, trying fallback $FALLBACK_PORT: ${e.message}")
                    ServerSocket(FALLBACK_PORT).also { actualPort = FALLBACK_PORT }
                }
                serverSocket?.reuseAddress = true
                Log.i(TAG, "LocalSendServer listening on port $actualPort")

                while (isActive && isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        scope.launch {
                            handleClient(context, client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                        Log.e(TAG, "Server accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server startup error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun getPort(): Int = actualPort

    private fun handleClient(context: Context, socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: ""
        try {
            socket.soTimeout = 120_000 // 2 minutes timeout for large files
            val rawIn = socket.getInputStream()
            val rawOut = socket.getOutputStream()

            val requestLine = readHeaderLine(rawIn) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            // Read headers byte-by-byte so rawIn remains exactly at the first byte of payload
            val headers = mutableMapOf<String, String>()
            var line = readHeaderLine(rawIn)
            while (!line.isNullOrBlank()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
                line = readHeaderLine(rawIn)
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

            when {
                // 0. OPTIONS - CORS PREFLIGHT (REQUIRED FOR TAURI / BROWSER / DESKTOP WEBVIEW)
                method == "OPTIONS" -> {
                    val response = "HTTP/1.1 204 No Content\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: *\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    rawOut.write(response.toByteArray(Charsets.UTF_8))
                    rawOut.flush()
                }

                // 1. GET /api/ping
                method == "GET" && path.endsWith("/api/ping") -> {
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val json = JSONObject().apply {
                        put("status", "ok")
                        put("id", self?.id ?: SessionManager.getPlatformDeviceId())
                        put("name", self?.name ?: Build.MODEL)
                        put("os", "Android")
                        put("port", actualPort)
                    }
                    sendJsonResponse(rawOut, 200, json.toString())
                }

                // 2. POST /api/pair - ZERO CONFIRMATION
                method == "POST" && path.endsWith("/api/pair") -> {
                    val bodyStr = readExactString(rawIn, contentLength.toInt())
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                    val senderId = json.optString("id", "")
                    val senderName = json.optString("name", "SyncSwift Device")
                    val senderHost = json.optString("host", clientIp).ifBlank { clientIp }
                    val senderPort = json.optInt("port", DEFAULT_PORT)
                    val senderOs = json.optString("os", "Mobile")

                    val clean = cleanDeviceDisplayName(senderName)
                    ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                    ConnectionManager.markDeviceConnected(senderHost)
                    if (senderId.isNotBlank()) ConnectionManager.markDeviceConnected(senderId)
                    ConnectionManager.markDeviceConnected(senderName)
                    ConnectionManager.markDeviceConnected(clean)

                    val peerDev = DiscoveredDevice(
                        id = senderId.ifBlank { "dev-$senderHost" },
                        name = clean,
                        host = senderHost,
                        port = senderPort,
                        osType = senderOs,
                        isSelf = false,
                        isOnline = true
                    )
                    DeviceDiscoveryManager.addOrUpdateConnectedDevice(peerDev)
                    DeviceDiscoveryManager.saveManualDevices()

                    Handler(Looper.getMainLooper()).post {
                        val copy = currentAppCopy()
                        showNativeToast(copy.connectionAccepted)
                    }

                    val resp = JSONObject().apply {
                        put("status", "accepted")
                        put("name", cleanDeviceDisplayName(SessionManager.getMyDeviceName()))
                    }
                    sendJsonResponse(rawOut, 200, resp.toString())
                }

                // 3. POST /api/unpair
                method == "POST" && path.endsWith("/api/unpair") -> {
                    val bodyStr = readExactString(rawIn, contentLength.toInt())
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                    val senderId = json.optString("id", "")
                    val senderHost = json.optString("host", clientIp).ifBlank { clientIp }
                    val senderName = json.optString("name", "")
                    val clean = cleanDeviceDisplayName(senderName)

                    val peerDev = DiscoveredDevice(
                        id = senderId.ifBlank { "dev-$senderHost" },
                        name = clean,
                        host = senderHost,
                        port = DEFAULT_PORT,
                        osType = "Mobile"
                    )
                    ConnectionManager.disconnectDevice(peerDev, notifyPeer = false)
                    if (senderId.isNotBlank()) ConnectionManager.markDeviceDisconnected(senderId)
                    if (senderHost.isNotBlank()) {
                        ConnectionManager.markDeviceDisconnected(senderHost)
                        ConnectionManager.markDeviceDisconnected("$senderHost:$DEFAULT_PORT")
                    }
                    if (clean.isNotBlank()) ConnectionManager.markDeviceDisconnected(clean)

                    Handler(Looper.getMainLooper()).post {
                        val copy = currentAppCopy()
                        showNativeToast(copy.disconnected)
                    }

                    val resp = JSONObject().apply { put("status", "unpaired") }
                    sendJsonResponse(rawOut, 200, resp.toString())
                }

                // 4. POST /api/clipboard - ZERO CONFIRMATION
                method == "POST" && path.endsWith("/api/clipboard") -> {
                    val bodyStr = readExactString(rawIn, contentLength.toInt())
                    val text = try {
                        val obj = JSONObject(bodyStr)
                        obj.optString("text", bodyStr)
                    } catch (_: Exception) {
                        bodyStr
                    }

                    if (text.isNotBlank()) {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("SyncSwift", text))
                                net.syncswift.clipboard.ClipboardHistoryManager.addClip(text)
                                val copy = currentAppCopy()
                                showNativeToast(copy.clipboardSynced.replace("%s", text.take(30)))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error applying clipboard: ${e.message}")
                            }
                        }

                        val copy = currentAppCopy()
                        val item = TransferItem(
                            id = UUID.randomUUID().toString(),
                            fileName = "${copy.clipboard}: ${text.take(30).replace("\n", " ")}",
                            fileSizeBytes = text.toByteArray().size.toLong(),
                            mimeType = "text/plain",
                            bytesTransferred = text.toByteArray().size.toLong(),
                            isSent = false,
                            peerName = headers["x-sender-name"]?.let { decodeHeader(it) } ?: "Remote Device",
                            status = TransferStatus.COMPLETED,
                            timestamp = System.currentTimeMillis()
                        )
                        TransferEngine.recordTransfer(item)
                    }

                    sendJsonResponse(rawOut, 200, """{"status":"ok"}""")
                }

                // 5. POST /api/file - ZERO CONFIRMATION, 100+ MB/s DIRECT STREAM
                method == "POST" && path.endsWith("/api/file") -> {
                    val rawName = headers["x-file-name"] ?: "received_file"
                    val fileName = decodeHeader(rawName)
                    val fileSize = headers["x-file-size"]?.toLongOrNull() ?: contentLength
                    val mimeType = headers["x-mime-type"] ?: "application/octet-stream"
                    val senderName = headers["x-sender-name"]?.let { decodeHeader(it) } ?: "Remote Device"

                    val transferId = UUID.randomUUID().toString()
                    val item = TransferItem(
                        id = transferId,
                        fileName = fileName,
                        fileSizeBytes = fileSize,
                        mimeType = mimeType,
                        bytesTransferred = 0,
                        isSent = false,
                        peerName = senderName,
                        status = TransferStatus.IN_PROGRESS,
                        timestamp = System.currentTimeMillis()
                    )
                    TransferEngine.recordTransfer(item)

                    // Stream directly from rawIn into local storage
                    val savedPath = saveIncomingFileStream(context, fileName, fileSize, mimeType, transferId, rawIn)

                    if (savedPath != null) {
                        TransferEngine.updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED, savedPath)
                        Handler(Looper.getMainLooper()).post {
                            val copy = currentAppCopy()
                            showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                        }
                        sendJsonResponse(rawOut, 200, """{"status":"ok","path":"$savedPath"}""")
                    } else {
                        TransferEngine.updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        sendJsonResponse(rawOut, 500, """{"status":"error","message":"Failed to save file"}""")
                    }
                }

                else -> {
                    sendJsonResponse(rawOut, 404, """{"status":"not_found"}""")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun readHeaderLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (sb.isEmpty()) return null
                break
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun readExactString(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var total = 0
        while (total < length) {
            val n = input.read(bytes, total, length - total)
            if (n == -1) break
            total += n
        }
        return String(bytes, 0, total, Charsets.UTF_8)
    }

    private fun decodeHeader(v: String): String {
        return try {
            URLDecoder.decode(v, "UTF-8")
        } catch (_: Exception) {
            v
        }
    }

    private fun sendJsonResponse(out: OutputStream, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = if (code == 200) "OK" else if (code == 404) "Not Found" else "Internal Server Error"
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun saveIncomingFileStream(
        context: Context,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        transferId: String,
        input: InputStream
    ): String? {
        var outputStream: OutputStream? = null
        var fallbackPath: String? = null
        var insertedUri: android.net.Uri? = null

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isImage = mimeType.startsWith("image/", ignoreCase = true) ||
                ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
        val effectiveMime = if (isImage && !mimeType.startsWith("image/", ignoreCase = true)) {
            when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic" -> "image/heic"
                "bmp" -> "image/bmp"
                else -> "image/jpeg"
            }
        } else if (mimeType.isNotBlank()) mimeType else "application/octet-stream"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, effectiveMime)
                    if (isImage) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SyncSwift")
                    } else {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SyncSwift")
                    }
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collectionUri = if (isImage) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                val targetUri = context.contentResolver.insert(collectionUri, contentValues)
                if (targetUri != null) {
                    insertedUri = targetUri
                    outputStream = context.contentResolver.openOutputStream(targetUri)
                    fallbackPath = targetUri.toString()
                }
            }

            if (outputStream == null) {
                val dirType = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
                val targetDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File(context.getExternalFilesDir(dirType), "SyncSwift").apply { mkdirs() }
                } else {
                    File(Environment.getExternalStoragePublicDirectory(dirType), "SyncSwift").apply { mkdirs() }
                }
                val destFile = File(targetDir, fileName)
                fallbackPath = destFile.absolutePath
                outputStream = FileOutputStream(destFile)
            }

            // 256 KB buffer for ultra-fast Wi-Fi streaming
            val buffer = ByteArray(256 * 1024)
            var totalRead = 0L

            outputStream.use { out ->
                while (totalRead < fileSize) {
                    val toRead = minOf(buffer.size.toLong(), fileSize - totalRead).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    TransferEngine.updateTransferProgress(transferId, totalRead, TransferStatus.IN_PROGRESS)
                }
                out.flush()
            }

            // Clear IS_PENDING on Android 10+ so file appears in gallery/downloads immediately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                try {
                    val finishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(insertedUri, finishValues, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear IS_PENDING on uri: ${e.message}")
                }
            }

            // Scanner notify
            if (fallbackPath != null && !fallbackPath.startsWith("content://")) {
                MediaScannerConnection.scanFile(context, arrayOf(fallbackPath), arrayOf(effectiveMime), null)
            }

            return fallbackPath ?: fileName
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incoming file stream: ${e.message}")
            return null
        }
    }
}
