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
import net.syncswift.currentAppCopy
import net.syncswift.showNativeToast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import net.syncswift.SyncSwiftConfig
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

    private var lastReceivedClipHash: Int = 0
    private var lastReceivedClipTime: Long = 0L

    fun applyReceivedClipboard(text: String, copy: net.syncswift.Copy): Boolean {
        val hash = text.hashCode()
        val now = System.currentTimeMillis()
        if (hash == lastReceivedClipHash && now - lastReceivedClipTime < 5000L) {
            return false
        }
        lastReceivedClipHash = hash
        lastReceivedClipTime = now

        appContext?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("SyncSwift", text)
                    cm?.setPrimaryClip(clip)
                    showNativeToast(copy.clipboardSynced.replace("%s", text.take(30)))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set clipboard: ${e.message}")
                }
            }
        }
        return true
    }

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
                            message = currentAppCopy().transferringFile
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
                                message = currentAppCopy().transferringFile
                            )
                        }
                        output.flush()

                        val ack = input.readByte()
                        if (ack == ACK) {
                            val copy = currentAppCopy()
                            updateTransferProgress(reqTransferId, staged.fileBytes.size.toLong(), TransferStatus.COMPLETED)
                            OutgoingStatusManager.completeSuccess(copy.fileSentSuccessfully.replace("%s", staged.fileName))
                            withContext(Dispatchers.Main) {
                                showNativeToast(copy.fileSentSuccessfully.replace("%s", staged.fileName))
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

                        // Launch watcher to monitor client disconnection / cancellation in real time
                        val watchJob = scope.launch(Dispatchers.IO) {
                            try {
                                val b = input.read()
                                if (b == -1 || b == 10) { // EOF or TYPE_CANCEL
                                    ConnectionManager.cancelIncomingPairRequest()
                                }
                            } catch (_: Exception) {
                                ConnectionManager.cancelIncomingPairRequest()
                            }
                        }

                        // Wait for user to accept or reject in UI (max 30 seconds)
                        val accepted = try {
                            withTimeout(30000) {
                                request.deferredResponse.await()
                            }
                        } catch (e: Exception) {
                            ConnectionManager.cancelIncomingPairRequest()
                            false
                        } finally {
                            watchJob.cancel()
                        }

                        if (accepted) {
                            try {
                                output.writeByte(RESP_ACCEPTED.toInt())
                                output.flush()
                                ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                                ConnectionManager.markDeviceConnected(request.senderId)
                            } catch (e: Exception) {
                                ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
                                ConnectionManager.markDeviceDisconnected(request.senderId)
                                showNativeToast(currentAppCopy().requestCancelledBySender)
                            }
                        } else {
                            try {
                                output.writeByte(RESP_REJECTED.toInt())
                                output.flush()
                            } catch (_: Exception) {}
                        }
                    }

                    TYPE_UNPAIR -> {
                        val senderHost = input.readUTF()
                        val senderPort = input.readInt()
                        ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
                    }

                    TYPE_CLIPBOARD -> {
                        val senderName = input.readUTF()
                        val text = input.readUTF()
                        Log.d(TAG, "Received clipboard transfer request from $senderName: $text")

                        val copy = currentAppCopy()
                        val preview = text.take(35).replace("\n", " ")
                        val transferRequest = IncomingTransferRequest(
                            senderName = senderName,
                            fileName = "${copy.clipboard}: $preview",
                            fileSize = text.toByteArray().size.toLong(),
                            mimeType = "text/plain"
                        )

                        ConnectionManager.dispatchIncomingTransferRequest(transferRequest)

                        // Launch watcher to monitor client disconnection / cancellation in real time
                        val watchJob = scope.launch(Dispatchers.IO) {
                            try {
                                val b = input.read()
                                if (b == -1 || b == 10) {
                                    ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                                }
                            } catch (_: Exception) {
                                ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                            }
                        }

                        // Wait for recipient to accept or reject in UI (max 35 seconds)
                        val accepted = try {
                            withTimeout(35000) {
                                transferRequest.deferredResponse.await()
                            }
                        } catch (e: Exception) {
                            ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                            false
                        } finally {
                            watchJob.cancel()
                        }

                        if (!accepted) {
                            try {
                                output.writeByte(RESP_REJECTED.toInt())
                                output.flush()
                            } catch (_: Exception) {}
                            return
                        }

                        // Receiver accepted -> set clipboard and reply accepted
                        applyReceivedClipboard(text, copy)

                        val item = TransferItem(
                            id = UUID.randomUUID().toString(),
                            fileName = "${copy.clipboard}: $preview",
                            fileSizeBytes = text.toByteArray().size.toLong(),
                            mimeType = "text/plain",
                            bytesTransferred = text.toByteArray().size.toLong(),
                            isSent = false,
                            peerName = senderName,
                            status = TransferStatus.COMPLETED,
                            timestamp = System.currentTimeMillis()
                        )
                        recordTransfer(item)

                        output.writeByte(RESP_ACCEPTED.toInt())
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

                        // Launch watcher to monitor client disconnection / cancellation in real time
                        val watchJob = scope.launch(Dispatchers.IO) {
                            try {
                                val b = input.read()
                                if (b == -1 || b == 10) { // EOF or TYPE_CANCEL
                                    ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                                }
                            } catch (_: Exception) {
                                ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                            }
                        }

                        // Wait for user to accept or reject in UI (max 35 seconds)
                        val accepted = try {
                            withTimeout(35000) {
                                transferRequest.deferredResponse.await()
                            }
                        } catch (e: Exception) {
                            ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                            false
                        } finally {
                            watchJob.cancel()
                        }

                        if (!accepted) {
                            try {
                                output.writeByte(RESP_REJECTED.toInt())
                                output.flush()
                            } catch (_: Exception) {}
                            return
                        }

                        // Send acceptance to sender so sender starts streaming bytes
                        try {
                            output.writeByte(RESP_ACCEPTED.toInt())
                            output.flush()
                        } catch (e: Exception) {
                            showNativeToast(currentAppCopy().transferCancelledBySender)
                            return
                        }

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

                        if (savedSuccessfully != null) {
                            updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED, savedSuccessfully)
                            output.writeByte(ACK.toInt())
                            output.flush()

                            Handler(Looper.getMainLooper()).post {
                                val copy = currentAppCopy()
                                showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
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

                        if (saved != null) {
                            updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED, saved)
                            output.writeByte(ACK.toInt())
                            output.flush()
                            Handler(Looper.getMainLooper()).post {
                                val copy = currentAppCopy()
                                showNativeToast(copy.fileReceived.replace("%s", fileName))
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

                        val copy = currentAppCopy()
                        updateTransferProgress(transferId, 0, TransferStatus.IN_PROGRESS)
                        OutgoingStatusManager.updateProgress(
                            transferredBytes = 0L,
                            totalBytes = staged.fileBytes.size.toLong(),
                            message = copy.transferringFile
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
                                message = copy.transferringFile
                            )
                        }
                        output.flush()

                        val ack = input.readByte()
                        if (ack == ACK) {
                            updateTransferProgress(transferId, staged.fileBytes.size.toLong(), TransferStatus.COMPLETED)
                            OutgoingStatusManager.completeSuccess(copy.fileSentSuccessfully.replace("%s", staged.fileName))
                            stagedTransfers.remove(transferId)
                            Handler(Looper.getMainLooper()).post {
                                showNativeToast(copy.fileSentSuccessfully.replace("%s", staged.fileName))
                            }
                        } else {
                            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                            OutgoingStatusManager.fail(copy.transferFailedRecipientIncomplete)
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
        input: java.io.InputStream
    ): String? {
        val ctx = appContext ?: return null
        var targetUri: Uri? = null
        var outputStream: OutputStream? = null
        var fallbackPath: String? = null

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
                fallbackPath = destFile.absolutePath
                outputStream = FileOutputStream(destFile)
            }

            val buffer = ByteArray(64 * 1024)
            var totalRead = 0L

            outputStream.use { out ->
                while (totalRead < fileSize || fileSize <= 0) {
                    val toRead = if (fileSize > 0) minOf(buffer.size.toLong(), fileSize - totalRead).toInt() else buffer.size
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    if (fileSize > 0) {
                        updateTransferProgress(transferId, totalRead, TransferStatus.IN_PROGRESS)
                    }
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

            val isOk = if (fileSize > 0) totalRead == fileSize else totalRead > 0
            if (isOk) {
                val savedLocation = targetUri?.toString() ?: fallbackPath
                showFileCompletedNotification(fileName, mimeType, savedLocation)
                return savedLocation
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving incoming file: ${e.message}", e)
            return null
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
        val transferId = UUID.randomUUID().toString()
        var isCancelled = false
        var transferJob: Job? = null
        var activeSocket: Socket? = null

        val cleanTarget = net.syncswift.discovery.cleanDeviceDisplayName(targetDeviceName)
        val targetDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
            net.syncswift.discovery.cleanDeviceDisplayName(it.name) == cleanTarget
        }
        val isLocalNetwork = targetDev != null && targetDev.isOnline && !targetDev.isRemote &&
                !targetHost.startsWith("remote-") && !ConnectionManager.isRemoteDevice(targetDev) &&
                (targetHost == "127.0.0.1" || targetHost == "localhost" ||
                targetHost.startsWith("192.168.") || targetHost.startsWith("10.") ||
                (targetHost.startsWith("172.") && (targetHost.substringAfter("172.").substringBefore(".").toIntOrNull() ?: 0) in 16..31))

        val cancelTransfer: () -> Unit = {
            isCancelled = true
            transferJob?.cancel()
            OutgoingStatusManager.dismiss()
            try {
                activeSocket?.let { s ->
                    try {
                        val out = s.getOutputStream()
                        out.write(10) // TYPE_CANCEL
                        out.flush()
                    } catch (_: Exception) {}
                    s.close()
                }
            } catch (_: Exception) {}
            activeSocket = null
            updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
            ConnectionManager.markRequestCancelled(transferId)
        }

        transferJob = scope.launch {
            val copy = currentAppCopy()
            // 1. Pro Limit: 5 GB File Size Check
            if (!net.syncswift.subscription.SubscriptionManager.canTransferFileSize(fileBytes.size.toLong())) {
                OutgoingStatusManager.fail(copy.fileSizeCapExceeded)
                net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                    net.syncswift.subscription.PaywallReason.FILE_SIZE_LIMIT
                )
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.fileSizeCapExceeded)
                    onError(copy.fileSizeCapExceeded)
                }
                return@launch
            }

            // 2. Pro Limit: Local Network Only Check
            if (!isLocalNetwork && !net.syncswift.subscription.SubscriptionManager.canRemoteTransfer()) {
                OutgoingStatusManager.fail(copy.remoteTransferProRequired)
                net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                    net.syncswift.subscription.PaywallReason.REMOTE_TRANSFER
                )
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.remoteTransferProRequired)
                    onError(copy.remoteTransferProRequired)
                }
                return@launch
            }

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

            // If not local network, directly execute Remote Delivery via Supabase Storage & Push Notification
            if (!isLocalNetwork) {
                executeRemoteTransfer(
                    targetDeviceName = targetDeviceName,
                    targetHost = targetHost,
                    fileName = fileName,
                    fileBytes = fileBytes,
                    mimeType = mimeType,
                    transferId = transferId,
                    onSuccess = onSuccess,
                    onError = onError
                )
                return@launch
            }

            OutgoingStatusManager.show(
                type = OutgoingStatusType.FILE,
                deviceName = targetDeviceName,
                fileName = fileName,
                totalBytes = fileBytes.size.toLong(),
                step = OutgoingStep.WAITING_APPROVAL,
                message = copy.waitingForRecipientApproval,
                onCancel = cancelTransfer
            )

            try {
                val socket = Socket()
                activeSocket = socket
                try {
                    socket.connect(InetSocketAddress(targetHost, targetPort), 1200)
                } catch (e: Exception) {
                    activeSocket = null
                    if (isCancelled || !isActive) return@launch
                    // Local socket unreachable: Fallback to remote transfer if allowed
                    if (!net.syncswift.subscription.SubscriptionManager.canRemoteTransfer()) {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail(copy.remoteTransferProRequired)
                        net.syncswift.subscription.SubscriptionManager.triggerPaywall(
                            net.syncswift.subscription.PaywallReason.REMOTE_TRANSFER
                        )
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.remoteTransferProRequired)
                            onError(copy.remoteTransferProRequired)
                        }
                        return@launch
                    }

                    executeRemoteTransfer(
                        targetDeviceName = targetDeviceName,
                        targetHost = targetHost,
                        fileName = fileName,
                        fileBytes = fileBytes,
                        mimeType = mimeType,
                        transferId = transferId,
                        onSuccess = onSuccess,
                        onError = onError
                    )
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
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail(copy.transferRejectedByRecipient)
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.transferDeclined)
                            onError(copy.transferDeclined)
                        }
                        return@launch
                    }

                    // 3. Receiver accepted -> stream file bytes
                    updateTransferProgress(transferId, 0, TransferStatus.IN_PROGRESS)
                    OutgoingStatusManager.updateProgress(
                        transferredBytes = 0L,
                        totalBytes = fileBytes.size.toLong(),
                        message = copy.transferringFile
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
                            message = copy.transferringFile
                        )
                    }
                    output.flush()

                    // 4. Await completion ACK
                    val ack = input.readByte()
                    if (ack == ACK) {
                        updateTransferProgress(transferId, fileBytes.size.toLong(), TransferStatus.COMPLETED)
                        OutgoingStatusManager.completeSuccess(copy.fileSentSuccessfully.replace("%s", fileName))
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.fileSentSuccessfully.replace("%s", fileName))
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
                    copy.deviceNotAvailable
                } else {
                    "${copy.transferFailed}: ${e.message}"
                }
                OutgoingStatusManager.fail(msg)
                withContext(Dispatchers.Main) {
                    showNativeToast(msg)
                    onError(msg)
                }
            }
        }
    }

    private suspend fun executeRemoteTransfer(
        targetDeviceName: String,
        targetHost: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        transferId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        var isCancelled = false
        var uploadJob: Job? = null
        val copy = currentAppCopy()
        val cleanTarget = net.syncswift.discovery.cleanDeviceDisplayName(targetDeviceName)
        val targetDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
            net.syncswift.discovery.cleanDeviceDisplayName(it.name) == cleanTarget
        }

        val cancelRemote: () -> Unit = {
            isCancelled = true
            uploadJob?.cancel()
            updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
            ConnectionManager.markRequestCancelled(transferId)
            OutgoingStatusManager.dismiss()
        }

        OutgoingStatusManager.show(
            type = OutgoingStatusType.FILE,
            deviceName = targetDeviceName,
            fileName = fileName,
            totalBytes = fileBytes.size.toLong(),
            step = OutgoingStep.IN_PROGRESS,
            message = copy.remoteTransferUploading,
            onCancel = cancelRemote
        )

        uploadJob = scope.launch(Dispatchers.IO) {
            val downloadUrl = uploadToSupabaseStorage(transferId, fileName, fileBytes, mimeType)
            if (isCancelled || !isActive) return@launch

            if (downloadUrl == null) {
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                OutgoingStatusManager.fail(copy.transferFailed)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.transferFailed)
                    onError(copy.transferFailed)
                }
                return@launch
            }

            var pushToken = targetDev?.pushToken
            if (pushToken.isNullOrBlank() && targetHost.isNotBlank()) {
                pushToken = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)
            }
            if (isCancelled || !isActive) return@launch

            if (!pushToken.isNullOrBlank()) {
                val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
                val fcmSent = ConnectionManager.sendFcmNotification(
                    token = pushToken,
                    title = targetCopy.notificationFileRequestTitle,
                    body = targetCopy.notificationFileRequestBody
                        .replace("%s", Build.MODEL)
                        .replace("%s", fileName)
                        .replace("(%s)", "(${formatBytes(fileBytes.size.toLong())})"),
                    data = mapOf(
                        "type" to "file_request",
                        "transferId" to transferId,
                        "senderName" to Build.MODEL,
                        "fileName" to fileName,
                        "fileSize" to "${fileBytes.size}",
                        "mimeType" to mimeType,
                        "downloadUrl" to downloadUrl,
                        "isRemote" to "true",
                        "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                        "timestamp" to "${System.currentTimeMillis()}"
                    )
                )

                if (isCancelled || !isActive) return@launch

                withContext(Dispatchers.Main) {
                    if (fcmSent) {
                        OutgoingStatusManager.show(
                            type = OutgoingStatusType.FILE,
                            deviceName = targetDeviceName,
                            fileName = fileName,
                            totalBytes = fileBytes.size.toLong(),
                            step = OutgoingStep.WAITING_APPROVAL,
                            message = copy.transferNotificationSentWaiting,
                            onCancel = cancelRemote
                        )
                        showNativeToast(copy.transferNotificationSentWaiting)
                        onSuccess()
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail(copy.deviceNotificationFailed)
                        showNativeToast(copy.deviceNotificationFailed)
                        onError(copy.deviceNotificationFailed)
                    }
                }
            } else {
                if (isCancelled || !isActive) return@launch
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                OutgoingStatusManager.fail(copy.deviceOfflineOrNotFound)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.deviceNotAvailable)
                    onError(copy.deviceNotAvailable)
                }
            }
        }
    }

    private suspend fun uploadToSupabaseStorage(
        transferId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val safeName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val objectPath = "${transferId}_$safeName"
            val uploadUrl = java.net.URL("${SyncSwiftConfig.SUPABASE_URL}/storage/v1/object/syncswift_transfers/$objectPath")
            val conn = (uploadUrl.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                val key = SyncSwiftConfig.SUPABASE_SERVICE_ROLE_KEY.ifBlank { SyncSwiftConfig.SUPABASE_ANON_KEY }
                setRequestProperty("apikey", key)
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", if (mimeType.isNotBlank()) mimeType else "application/octet-stream")
                setFixedLengthStreamingMode(fileBytes.size)
            }
            conn.outputStream.use { os ->
                os.write(fileBytes)
                os.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                "${SyncSwiftConfig.SUPABASE_URL}/storage/v1/object/public/syncswift_transfers/$objectPath"
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Supabase storage upload failed ($code): $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase storage upload exception: ${e.message}", e)
            null
        }
    }

    fun downloadRemoteFile(
        downloadUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        transferId: String,
        senderName: String
    ) {
        scope.launch(Dispatchers.IO) {
            try {
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

                val conn = (java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 60000
                }
                if (conn.responseCode !in 200..299) {
                    Log.e(TAG, "Failed to connect to remote download URL: ${conn.responseCode}")
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    return@launch
                }

                val totalSize = if (fileSize > 0) fileSize else conn.contentLengthLong
                val input = conn.inputStream
                val savedLocation = saveIncomingStream(
                    fileName = fileName,
                    fileSize = totalSize,
                    mimeType = mimeType,
                    transferId = transferId,
                    input = input
                )

                if (savedLocation != null) {
                    updateTransferProgress(transferId, totalSize, TransferStatus.COMPLETED, savedLocation)
                    withContext(Dispatchers.Main) {
                        val copy = currentAppCopy()
                        showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                    }
                } else {
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Remote file download error: ${e.message}", e)
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
            }
        }
    }

    fun showFileCompletedNotification(fileName: String, mimeType: String, localFilePath: String?) {
        val ctx = appContext ?: return
        try {
            val copy = currentAppCopy()
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "syncswift_transfers_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    copy.notificationChannelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = copy.notificationChannelDesc
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationId = (System.currentTimeMillis() % 100000).toInt()

            val openIntent = android.content.Intent(ctx, net.syncswift.fcm.NotificationActionReceiver::class.java).apply {
                action = net.syncswift.fcm.NotificationActionReceiver.ACTION_OPEN_FILE
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_PATH, localFilePath)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_NAME, fileName)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_MIME_TYPE, mimeType)
            }
            val openPending = android.app.PendingIntent.getBroadcast(
                ctx,
                notificationId + 10,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val openActionIntent = android.content.Intent(ctx, net.syncswift.fcm.NotificationActionReceiver::class.java).apply {
                action = net.syncswift.fcm.NotificationActionReceiver.ACTION_OPEN_FILE
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_PATH, localFilePath)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_NAME, fileName)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_MIME_TYPE, mimeType)
            }
            val openActionPending = android.app.PendingIntent.getBroadcast(
                ctx,
                notificationId + 11,
                openActionIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val showLocationIntent = android.content.Intent(ctx, net.syncswift.fcm.NotificationActionReceiver::class.java).apply {
                action = net.syncswift.fcm.NotificationActionReceiver.ACTION_SHOW_LOCATION
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_PATH, localFilePath)
                putExtra(net.syncswift.fcm.NotificationActionReceiver.EXTRA_FILE_NAME, fileName)
            }
            val showLocationPending = android.app.PendingIntent.getBroadcast(
                ctx,
                notificationId + 12,
                showLocationIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(net.syncswift.R.mipmap.ic_launcher)
                .setContentTitle(copy.fileReceived.replace("%s", fileName))
                .setContentText("${copy.openFileAction} · ${copy.showFileLocationAction}")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_view, copy.openFileAction, openActionPending)
                .addAction(android.R.drawable.ic_menu_agenda, copy.showFileLocationAction, showLocationPending)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show file completed notification: ${e.message}", e)
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
            val copy = currentAppCopy()
            val transferId = UUID.randomUUID().toString()
            val item = TransferItem(
                id = transferId,
                fileName = "${copy.clipboard}: ${text.take(35).replace("\n", " ")}",
                fileSizeBytes = text.toByteArray().size.toLong(),
                mimeType = "text/plain",
                bytesTransferred = text.toByteArray().size.toLong(),
                isSent = true,
                peerName = targetDeviceName,
                status = TransferStatus.PENDING,
                timestamp = System.currentTimeMillis()
            )
            recordTransfer(item)

            var activeSocket: Socket? = null
            var isCancelled = false
            var job: Job? = null

            val cancelClipboard: () -> Unit = {
                isCancelled = true
                job?.cancel()
                try {
                    activeSocket?.close()
                } catch (_: Exception) {}
                activeSocket = null
                updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
                OutgoingStatusManager.dismiss()
            }

            OutgoingStatusManager.show(
                type = OutgoingStatusType.FILE,
                deviceName = targetDeviceName,
                fileName = "${copy.clipboard}: ${text.take(30).replace("\n", " ")}",
                totalBytes = text.toByteArray().size.toLong(),
                step = OutgoingStep.WAITING_APPROVAL,
                message = copy.waitingForRecipientApproval,
                onCancel = cancelClipboard
            )

            job = coroutineContext.job

            val cleanTarget = net.syncswift.discovery.cleanDeviceDisplayName(targetDeviceName)
            val targetDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
                net.syncswift.discovery.cleanDeviceDisplayName(it.name) == cleanTarget
            }
            val isLocalDiscovered = targetDev != null && targetDev.isOnline && !targetDev.isRemote &&
                    !targetHost.startsWith("remote-") && !ConnectionManager.isRemoteDevice(targetDev)

            suspend fun sendViaFcmFallback(): Boolean {
                if (isCancelled || !isActive) return false
                var pushToken = targetDev?.pushToken
                if (pushToken.isNullOrBlank()) {
                    pushToken = ConnectionManager.queryPushTokenFromSupabase(targetHost, targetDeviceName)
                }
                if (isCancelled || !isActive) return false
                if (!pushToken.isNullOrBlank()) {
                    val self = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
                    val selfName = net.syncswift.discovery.cleanDeviceDisplayName(self?.name ?: android.os.Build.MODEL)
                    val preview = text.trim().replace("\n", " ").take(100)
                    val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
                    val notificationBody = targetCopy.clipboardSynced.replace("%s", preview)
                    val sent = ConnectionManager.sendFcmNotification(
                        token = pushToken,
                        title = targetCopy.syncClipboardAction,
                        body = notificationBody,
                        data = mapOf(
                            "type" to "clipboard_sync",
                            "text" to text,
                            "content" to text,
                            "body" to preview,
                            "senderName" to selfName,
                            "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode())
                        )
                    )
                    if (isCancelled || !isActive) return false
                    if (sent) {
                        updateTransferProgress(transferId, text.toByteArray().size.toLong(), TransferStatus.COMPLETED)
                        OutgoingStatusManager.completeSuccess(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                            onSuccess()
                        }
                        return true
                    }
                }
                return false
            }

            if (!isLocalDiscovered) {
                val ok = sendViaFcmFallback()
                if (!ok && !isCancelled && isActive) {
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    OutgoingStatusManager.fail(copy.deviceNotAvailable)
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.deviceNotAvailable)
                        onError(copy.deviceNotAvailable)
                    }
                }
                return@launch
            }

            try {
                val socket = Socket()
                activeSocket = socket
                try {
                    socket.connect(InetSocketAddress(targetHost, targetPort), 1200)
                } catch (e: Exception) {
                    activeSocket = null
                    if (isCancelled || !isActive) return@launch
                    val ok = sendViaFcmFallback()
                    if (!ok && !isCancelled && isActive) {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail(copy.deviceNotAvailable)
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.deviceNotAvailable)
                            onError(copy.deviceNotAvailable)
                        }
                    }
                    return@launch
                }

                socket.use { s ->
                    s.soTimeout = 35000
                    val output = DataOutputStream(s.getOutputStream())
                    val input = DataInputStream(s.getInputStream())

                    output.writeByte(PROTOCOL_VERSION.toInt())
                    output.writeByte(TYPE_CLIPBOARD.toInt())
                    output.writeUTF(Build.MODEL)
                    output.writeUTF(text)
                    output.flush()

                    val resp = input.readByte()
                    if (resp == RESP_ACCEPTED || resp == ACK) {
                        updateTransferProgress(transferId, text.toByteArray().size.toLong(), TransferStatus.COMPLETED)
                        OutgoingStatusManager.completeSuccess(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                            onSuccess()
                        }
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                        OutgoingStatusManager.fail(copy.transferDeclined)
                        withContext(Dispatchers.Main) {
                            showNativeToast(copy.transferDeclined)
                            onError(copy.transferDeclined)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send clipboard failed: ${e.message}")
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                OutgoingStatusManager.fail(copy.deviceNotAvailable)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.deviceNotAvailable)
                    onError(copy.deviceNotAvailable)
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

                    if (saved != null) {
                        out.writeByte(ACK.toInt())
                        out.flush()
                        updateTransferProgress(transferId, if (incomingSize > 0) incomingSize else fileSize, TransferStatus.COMPLETED, saved)
                        withContext(Dispatchers.Main) {
                            val copy = currentAppCopy()
                            showNativeToast(copy.fileDownloadSuccess.replace("%s", fileName))
                        }
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download staged file failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    val copy = currentAppCopy()
                    showNativeToast(copy.fileDownloadFailed.replace("%s", e.message ?: ""))
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

    fun updateTransferProgress(id: String, bytes: Long, status: TransferStatus, localFilePath: String? = null) {
        val current = _transfers.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            if (status == TransferStatus.CANCELLED) {
                current.removeAt(index)
                _transfers.value = current
                saveTransfers()
                return
            }
            val existing = current[index]
            current[index] = existing.copy(
                bytesTransferred = bytes,
                status = status,
                localFilePath = localFilePath ?: existing.localFilePath
            )
            _transfers.value = current
            if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED) {
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
                        timestamp = obj.optLong("timestamp", 0L),
                        localFilePath = if (obj.has("localFilePath")) obj.optString("localFilePath").takeIf { it.isNotBlank() } else null
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
                obj.put("localFilePath", item.localFilePath ?: "")
                arr.put(obj)
            }
            SessionManager.saveTransfersJson(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transfers: ${e.message}")
        }
    }
}
