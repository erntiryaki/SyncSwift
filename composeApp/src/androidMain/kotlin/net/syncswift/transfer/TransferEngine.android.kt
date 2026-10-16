package net.syncswift.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import net.syncswift.SessionManager
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.connection.IncomingTransferRequest
import net.syncswift.connection.OutgoingStatusManager
import net.syncswift.connection.OutgoingStatusType
import net.syncswift.connection.OutgoingStep
import net.syncswift.showNativeToast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

actual object TransferEngine {
    private const val TAG = "SyncSwiftTransfer"
    private const val PROTOCOL_VERSION: Byte = 1
    private const val TYPE_CLIPBOARD: Byte = 1
    private const val TYPE_FILE: Byte = 2
    private const val TYPE_PAIR_REQUEST: Byte = 3
    private const val TYPE_UNPAIR: Byte = 4
    private const val TYPE_FILE_HANDSHAKE: Byte = 5

    private const val ACK: Byte = 6
    private const val RESP_ACCEPTED: Byte = 7
    private const val RESP_REJECTED: Byte = 8
    const val TYPE_REQUEST_FILE_DOWNLOAD: Byte = 9

    data class StagedFileTransfer(
        val fileName: String,
        val fileBytes: ByteArray,
        val mimeType: String,
        val targetDeviceName: String
    )
    private val stagedTransfers = java.util.concurrent.ConcurrentHashMap<String, StagedFileTransfer>()

    private var appContext: Context? = null
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    actual val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        restoreTransfers()
        startServer()
    }

    fun getServerPort(): Int {
        if (serverSocket == null || serverSocket?.isClosed == true) {
            startServer()
        }
        return serverSocket?.localPort ?: 52424
    }

    private fun startServer() {
        if (isServerRunning && serverSocket != null && !serverSocket!!.isClosed) return
        scope.launch {
            try {
                serverSocket = try {
                    ServerSocket(52424)
                } catch (e: Exception) {
                    ServerSocket(0) // dynamic fallback port
                }
                isServerRunning = true
                Log.d(TAG, "Transfer server started on port: ${serverSocket?.localPort}")

                while (isServerRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        scope.launch { handleIncomingClient(client) }
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                        Log.w(TAG, "Error accepting client: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server: ${e.message}")
            }
        }
    }

    private suspend fun handleIncomingClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 40000
                val input = DataInputStream(s.getInputStream())
                val output = DataOutputStream(s.getOutputStream())

                val version = input.readByte()
                if (version != PROTOCOL_VERSION) {
                    Log.w(TAG, "Unsupported protocol version: $version")
                    return
                }

                val type = input.readByte()
                when (type) {
                    TYPE_REQUEST_FILE_DOWNLOAD -> {
                        val reqTransferId = input.readUTF()
                        val receiverName = input.readUTF()
                        val staged = stagedTransfers[reqTransferId]
                        if (staged == null) {
                            Log.w(TAG, "Requested transfer ID not found: $reqTransferId")
                            output.writeByte(RESP_REJECTED.toInt())
                            output.flush()
                            return
                        }

                        Log.d(TAG, "Receiver $receiverName accepted transfer $reqTransferId, streaming ${staged.fileName}...")
                        output.writeByte(RESP_ACCEPTED.toInt())
                        output.writeLong(staged.fileBytes.size.toLong())
                        output.flush()

                        updateTransferProgress(reqTransferId, 0, TransferStatus.IN_PROGRESS)
                        OutgoingStatusManager.updateProgress(
                            transferredBytes = 0L,
                            totalBytes = staged.fileBytes.size.toLong(),
                            message = "Dosya gönderiliyor..."
                        )

                        val bufferSize = 64 * 1024
                        var offset = 0
                        while (offset < staged.fileBytes.size) {
                            val len = minOf(bufferSize, staged.fileBytes.size - offset)
                            output.write(staged.fileBytes, offset, len)
                            offset += len
                            updateTransferProgress(reqTransferId, offset.toLong(), TransferStatus.IN_PROGRESS)
                            OutgoingStatusManager.updateProgress(
                                transferredBytes = offset.toLong(),
                                totalBytes = staged.fileBytes.size.toLong(),
                                message = "Dosya gönderiliyor..."
                            )
                        }
                        output.flush()

                        val ack = input.readByte()
                        if (ack == ACK) {
                            updateTransferProgress(reqTransferId, staged.fileBytes.size.toLong(), TransferStatus.COMPLETED)
                            OutgoingStatusManager.completeSuccess("Dosya başarıyla gönderildi: ${staged.fileName}")
                            withContext(Dispatchers.Main) {
                                showNativeToast("Dosya başarıyla gönderildi: ${staged.fileName}")
                            }
                            stagedTransfers.remove(reqTransferId)
                        }
                    }

                    TYPE_PAIR_REQUEST -> {
                        val senderName = input.readUTF()
                        val senderHost = input.readUTF()
                        val senderPort = input.readInt()
                        val senderOs = input.readUTF()

                        Log.d(TAG, "Incoming pair request from $senderName ($senderHost:$senderPort)")

                        val request = IncomingPairRequest(
                            senderId = "$senderHost:$senderPort",
                            senderName = senderName,
                            senderHost = senderHost,
                            senderPort = senderPort,
                            senderOsType = senderOs
                        )

                        ConnectionManager.dispatchIncomingPairRequest(request)

                        // Wait for user to accept or reject in UI (max 30 seconds)
                        val accepted = try {
                            withTimeout(30000) {
                                request.deferredResponse.await()
                            }
                        } catch (e: Exception) {
                            false
                        }

                        if (accepted) {
                            output.writeByte(RESP_ACCEPTED.toInt())
                            output.flush()
                            ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                        } else {
                            output.writeByte(RESP_REJECTED.toInt())
                            output.flush()
                        }
                    }

                    TYPE_UNPAIR -> {
                        val senderHost = input.readUTF()
                        val senderPort = input.readInt()
                        ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
                    }

                    TYPE_CLIPBOARD -> {
                        val text = input.readUTF()
                        Log.d(TAG, "Received clipboard: $text")

                        appContext?.let { ctx ->
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("SyncSwift", text)
                                    cm?.setPrimaryClip(clip)
                                    showNativeToast("Pano senkronize edildi: ${text.take(30)}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to set clipboard: ${e.message}")
                                }
                            }
                        }

                        val item = TransferItem(
                            id = UUID.randomUUID().toString(),
                            fileName = text.take(40).replace("\n", " "),
                            fileSizeBytes = text.toByteArray().size.toLong(),
                            mimeType = "text/plain",
                            bytesTransferred = text.toByteArray().size.toLong(),
                            isSent = false,
                            peerName = "Remote Device",
                            status = TransferStatus.COMPLETED,
                            timestamp = System.currentTimeMillis()
                        )
                        recordTransfer(item)

                        output.writeByte(ACK.toInt())
                        output.flush()
                    }

                    TYPE_FILE_HANDSHAKE -> {
                        val senderName = input.readUTF()
                        val fileName = input.readUTF()
                        val fileSize = input.readLong()
                        val mimeType = input.readUTF()

                        Log.d(TAG, "Incoming file request: $fileName ($fileSize bytes) from $senderName")

                        val transferRequest = IncomingTransferRequest(
                            senderName = senderName,
                            fileName = fileName,
                            fileSize = fileSize,
                            mimeType = mimeType
                        )

                        ConnectionManager.dispatchIncomingTransferRequest(transferRequest)

                        // Wait for user to accept or reject in UI (max 35 seconds)
                        val accepted = try {
                            withTimeout(35000) {
                                transferRequest.deferredResponse.await()
                            }
                        } catch (e: Exception) {
                            false
                        }

                        if (!accepted) {
                            output.writeByte(RESP_REJECTED.toInt())
                            output.flush()
                            return
                        }

                        // Send acceptance to sender so sender starts streaming bytes
                        output.writeByte(RESP_ACCEPTED.toInt())
                        output.flush()

                        // Receive and save file stream
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
                        recordTransfer(item)

                        val savedSuccessfully = saveIncomingStream(
                            fileName = fileName,
                            fileSize = fileSize,
                            mimeType = mimeType,
                            transferId = transferId,
                            input = input
                        )

                        if (savedSuccessfully) {
                            updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED)
                            output.writeByte(ACK.toInt())
                            output.flush()

                            Handler(Looper.getMainLooper()).post {
                                showNativeToast("Dosya başarıyla alındı: $fileName")
                            }
                        } else {
                            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        }
                    }

                    TYPE_FILE -> {
                        // Direct file stream (legacy compatibility)
                        val senderName = input.readUTF()
                        val fileName = input.readUTF()
                        val fileSize = input.readLong()
                        val mimeType = input.readUTF()

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
                        recordTransfer(item)

                        val saved = saveIncomingStream(
                            fileName = fileName,
                            fileSize = fileSize,
                            mimeType = mimeType,
                            transferId = transferId,
                            input = input
                        )

                        if (saved) {
                            updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED)
                            output.writeByte(ACK.toInt())
                            output.flush()
                            Handler(Looper.getMainLooper()).post {
                                showNativeToast("Dosya alındı: $fileName")
                            }
                        } else {
                            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        }
                    }

                    TYPE_REQUEST_FILE_DOWNLOAD -> {
                        val transferId = input.readUTF()
                        val receiverModel = input.readUTF()
                        Log.d(TAG, "Incoming request to download staged transfer: $transferId from $receiverModel")

                        val staged = stagedTransfers[transferId]
                        if (staged == null) {
                            Log.w(TAG, "Staged transfer $transferId not found or expired")
                            output.writeByte(RESP_REJECTED.toInt())
                            output.flush()
                            return
                        }

                        // Staged file found -> accept and stream bytes
                        output.writeByte(RESP_ACCEPTED.toInt())
                        output.writeLong(staged.fileBytes.size.toLong())
                        output.flush()

                        updateTransferProgress(transferId, 0, TransferStatus.IN_PROGRESS)
                        OutgoingStatusManager.updateProgress(
                            transferredBytes = 0L,
                            totalBytes = staged.fileBytes.size.toLong(),
                            message = "Dosya gönderiliyor..."
                        )

                        val bufferSize = 64 * 1024
                        var offset = 0
                        while (offset < staged.fileBytes.size) {
                            val len = minOf(bufferSize, staged.fileBytes.size - offset)
                            output.write(staged.fileBytes, offset, len)
                            offset += len
                            updateTransferProgress(transferId, offset.toLong(), TransferStatus.IN_PROGRESS)
                            OutgoingStatusManager.updateProgress(
                                transferredBytes = offset.toLong(),
                                totalBytes = staged.fileBytes.size.toLong(),
                                message = "Dosya gönderiliyor..."
                            )
                        }
                        output.flush()

                        val ack = input.readByte()
                        if (ack == ACK) {
                            updateTransferProgress(transferId, staged.fileBytes.size.toLong(), TransferStatus.COMPLETED)
                            OutgoingStatusManager.completeSuccess("Dosya başarıyla gönderildi: ${staged.fileName}")
                            stagedTransfers.remove(transferId)
                            Handler(Looper.getMainLooper()).post {
                                showNativeToast("Dosya başarıyla gönderildi: ${staged.fileName}")
                            }
                        } else {
                            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                            OutgoingStatusManager.fail("Aktarım alıcı tarafından tamamlanamadı")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: ${e.message}")
            }
        }
    }

    private fun saveIncomingStream(
        fileName: String,
        fileSize: Long,
        mimeType: String,
        transferId: String,
        input: DataInputStream
    ): Boolean {
        val ctx = appContext ?: return false
        var targetUri: Uri? = null
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (mimeType.isNotBlank()) mimeType else "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SyncSwift")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                targetUri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (targetUri != null) {
                    outputStream = ctx.contentResolver.openOutputStream(targetUri)
                }
            }

            if (outputStream == null) {
                // Fallback to app-accessible storage or legacy directory
                val targetDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SyncSwift").apply { mkdirs() }
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SyncSwift").apply { mkdirs() }
                }
                val destFile = File(targetDir, fileName)
                outputStream = FileOutputStream(destFile)
            }

            val buffer = ByteArray(64 * 1024)
            var totalRead = 0L

            outputStream.use { out ->
                while (totalRead < fileSize) {
                    val toRead = minOf(buffer.size.toLong(), fileSize - totalRead).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    updateTransferProgress(transferId, totalRead, TransferStatus.IN_PROGRESS)
                }
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri != null) {
                try {
                    val finishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    ctx.contentResolver.update(targetUri, finishValues, null, null)
                } catch (_: Exception) {}
            }

            return totalRead == fileSize
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving incoming file: ${e.message}", e)
            return false
        }
    }

    actual fun sendFile(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            // 1. Pro Limit: 5 GB File Size Check
            if (!net.syncswift.subscription.SubscriptionManager.canTransferFileSize(fileBytes.size.toLong())) {
                OutgoingStatusManager.fail("Temel planda transfer başı en fazla 5 GB gönderilebilir.")
                net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                    net.syncswift.subscription.PaywallReason.FILE_SIZE_LIMIT
                )
                withContext(Dispatchers.Main) {
                    showNativeToast("5 GB dosya sınırı! Sınırsız aktarım için Pro'ya geçin.")
                    onError("5 GB limit aşıldı")
                }
                return@launch
            }

            // 2. Pro Limit: Local Network Only Check
            val isLocalNetwork = targetHost == "127.0.0.1" || targetHost == "localhost" ||
                    targetHost.startsWith("192.168.") || targetHost.startsWith("10.") ||
                    (targetHost.startsWith("172.") && (targetHost.substringAfter("172.").substringBefore(".").toIntOrNull() ?: 0) in 16..31)

            if (!isLocalNetwork && !net.syncswift.subscription.SubscriptionManager.canRemoteTransfer()) {
                OutgoingStatusManager.fail("Uzaktan transfer SyncSwift Pro özelliğidir.")
                net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                    net.syncswift.subscription.PaywallReason.REMOTE_TRANSFER
                )
                withContext(Dispatchers.Main) {
                    showNativeToast("Uzaktan transfer Pro üyeliğe özeldir.")
                    onError("Uzaktan transfer Pro özelliğidir")
                }
                return@launch
            }

            val transferId = UUID.randomUUID().toString()
            val item = TransferItem(
                id = transferId,
                fileName = fileName,
                fileSizeBytes = fileBytes.size.toLong(),
                mimeType = mimeType,
                bytesTransferred = 0,
                isSent = true,
                peerName = targetDeviceName,
                status = TransferStatus.PENDING,
                timestamp = System.currentTimeMillis()
            )
            recordTransfer(item)
            stagedTransfers[transferId] = StagedFileTransfer(fileName, fileBytes, mimeType, targetDeviceName)

            OutgoingStatusManager.show(
                type = OutgoingStatusType.FILE,
                deviceName = targetDeviceName,
                fileName = fileName,
                totalBytes = fileBytes.size.toLong(),
                step = OutgoingStep.WAITING_APPROVAL,
                message = "Alıcının dosya aktarımını onaylaması bekleniyor..."
            )

            try {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(targetHost, targetPort), 5000)
                } catch (e: Exception) {
                    // 3. Pro Limit: Background wake when receiving app is closed
                    if (!net.syncswift.subscription.SubscriptionManager.canBackgroundWake()) {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail("Temel planda alıcı cihazda uygulamanın açık olması şarttır.")
                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                            net.syncswift.subscription.PaywallReason.BACKGROUND_WAKE
                        )
                        withContext(Dispatchers.Main) {
                            showNativeToast("Alıcı cihazda uygulamanın açık olması gerekir veya Pro'ya geçin.")
                            onError("Uygulamanın açık olması şartı")
                        }
                        return@launch
                    }

                    val cleanTarget = net.syncswift.discovery.cleanDeviceDisplayName(targetDeviceName)
                    val targetDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull { 
                        (it.host == targetHost && it.port == targetPort) || 
                        (targetHost.isNotBlank() && it.host == targetHost) || 
                        net.syncswift.discovery.cleanDeviceDisplayName(it.name) == cleanTarget
                    }
                    var pushToken = targetDev?.pushToken
                    if (pushToken.isNullOrBlank()) {
                        pushToken = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)
                    }
                    if (!pushToken.isNullOrBlank()) {
                        val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                        val selfHost = self?.host ?: ""
                        val selfPort = getServerPort()
                        val fcmSent = ConnectionManager.sendFcmNotification(
                            token = pushToken,
                            title = "Yeni Dosya Aktarımı",
                            body = "${Build.MODEL} size bir dosya göndermek istiyor: $fileName",
                            data = mapOf(
                                "type" to "file_request",
                                "transferId" to transferId,
                                "senderName" to Build.MODEL,
                                "senderHost" to selfHost,
                                "senderPort" to "$selfPort",
                                "fileName" to fileName,
                                "fileSize" to "${fileBytes.size}",
                                "mimeType" to mimeType
                            )
                        )
                        withContext(Dispatchers.Main) {
                            if (fcmSent) {
                                OutgoingStatusManager.show(
                                    type = OutgoingStatusType.FILE,
                                    deviceName = targetDeviceName,
                                    fileName = fileName,
                                    totalBytes = fileBytes.size.toLong(),
                                    step = OutgoingStep.WAITING_APPROVAL,
                                    message = "Cihaza aktarım bildirimi gönderildi. Onay bekleniyor..."
                                )
                                showNativeToast("Cihaza aktarım bildirimi gönderildi. Onay bekleniyor...")
                            } else {
                                OutgoingStatusManager.fail("Cihaz uygun değil (Bildirim iletilemedi)")
                                showNativeToast("Cihaz uygun değil (Bildirim iletilemedi)")
                                onError("Cihaz uygun değil")
                            }
                        }
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail("Cihaz çevrimdışı veya bulunamadı")
                        withContext(Dispatchers.Main) {
                            showNativeToast("Cihaz uygun değil")
                            onError("Cihaz uygun değil")
                        }
                    }
                    return@launch
                }

                socket.use { s ->
                    s.soTimeout = 40000
                    val output = DataOutputStream(s.getOutputStream())
                    val input = DataInputStream(s.getInputStream())

                    // 1. Send Handshake
                    output.writeByte(PROTOCOL_VERSION.toInt())
                    output.writeByte(TYPE_FILE_HANDSHAKE.toInt())
                    output.writeUTF(Build.MODEL)
                    output.writeUTF(fileName)
                    output.writeLong(fileBytes.size.toLong())
                    output.writeUTF(mimeType)
                    output.flush()

                    // 2. Wait for receiver's decision
                    val response = input.readByte()
                    if (response != RESP_ACCEPTED) {
                        updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
                        OutgoingStatusManager.fail("Aktarım alıcı tarafından reddedildi")
                        withContext(Dispatchers.Main) {
                            showNativeToast("Transfer reddedildi")
                            onError("Transfer reddedildi")
                        }
                        return@launch
                    }

                    // 3. Receiver accepted -> stream file bytes
                    updateTransferProgress(transferId, 0, TransferStatus.IN_PROGRESS)
                    OutgoingStatusManager.updateProgress(
                        transferredBytes = 0L,
                        totalBytes = fileBytes.size.toLong(),
                        message = "Dosya gönderiliyor..."
                    )
                    val bufferSize = 64 * 1024
                    var offset = 0
                    while (offset < fileBytes.size) {
                        val len = minOf(bufferSize, fileBytes.size - offset)
                        output.write(fileBytes, offset, len)
                        offset += len
                        updateTransferProgress(transferId, offset.toLong(), TransferStatus.IN_PROGRESS)
                        OutgoingStatusManager.updateProgress(
                            transferredBytes = offset.toLong(),
                            totalBytes = fileBytes.size.toLong(),
                            message = "Dosya gönderiliyor..."
                        )
                    }
                    output.flush()

                    // 4. Await completion ACK
                    val ack = input.readByte()
                    if (ack == ACK) {
                        updateTransferProgress(transferId, fileBytes.size.toLong(), TransferStatus.COMPLETED)
                        OutgoingStatusManager.completeSuccess("Dosya başarıyla gönderildi: $fileName")
                        withContext(Dispatchers.Main) {
                            showNativeToast("Dosya başarıyla gönderildi: $fileName")
                            onSuccess()
                        }
                    } else {
                        throw Exception("Invalid ACK from receiver")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send file failed: ${e.message}")
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                val msg = if (e.message?.contains("refused", ignoreCase = true) == true ||
                             e.message?.contains("timed out", ignoreCase = true) == true) {
                    "Cihaz uygun değil"
                } else {
                    "Aktarım başarısız: ${e.message}"
                }
                OutgoingStatusManager.fail(msg)
                withContext(Dispatchers.Main) {
                    showNativeToast(msg)
                    onError(msg)
                }
            }
        }
    }

    actual fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            val transferId = UUID.randomUUID().toString()
            val item = TransferItem(
                id = transferId,
                fileName = text.take(40).replace("\n", " "),
                fileSizeBytes = text.toByteArray().size.toLong(),
                mimeType = "text/plain",
                bytesTransferred = text.toByteArray().size.toLong(),
                isSent = true,
                peerName = targetDeviceName,
                status = TransferStatus.IN_PROGRESS,
                timestamp = System.currentTimeMillis()
            )
            recordTransfer(item)

            try {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(targetHost, targetPort), 6000)
                } catch (e: Exception) {
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    withContext(Dispatchers.Main) {
                        showNativeToast("Cihaz uygun değil")
                        onError("Cihaz uygun değil")
                    }
                    return@launch
                }

                socket.use { s ->
                    s.soTimeout = 15000
                    val output = DataOutputStream(s.getOutputStream())
                    val input = DataInputStream(s.getInputStream())

                    output.writeByte(PROTOCOL_VERSION.toInt())
                    output.writeByte(TYPE_CLIPBOARD.toInt())
                    output.writeUTF(text)
                    output.flush()

                    val ack = input.readByte()
                    if (ack == ACK) {
                        updateTransferProgress(transferId, text.toByteArray().size.toLong(), TransferStatus.COMPLETED)
                        withContext(Dispatchers.Main) {
                            showNativeToast("Pano gönderildi: ${text.take(30)}")
                            onSuccess()
                        }
                    } else {
                        throw Exception("Invalid ACK from receiver")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send clipboard failed: ${e.message}")
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                withContext(Dispatchers.Main) {
                    showNativeToast("Cihaz uygun değil")
                    onError("Cihaz uygun değil")
                }
            }
        }
    }

    fun downloadStagedFile(
        senderHost: String,
        senderPort: Int,
        senderName: String,
        transferId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ) {
        scope.launch {
            try {
                if (senderHost.isBlank()) {
                    Log.e(TAG, "Cannot download: senderHost is blank")
                    return@launch
                }

                val socket = Socket()
                socket.connect(InetSocketAddress(senderHost, senderPort), 10000)
                socket.use { s ->
                    s.soTimeout = 60000
                    val out = DataOutputStream(s.getOutputStream())
                    val input = DataInputStream(s.getInputStream())

                    out.writeByte(PROTOCOL_VERSION.toInt())
                    out.writeByte(TYPE_REQUEST_FILE_DOWNLOAD.toInt())
                    out.writeUTF(transferId)
                    out.writeUTF(Build.MODEL)
                    out.flush()

                    val resp = input.readByte()
                    if (resp != RESP_ACCEPTED) {
                        Log.w(TAG, "Sender rejected download request for $transferId")
                        return@launch
                    }

                    val incomingSize = input.readLong()

                    val item = TransferItem(
                        id = transferId,
                        fileName = fileName,
                        fileSizeBytes = if (incomingSize > 0) incomingSize else fileSize,
                        mimeType = mimeType,
                        bytesTransferred = 0,
                        isSent = false,
                        peerName = senderName,
                        status = TransferStatus.IN_PROGRESS,
                        timestamp = System.currentTimeMillis()
                    )
                    recordTransfer(item)

                    val saved = saveIncomingStream(
                        fileName = fileName,
                        fileSize = if (incomingSize > 0) incomingSize else fileSize,
                        mimeType = mimeType,
                        transferId = transferId,
                        input = input
                    )

                    if (saved) {
                        out.writeByte(ACK.toInt())
                        out.flush()
                        updateTransferProgress(transferId, if (incomingSize > 0) incomingSize else fileSize, TransferStatus.COMPLETED)
                        withContext(Dispatchers.Main) {
                            showNativeToast("Dosya başarıyla indirildi: $fileName")
                        }
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download staged file failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    showNativeToast("Dosya indirme başarısız: ${e.message}")
                }
            }
        }
    }

    private fun recordTransfer(item: TransferItem) {
        val current = _transfers.value.toMutableList()
        current.add(0, item)
        _transfers.value = current
        saveTransfers()
    }

    private fun updateTransferProgress(id: String, bytes: Long, status: TransferStatus) {
        val current = _transfers.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(
                bytesTransferred = bytes,
                status = status
            )
            _transfers.value = current
            if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED || status == TransferStatus.CANCELLED) {
                saveTransfers()
            }
        }
    }

    private fun restoreTransfers() {
        val json = SessionManager.getSavedTransfersJson() ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<TransferItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val statusStr = obj.optString("status", TransferStatus.COMPLETED.name)
                val status = try { TransferStatus.valueOf(statusStr) } catch (e: Exception) { TransferStatus.COMPLETED }
                list.add(
                    TransferItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        fileName = obj.optString("fileName", "Unknown"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        mimeType = obj.optString("mimeType", "*/*"),
                        bytesTransferred = obj.optLong("bytesTransferred", 0L),
                        isSent = obj.optBoolean("isSent", false),
                        peerName = obj.optString("peerName", ""),
                        status = status,
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            _transfers.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring transfers: ${e.message}")
        }
    }

    private fun saveTransfers() {
        try {
            val arr = JSONArray()
            // Keep at most last 100 transfers to keep preferences fast and responsive
            val list = _transfers.value.take(100)
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("fileName", item.fileName)
                obj.put("fileSizeBytes", item.fileSizeBytes)
                obj.put("mimeType", item.mimeType)
                obj.put("bytesTransferred", item.bytesTransferred)
                obj.put("isSent", item.isSent)
                obj.put("peerName", item.peerName)
                obj.put("status", item.status.name)
                obj.put("timestamp", item.timestamp)
                arr.put(obj)
            }
            SessionManager.saveTransfersJson(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transfers: ${e.message}")
        }
    }
}
