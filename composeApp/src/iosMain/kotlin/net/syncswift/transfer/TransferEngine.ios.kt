package net.syncswift.transfer

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.PlatformConfig
import net.syncswift.SessionManager
import net.syncswift.currentTimeMs
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.connection.IncomingTransferRequest
import net.syncswift.connection.OutgoingStatusManager
import net.syncswift.connection.OutgoingStatusType
import net.syncswift.connection.OutgoingStep
import net.syncswift.currentAppCopy
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.showNativeToast
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.UIKit.UIPasteboard
import platform.darwin.*
import platform.posix.*
import kotlin.coroutines.resume
import kotlin.Byte

@OptIn(ExperimentalForeignApi::class)
actual object TransferEngine {
    private fun htons(s: UShort): UShort = (((s.toInt() and 0xFF) shl 8) or ((s.toInt() ushr 8) and 0xFF)).toUShort()
    private fun parseIpv4(ip: String): UInt? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val b0 = parts[0].toUIntOrNull() ?: return null
        val b1 = parts[1].toUIntOrNull() ?: return null
        val b2 = parts[2].toUIntOrNull() ?: return null
        val b3 = parts[3].toUIntOrNull() ?: return null
        if (b0 > 255u || b1 > 255u || b2 > 255u || b3 > 255u) return null
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private const val PROTOCOL_VERSION: Byte = 1
    private const val TYPE_CLIPBOARD: Byte = 1
    private const val TYPE_FILE: Byte = 2
    private const val TYPE_PAIR_REQUEST: Byte = 3
    private const val TYPE_UNPAIR: Byte = 4
    private const val TYPE_FILE_HANDSHAKE: Byte = 5
    private const val ACK: Byte = 6
    private const val RESP_ACCEPTED: Byte = 7
    private const val RESP_REJECTED: Byte = 8
    private const val TYPE_REQUEST_FILE_DOWNLOAD: Byte = 9

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("[SyncSwift iOS Transfer] Coroutine error caught: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    actual val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    private var serverSocketFd: Int = -1
    private var isListening = false

    init {
        try {
            signal(SIGPIPE, SIG_IGN)
        } catch (_: Throwable) {}
        startForegroundServer()
    }

    private fun recordTransfer(item: TransferItem) {
        val current = _transfers.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _transfers.value = current.take(50)
    }

    private fun updateTransferProgress(id: String, bytes: Long, status: TransferStatus, localFilePath: String? = null) {
        if (status == TransferStatus.CANCELLED) {
            _transfers.value = _transfers.value.filter { it.id != id }
            return
        }
        _transfers.value = _transfers.value.map {
            if (it.id == id) {
                it.copy(bytesTransferred = bytes, status = status, localFilePath = localFilePath ?: it.localFilePath)
            } else it
        }
    }

    fun stopServer() {
        isListening = false
        val fd = serverSocketFd
        serverSocketFd = -1
        if (fd >= 0) {
            try {
                shutdown(fd, SHUT_RDWR)
                close(fd)
            } catch (_: Throwable) {}
        }
        println("[SyncSwift iOS] TransferEngine server stopped safely")
    }

    fun startForegroundServer() {
        if (isListening && serverSocketFd >= 0) return
        scope.launch(Dispatchers.Default) {
            try {
                if (serverSocketFd >= 0) {
                    close(serverSocketFd)
                    serverSocketFd = -1
                }
                serverSocketFd = socket(AF_INET, SOCK_STREAM, 0)
                if (serverSocketFd < 0) return@launch

                memScoped {
                    val opt = alloc<IntVar>()
                    opt.value = 1
                    setsockopt(serverSocketFd, SOL_SOCKET, SO_REUSEADDR, opt.ptr, sizeOf<IntVar>().convert())
                    setsockopt(serverSocketFd, SOL_SOCKET, SO_REUSEPORT, opt.ptr, sizeOf<IntVar>().convert())
                    val nosigpipe = alloc<IntVar>()
                    nosigpipe.value = 1
                    setsockopt(serverSocketFd, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                    val serverAddr = alloc<sockaddr_in>()
                    serverAddr.sin_family = AF_INET.convert()
                    serverAddr.sin_port = htons(52424.toUShort())
                    serverAddr.sin_addr.s_addr = INADDR_ANY

                    if (bind(serverSocketFd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                        close(serverSocketFd)
                        serverSocketFd = -1
                        return@launch
                    }

                    if (listen(serverSocketFd, 10) < 0) {
                        close(serverSocketFd)
                        serverSocketFd = -1
                        return@launch
                    }
                }

                isListening = true
                println("[SyncSwift iOS] TransferEngine listening on port 52424")

                while (isListening && serverSocketFd >= 0) {
                    var clientSock = -1
                    memScoped {
                        val clientAddr = alloc<sockaddr_in>()
                        val clientLen = alloc<socklen_tVar>()
                        clientLen.value = sizeOf<sockaddr_in>().convert()

                        clientSock = accept(serverSocketFd, clientAddr.ptr.reinterpret(), clientLen.ptr)
                    }
                    if (clientSock >= 0) {
                        handleIncomingClient(clientSock)
                    }
                }
            } catch (e: Throwable) {
                isListening = false
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readExact(sock: Int, buf: ByteArray, offset: Int, length: Int): Boolean {
        var readTotal = 0
        while (readTotal < length) {
            val r = buf.usePinned { pinned ->
                recv(sock, pinned.addressOf(offset + readTotal), (length - readTotal).convert(), 0)
            }
            if (r <= 0) return false
            readTotal += r.toInt()
        }
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readUTF(sock: Int): String? {
        val lenBuf = ByteArray(2)
        if (!readExact(sock, lenBuf, 0, 2)) return null
        val len = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)
        if (len == 0) return ""
        val strBuf = ByteArray(len)
        if (!readExact(sock, strBuf, 0, len)) return null
        return strBuf.decodeToString()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readInt(sock: Int): Int? {
        val intBuf = ByteArray(4)
        if (!readExact(sock, intBuf, 0, 4)) return null
        return ((intBuf[0].toInt() and 0xFF) shl 24) or
               ((intBuf[1].toInt() and 0xFF) shl 16) or
               ((intBuf[2].toInt() and 0xFF) shl 8) or
               (intBuf[3].toInt() and 0xFF)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readLong(sock: Int): Long? {
        val buf = ByteArray(8)
        if (!readExact(sock, buf, 0, 8)) return null
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (buf[i].toLong() and 0xFFL)
        }
        return value
    }

    private fun writeLong(value: Long): ByteArray {
        val buf = ByteArray(8)
        for (i in 0 until 8) {
            buf[i] = ((value ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        return buf
    }

    private fun writeUTF(str: String): ByteArray {
        val bytes = str.encodeToByteArray()
        val buf = ByteArray(2 + bytes.size)
        buf[0] = ((bytes.size shr 8) and 0xFF).toByte()
        buf[1] = (bytes.size and 0xFF).toByte()
        bytes.copyInto(buf, 2)
        return buf
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun handleIncomingClient(clientSock: Int) {
        scope.launch(Dispatchers.Default) {
            try {
                memScoped {
                    val nosigpipe = alloc<IntVar>()
                    nosigpipe.value = 1
                    setsockopt(clientSock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                    val tv = alloc<timeval>()
                    tv.tv_sec = 40
                    tv.tv_usec = 0
                    setsockopt(clientSock, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())

                    val header = ByteArray(2)
                    if (!readExact(clientSock, header, 0, 2)) return@memScoped
                    if (header[0] != PROTOCOL_VERSION) return@memScoped

                    when (header[1]) {
                        TYPE_PAIR_REQUEST -> {
                            val senderName = readUTF(clientSock) ?: return@memScoped
                            val senderHost = readUTF(clientSock) ?: return@memScoped
                            val senderPort = readInt(clientSock) ?: 52424
                            val senderOs = readUTF(clientSock) ?: "Unknown"

                            println("[SyncSwift iOS] Incoming pair request from $senderName ($senderHost:$senderPort)")

                            val request = IncomingPairRequest(
                                senderId = "$senderHost:$senderPort",
                                senderName = senderName,
                                senderHost = senderHost,
                                senderPort = senderPort,
                                senderOsType = senderOs
                            )

                            ConnectionManager.dispatchIncomingPairRequest(request)

                            val watchJob = scope.launch(Dispatchers.Default) {
                                val buf = ByteArray(1)
                                val r = buf.usePinned { pinned ->
                                    recv(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                if (r <= 0 || buf[0] == 10.toByte()) {
                                    ConnectionManager.cancelIncomingPairRequest()
                                }
                            }

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

                            val respByte = byteArrayOf(if (accepted) RESP_ACCEPTED else RESP_REJECTED)
                            val sent = respByte.usePinned { pinned ->
                                send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                            }

                            if (accepted) {
                                if (sent > 0) {
                                    ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                                    ConnectionManager.markDeviceConnected(request.senderId)
                                } else {
                                    ConnectionManager.markDeviceDisconnected("$senderHost:$senderPort")
                                    ConnectionManager.markDeviceDisconnected(request.senderId)
                                    dispatch_async(dispatch_get_main_queue()) {
                                        showNativeToast(currentAppCopy().requestCancelledBySender)
                                    }
                                }
                            }
                        }

                        TYPE_UNPAIR -> {
                            val senderHost = readUTF(clientSock) ?: ""
                            val senderPort = readInt(clientSock) ?: 52424
                            val senderId = readUTF(clientSock) ?: ""
                            val senderName = readUTF(clientSock) ?: ""
                            val peerDev = DiscoveredDevice(
                                id = senderId.ifBlank { "manual-$senderHost" },
                                name = senderName,
                                host = senderHost,
                                port = senderPort,
                                osType = "Mobile"
                            )
                            ConnectionManager.disconnectDevice(peerDev)
                        }

                        TYPE_FILE_HANDSHAKE -> {
                            val senderName = readUTF(clientSock) ?: return@memScoped
                            val fileName = readUTF(clientSock) ?: return@memScoped
                            val fileSize = readLong(clientSock) ?: return@memScoped
                            val mimeType = readUTF(clientSock) ?: "application/octet-stream"

                            println("[SyncSwift iOS] Incoming file request: $fileName ($fileSize bytes) from $senderName")

                            val transferRequest = IncomingTransferRequest(
                                senderName = senderName,
                                fileName = fileName,
                                fileSize = fileSize,
                                mimeType = mimeType
                            )

                            ConnectionManager.dispatchIncomingTransferRequest(transferRequest)

                            val watchJob = scope.launch(Dispatchers.Default) {
                                val buf = ByteArray(1)
                                val r = buf.usePinned { pinned ->
                                    recv(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                if (r <= 0 || buf[0] == 10.toByte()) {
                                    ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                                }
                            }

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
                                val respByte = byteArrayOf(RESP_REJECTED)
                                respByte.usePinned { pinned ->
                                    send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                return@memScoped
                            }

                            // Receiver accepted -> send acceptance to start streaming
                            val respAcc = byteArrayOf(RESP_ACCEPTED)
                            val sentAcc = respAcc.usePinned { pinned ->
                                send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                            }
                            if (sentAcc <= 0) {
                                dispatch_async(dispatch_get_main_queue()) {
                                    showNativeToast(currentAppCopy().transferCancelledBySender)
                                }
                                return@memScoped
                            }

                            val transferId = NSUUID().UUIDString
                            val item = TransferItem(
                                id = transferId,
                                fileName = fileName,
                                fileSizeBytes = fileSize,
                                mimeType = mimeType,
                                bytesTransferred = 0,
                                isSent = false,
                                peerName = senderName,
                                status = TransferStatus.IN_PROGRESS,
                                timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                            )
                            recordTransfer(item)

                            val savedPath = saveIncomingStream(fileName, fileSize, transferId, clientSock)
                            if (savedPath != null) {
                                updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED, savedPath)
                                val ackByte = byteArrayOf(ACK)
                                ackByte.usePinned { pinned ->
                                    send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                dispatch_async(dispatch_get_main_queue()) {
                                    val copy = currentAppCopy()
                                    showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                                }
                            } else {
                                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                            }
                        }

                        TYPE_FILE -> {
                            val senderName = readUTF(clientSock) ?: return@memScoped
                            val fileName = readUTF(clientSock) ?: return@memScoped
                            val fileSize = readLong(clientSock) ?: return@memScoped
                            val mimeType = readUTF(clientSock) ?: "application/octet-stream"

                            val transferId = NSUUID().UUIDString
                            val item = TransferItem(
                                id = transferId,
                                fileName = fileName,
                                fileSizeBytes = fileSize,
                                mimeType = mimeType,
                                bytesTransferred = 0,
                                isSent = false,
                                peerName = senderName,
                                status = TransferStatus.IN_PROGRESS,
                                timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                            )
                            recordTransfer(item)

                            val savedPath = saveIncomingStream(fileName, fileSize, transferId, clientSock)
                            if (savedPath != null) {
                                updateTransferProgress(transferId, fileSize, TransferStatus.COMPLETED, savedPath)
                                val ackByte = byteArrayOf(ACK)
                                ackByte.usePinned { pinned ->
                                    send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                dispatch_async(dispatch_get_main_queue()) {
                                    val copy = currentAppCopy()
                                    showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                                }
                            } else {
                                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                            }
                        }

                        TYPE_CLIPBOARD -> {
                            val str1 = readUTF(clientSock) ?: return@memScoped
                            val str2 = readUTF(clientSock)
                            val senderName = if (str2 != null) str1 else "Peer"
                            val receivedText = str2 ?: str1

                            val copy = currentAppCopy()
                            val preview = receivedText.take(35).replace("\n", " ")
                            val transferRequest = IncomingTransferRequest(
                                senderName = senderName,
                                fileName = "${copy.clipboard}: $preview",
                                fileSize = receivedText.encodeToByteArray().size.toLong(),
                                mimeType = "text/plain",
                                downloadUrl = receivedText
                            )

                            ConnectionManager.dispatchIncomingTransferRequest(transferRequest)

                            val accepted = try {
                                withTimeout(35000) {
                                    transferRequest.deferredResponse.await()
                                }
                            } catch (e: Exception) {
                                ConnectionManager.cancelIncomingTransferRequest(transferRequest.requestId)
                                false
                            }

                            if (!accepted) {
                                val rejByte = byteArrayOf(RESP_REJECTED)
                                rejByte.usePinned { pinned ->
                                    send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                                }
                                return@memScoped
                            }

                            applyIncomingClipboard(receivedText)

                            val item = TransferItem(
                                id = NSUUID().UUIDString,
                                fileName = "${copy.clipboard}: $preview",
                                fileSizeBytes = receivedText.encodeToByteArray().size.toLong(),
                                mimeType = "text/plain",
                                bytesTransferred = receivedText.encodeToByteArray().size.toLong(),
                                isSent = false,
                                peerName = senderName,
                                status = TransferStatus.COMPLETED,
                                timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                            )
                            recordTransfer(item)

                            val ackByte = byteArrayOf(RESP_ACCEPTED)
                            ackByte.usePinned { pinned ->
                                send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                            }
                        }

                        else -> {
                            println("[SyncSwift iOS] Unknown message type: ${header[1]}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[SyncSwift iOS] Client read error: ${e.message}")
            } finally {
                close(clientSock)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveIncomingStream(fileName: String, fileSize: Long, transferId: String, sock: Int): String? {
        val fileManager = NSFileManager.defaultManager
        val docDirs = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val docDir = docDirs.firstOrNull() as? NSURL ?: return null
        val targetUrl = docDir.URLByAppendingPathComponent(fileName) ?: return null
        val targetPath = targetUrl.path ?: return null

        val filePtr = fopen(targetPath, "wb") ?: return null
        try {
            val chunk = ByteArray(64 * 1024)
            var totalRead = 0L
            while (totalRead < fileSize) {
                val toRead = minOf(chunk.size.toLong(), fileSize - totalRead).toInt()
                val r = chunk.usePinned { pinned ->
                    recv(sock, pinned.addressOf(0), toRead.convert(), 0)
                }
                if (r <= 0) break
                chunk.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1.convert(), r.convert(), filePtr)
                }
                totalRead += r.toLong()
                updateTransferProgress(transferId, totalRead, TransferStatus.IN_PROGRESS)
            }
            fflush(filePtr)
            return if (totalRead >= fileSize) targetPath else null
        } catch (_: Exception) {
            return null
        } finally {
            fclose(filePtr)
        }
    }

    fun applyIncomingClipboard(text: String) {
        dispatch_async(dispatch_get_main_queue()) {
            try {
                UIPasteboard.generalPasteboard.string = text
                net.syncswift.clipboard.ClipboardHistoryManager.addClip(text)
                val copy = currentAppCopy()
                showNativeToast(copy.clipboardSynced.replace("%s", text.take(30)))
            } catch (e: Exception) {
                println("[SyncSwift iOS] Pasteboard write error: ${e.message}")
            }
        }

        val item = TransferItem(
            id = NSUUID().UUIDString,
            fileName = "Pano: ${text.take(30).replace("\n", " ")}",
            fileSizeBytes = text.length.toLong(),
            mimeType = "text/plain",
            bytesTransferred = text.length.toLong(),
            isSent = false,
            peerName = "Remote Device",
            status = TransferStatus.COMPLETED,
            timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
        )
        recordTransfer(item)
    }

    @OptIn(ExperimentalForeignApi::class)
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
        val copy = currentAppCopy()
        val transferId = NSUUID().UUIDString
        val item = TransferItem(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = fileBytes.size.toLong(),
            mimeType = mimeType,
            bytesTransferred = 0,
            isSent = true,
            peerName = targetDeviceName,
            status = TransferStatus.PENDING,
            timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
        )
        recordTransfer(item)

        var activeSocket: Int = -1
        var isCancelled = false
        var transferJob: Job? = null

        val cleanTarget = cleanDeviceDisplayName(targetDeviceName)
        val targetDev = net.syncswift.discovery.DeviceDiscoveryManager.devices.value.firstOrNull {
            cleanDeviceDisplayName(it.name) == cleanTarget
        }
        val isLocalDiscovered = targetDev != null && targetDev.isOnline && !targetDev.isRemote &&
                !targetHost.startsWith("remote-") && !ConnectionManager.isRemoteDevice(targetDev)

        val cancelTransfer: () -> Unit = {
            isCancelled = true
            transferJob?.cancel()
            OutgoingStatusManager.dismiss()
            if (activeSocket >= 0) {
                val cancelByte = byteArrayOf(10)
                cancelByte.usePinned { pinned ->
                    send(activeSocket, pinned.addressOf(0), 1.convert(), 0)
                }
                close(activeSocket)
                activeSocket = -1
            }
            updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
            ConnectionManager.markRequestCancelled(transferId)

            scope.launch(Dispatchers.Default) {
                val token = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)
                if (!token.isNullOrBlank()) {
                    val targetDev = DeviceDiscoveryManager.devices.value.find { it.host == targetHost || it.name == targetDeviceName }
                    val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                    ConnectionManager.sendFcmNotification(
                        token = token,
                        title = targetCopy.requestCancelledBySender,
                        body = targetCopy.transferCancelledBySender,
                        data = mapOf(
                            "type" to "file_cancelled",
                            "transferId" to transferId,
                            "senderName" to selfName
                        )
                    )
                }
            }
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

        transferJob = scope.launch(Dispatchers.Default) {
            val self = DeviceDiscoveryManager.getSelfDevice()
            val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)

            // 1. Direct Socket Transfer only if local discovered
            if (isLocalDiscovered && targetHost.isNotBlank() && targetHost != "127.0.0.1") {
                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock >= 0) {
                    activeSocket = sock
                    val directSuccess = memScoped {
                        val timeout = alloc<timeval>()
                        timeout.tv_sec = 2
                        timeout.tv_usec = 0
                        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                        val nosigpipe = alloc<IntVar>()
                        nosigpipe.value = 1
                        setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                        val serverAddr = alloc<sockaddr_in>()
                        serverAddr.sin_family = AF_INET.convert()
                        serverAddr.sin_port = htons(targetPort.toUShort())
                        val parsedIp = parseIpv4(targetHost)
                        if (parsedIp == null) {
                            close(sock)
                            activeSocket = -1
                            return@memScoped false
                        }
                        serverAddr.sin_addr.s_addr = parsedIp.convert()

                        if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                            close(sock)
                            activeSocket = -1
                            return@memScoped false
                        }

                        // Send Handshake: PROTOCOL_VERSION (1), TYPE_FILE_HANDSHAKE (5), selfName, fileName, fileSize, mimeType
                        val nameBytes = writeUTF(selfName)
                        val fileBytesUtf = writeUTF(fileName)
                        val sizeBytes = writeLong(fileBytes.size.toLong())
                        val mimeBytes = writeUTF(mimeType)
                        val handshakeSize = 2 + nameBytes.size + fileBytesUtf.size + sizeBytes.size + mimeBytes.size
                        val handshake = ByteArray(handshakeSize)
                        var hIdx = 0
                        handshake[hIdx++] = PROTOCOL_VERSION
                        handshake[hIdx++] = TYPE_FILE_HANDSHAKE
                        nameBytes.copyInto(handshake, hIdx); hIdx += nameBytes.size
                        fileBytesUtf.copyInto(handshake, hIdx); hIdx += fileBytesUtf.size
                        sizeBytes.copyInto(handshake, hIdx); hIdx += sizeBytes.size
                        mimeBytes.copyInto(handshake, hIdx); hIdx += mimeBytes.size

                        val sent = handshake.usePinned { pinned ->
                            send(sock, pinned.addressOf(0), handshake.size.convert(), 0)
                        }
                        if (sent <= 0) {
                            close(sock)
                            activeSocket = -1
                            return@memScoped false
                        }

                        // Wait for recipient acceptance
                        val respBuf = ByteArray(1)
                        val r = respBuf.usePinned { pinned ->
                            recv(sock, pinned.addressOf(0), 1.convert(), 0)
                        }
                        if (r <= 0 || respBuf[0] != RESP_ACCEPTED) {
                            close(sock)
                            activeSocket = -1
                            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                            OutgoingStatusManager.fail(copy.transferDeclined)
                            withContext(Dispatchers.Main) {
                                showNativeToast(copy.transferDeclined)
                                onError(copy.transferDeclined)
                            }
                            return@memScoped true // Handled rejection
                        }

                        // Receiver accepted -> stream file bytes
                        updateTransferProgress(transferId, 0, TransferStatus.IN_PROGRESS)
                        OutgoingStatusManager.updateProgress(
                            transferredBytes = 0L,
                            totalBytes = fileBytes.size.toLong(),
                            message = copy.transferringFile
                        )
                        val chunkSize = 64 * 1024
                        var offset = 0
                        while (offset < fileBytes.size) {
                            val len = minOf(chunkSize, fileBytes.size - offset)
                            val s = fileBytes.usePinned { pinned ->
                                send(sock, pinned.addressOf(offset), len.convert(), 0)
                            }
                            if (s <= 0) break
                            offset += s.toInt()
                            updateTransferProgress(transferId, offset.toLong(), TransferStatus.IN_PROGRESS)
                            OutgoingStatusManager.updateProgress(
                                transferredBytes = offset.toLong(),
                                totalBytes = fileBytes.size.toLong(),
                                message = copy.transferringFile
                            )
                        }

                        // Wait for completion ACK
                        val ackBuf = ByteArray(1)
                        val ackRes = ackBuf.usePinned { pinned ->
                            recv(sock, pinned.addressOf(0), 1.convert(), 0)
                        }
                        close(sock)
                        activeSocket = -1

                        if (ackRes > 0 && ackBuf[0] == ACK) {
                            updateTransferProgress(transferId, fileBytes.size.toLong(), TransferStatus.COMPLETED)
                            OutgoingStatusManager.completeSuccess(copy.fileSentSuccessfully.replace("%s", fileName))
                            withContext(Dispatchers.Main) {
                                showNativeToast(copy.fileSentSuccessfully.replace("%s", fileName))
                                onSuccess()
                            }
                            return@memScoped true
                        }
                        false
                    }
                    if (directSuccess) return@launch
                }
            }

            if (isCancelled || !isActive) return@launch

            // 2. Direct socket failed or remote -> Remote Transfer via Supabase Storage
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

            executeRemoteFileTransfer(
                targetDeviceName = targetDeviceName,
                targetHost = targetHost,
                fileName = fileName,
                fileBytes = fileBytes,
                mimeType = mimeType,
                transferId = transferId,
                onSuccess = onSuccess,
                onError = onError,
                isCancelledCheck = { isCancelled || !isActive },
                cancelAction = cancelTransfer
            )
        }
    }

    private suspend fun executeRemoteFileTransfer(
        targetDeviceName: String,
        targetHost: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        transferId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        isCancelledCheck: () -> Boolean = { false },
        cancelAction: (() -> Unit)? = null
    ) {
        val copy = currentAppCopy()
        OutgoingStatusManager.updateProgress(
            transferredBytes = 0L,
            totalBytes = fileBytes.size.toLong(),
            message = copy.remoteTransferUploading
        )

        val cleanTarget = cleanDeviceDisplayName(targetDeviceName)
        val token = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)

        val downloadUrl = uploadToSupabaseStorage(transferId, fileName, fileBytes, mimeType)
        if (isCancelledCheck()) return

        if (downloadUrl == null) {
            updateTransferProgress(transferId, 0, TransferStatus.FAILED)
            OutgoingStatusManager.fail(copy.transferFailed)
            withContext(Dispatchers.Main) {
                showNativeToast(copy.transferFailed)
                onError(copy.transferFailed)
            }
            return
        }

        if (isCancelledCheck()) return

        if (!token.isNullOrBlank()) {
            val self = DeviceDiscoveryManager.getSelfDevice()
            val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
            val targetDev = DeviceDiscoveryManager.devices.value.find { it.host == targetHost || it.name == targetDeviceName }
            val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
            val fcmSent = ConnectionManager.sendFcmNotification(
                token = token,
                title = targetCopy.notificationFileRequestTitle,
                body = targetCopy.notificationFileRequestBody.replace("%s", selfName).replace("%f", fileName),
                data = mapOf(
                    "type" to "file_request",
                    "transferId" to transferId,
                    "senderName" to selfName,
                    "fileName" to fileName,
                    "fileSize" to "${fileBytes.size}",
                    "mimeType" to mimeType,
                    "downloadUrl" to downloadUrl,
                    "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                    "timestamp" to "${currentTimeMs()}"
                )
            )
            if (isCancelledCheck()) return

            if (fcmSent) {
                OutgoingStatusManager.show(
                    type = OutgoingStatusType.FILE,
                    deviceName = targetDeviceName,
                    fileName = fileName,
                    totalBytes = fileBytes.size.toLong(),
                    step = OutgoingStep.WAITING_APPROVAL,
                    message = copy.transferNotificationSentWaiting,
                    onCancel = cancelAction
                )
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.transferNotificationSentWaiting)
                    onSuccess()
                }
                return
            }
        }

        if (isCancelledCheck()) return
        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
        OutgoingStatusManager.fail(copy.deviceNotAvailable)
        withContext(Dispatchers.Main) {
            showNativeToast(copy.deviceNotAvailable)
            onError(copy.deviceNotAvailable)
        }
    }

    private suspend fun uploadToSupabaseStorage(
        transferId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): String? {
        val supabaseUrl = PlatformConfig.supabaseUrl
        val serviceKey = PlatformConfig.supabaseServiceRoleKey.ifBlank { PlatformConfig.supabaseAnonKey }
        if (supabaseUrl.isBlank() || serviceKey.isBlank()) return null

        val ext = fileName.substringAfterLast(".", "")
        val objectPath = "$transferId.$ext"
        val url = NSURL.URLWithString("$supabaseUrl/storage/v1/object/syncswift_transfers/$objectPath") ?: return null

        return suspendCancellableCoroutine { continuation ->
            try {
                val req = NSMutableURLRequest.requestWithURL(url).apply {
                    setHTTPMethod("POST")
                    setTimeoutInterval(30.0)
                    setValue(if (mimeType.isNotBlank()) mimeType else "application/octet-stream", forHTTPHeaderField = "Content-Type")
                    setValue(serviceKey, forHTTPHeaderField = "apikey")
                    setValue("Bearer $serviceKey", forHTTPHeaderField = "Authorization")
                    setValue("true", forHTTPHeaderField = "x-upsert")
                }

                val data = fileBytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = fileBytes.size.convert())
                }
                req.setHTTPBody(data)

                val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { _, resp, err ->
                    try {
                        val success = if (err == null && resp != null) {
                            val http = resp as? NSHTTPURLResponse
                            (http?.statusCode?.toInt() ?: 0) in 200..299
                        } else false
                        val publicUrl = if (success) {
                            "$supabaseUrl/storage/v1/object/public/syncswift_transfers/$objectPath"
                        } else null
                        if (continuation.isActive) continuation.resume(publicUrl)
                    } catch (_: Throwable) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { task.cancel() }
                task.resume()
            } catch (_: Throwable) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun sendClipboard(
        targetHost: String,
        targetPort: Int,
        targetDeviceName: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val transferId = NSUUID().UUIDString
        val copy = currentAppCopy()
        var isCancelled = false
        var clipJob: Job? = null

        val cleanTarget = cleanDeviceDisplayName(targetDeviceName)
        val targetDev = DeviceDiscoveryManager.devices.value.firstOrNull {
            cleanDeviceDisplayName(it.name) == cleanTarget
        }
        val isLocalDiscovered = targetDev != null && targetDev.isOnline && !targetDev.isRemote &&
                !targetHost.startsWith("remote-") && !ConnectionManager.isRemoteDevice(targetDev)

        val cancelClipboard: () -> Unit = {
            isCancelled = true
            clipJob?.cancel()
            updateTransferProgress(transferId, 0, TransferStatus.CANCELLED)
            ConnectionManager.markRequestCancelled(transferId)
            OutgoingStatusManager.dismiss()

            scope.launch(Dispatchers.Default) {
                val token = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)
                if (!token.isNullOrBlank()) {
                    val targetDev = DeviceDiscoveryManager.devices.value.find { it.host == targetHost || it.name == targetDeviceName }
                    val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
                    val self = DeviceDiscoveryManager.getSelfDevice()
                    val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
                    ConnectionManager.sendFcmNotification(
                        token = token,
                        title = targetCopy.requestCancelledBySender,
                        body = targetCopy.clipboardCancelledBySender,
                        data = mapOf(
                            "type" to "clipboard_cancelled",
                            "transferId" to transferId,
                            "senderName" to selfName
                        )
                    )
                }
            }
        }

        OutgoingStatusManager.show(
            type = OutgoingStatusType.FILE,
            deviceName = targetDeviceName,
            fileName = "${copy.clipboard}: ${text.take(30).replace("\n", " ")}",
            totalBytes = text.encodeToByteArray().size.toLong(),
            step = OutgoingStep.WAITING_APPROVAL,
            message = copy.waitingForRecipientApproval,
            onCancel = cancelClipboard
        )

        clipJob = scope.launch(Dispatchers.Default) {
            val textBytes = text.encodeToByteArray()
            val item = TransferItem(
                id = transferId,
                fileName = text.take(40).replace("\n", " "),
                fileSizeBytes = textBytes.size.toLong(),
                mimeType = "text/plain",
                bytesTransferred = 0,
                isSent = true,
                peerName = targetDeviceName,
                status = TransferStatus.IN_PROGRESS,
                timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
            )
            recordTransfer(item)

            if (isLocalDiscovered) {
                val self = DeviceDiscoveryManager.getSelfDevice()
                val selfName = cleanDeviceDisplayName(self?.name ?: "iPhone")
                val nameBytes = selfName.encodeToByteArray()

                val size = 2 + (2 + nameBytes.size) + (2 + textBytes.size)
                val payload = ByteArray(size)
                var idx = 0
                payload[idx++] = PROTOCOL_VERSION
                payload[idx++] = TYPE_CLIPBOARD
                payload[idx++] = ((nameBytes.size shr 8) and 0xFF).toByte()
                payload[idx++] = (nameBytes.size and 0xFF).toByte()
                nameBytes.copyInto(payload, idx); idx += nameBytes.size
                payload[idx++] = ((textBytes.size shr 8) and 0xFF).toByte()
                payload[idx++] = (textBytes.size and 0xFF).toByte()
                textBytes.copyInto(payload, idx); idx += textBytes.size

                val socketOk = sendViaSocket(targetHost, targetPort, payload)
                if (socketOk && !isCancelled && isActive) {
                    updateTransferProgress(transferId, textBytes.size.toLong(), TransferStatus.COMPLETED)
                    OutgoingStatusManager.completeSuccess(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                    withContext(Dispatchers.Main) {
                        showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                        onSuccess()
                    }
                    return@launch
                }
            }

            if (isCancelled || !isActive) return@launch

            // Fallback or Direct: FCM Push Relay
            val fcmSent = sendViaFcmRelay(targetDeviceName, targetHost, text, transferId)
            if (isCancelled || !isActive) return@launch

            if (fcmSent) {
                OutgoingStatusManager.show(
                    type = OutgoingStatusType.FILE,
                    deviceName = targetDeviceName,
                    fileName = "${copy.clipboard}: ${text.take(30).replace("\n", " ")}",
                    totalBytes = textBytes.size.toLong(),
                    step = OutgoingStep.WAITING_APPROVAL,
                    message = copy.transferNotificationSentWaiting,
                    onCancel = cancelClipboard
                )
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.transferNotificationSentWaiting)
                    onSuccess()
                }
            } else {
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                OutgoingStatusManager.fail(copy.deviceNotAvailable)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.deviceNotAvailable)
                    onError(copy.deviceNotAvailable)
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sendViaSocket(host: String, port: Int, payload: ByteArray): Boolean {
        val sock = socket(AF_INET, SOCK_STREAM, 0)
        if (sock < 0) return false

        return memScoped {
            val timeout = alloc<timeval>()
            timeout.tv_sec = 35
            timeout.tv_usec = 0
            setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
            setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
            val nosigpipe = alloc<IntVar>()
            nosigpipe.value = 1
            setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

            val serverAddr = alloc<sockaddr_in>()
            serverAddr.sin_family = AF_INET.convert()
            serverAddr.sin_port = htons(port.toUShort())
            val parsedIp = parseIpv4(host)
            if (parsedIp == null) {
                close(sock)
                return@memScoped false
            }
            serverAddr.sin_addr.s_addr = parsedIp.convert()

            if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                close(sock)
                return@memScoped false
            }

            var totalSent = 0
            while (totalSent < payload.size) {
                val sent = payload.usePinned { pinned ->
                    send(sock, pinned.addressOf(totalSent), (payload.size - totalSent).convert(), 0)
                }
                if (sent <= 0) {
                    close(sock)
                    return@memScoped false
                }
                totalSent += sent.toInt()
            }

            val ackBuf = ByteArray(1)
            val received = ackBuf.usePinned { pinned ->
                recv(sock, pinned.addressOf(0), 1.convert(), 0)
            }
            close(sock)
            received > 0 && (ackBuf[0] == ACK || ackBuf[0] == RESP_ACCEPTED)
        }
    }

    private suspend fun sendViaFcmRelay(targetName: String, targetHost: String, text: String, transferId: String): Boolean {
        val cleanTarget = cleanDeviceDisplayName(targetName)
        val token = ConnectionManager.queryPushTokenFromSupabase(targetHost, cleanTarget)
        if (token.isNullOrBlank()) return false

        val self = DeviceDiscoveryManager.getSelfDevice()
        val selfName = cleanDeviceDisplayName(self?.name ?: UIDevice.currentDevice.name)
        val targetDev = DeviceDiscoveryManager.devices.value.find { it.host == targetHost || it.name == targetName }
        val targetCopy = net.syncswift.copyForLanguageCode(targetDev?.language)
        val preview = text.trim().replace("\n", " ").take(100)
        val notificationBody = targetCopy.notificationFileRequestBody
            .replace("%s", selfName)
            .replace("%s", "${currentAppCopy().clipboard}: $preview")
            .replace("(%s)", "")
        return ConnectionManager.sendFcmNotification(
            token = token,
            title = targetCopy.notificationFileRequestTitle,
            body = notificationBody,
            data = mapOf(
                "type" to "clipboard_request",
                "transferId" to transferId,
                "text" to text,
                "content" to text,
                "body" to preview,
                "senderName" to selfName,
                "senderLanguage" to (SessionManager.getSavedLanguage() ?: net.syncswift.getSystemLanguageCode()),
                "timestamp" to "${(NSDate().timeIntervalSince1970 * 1000).toLong()}"
            )
        )
    }

    fun downloadRemoteFile(
        downloadUrl: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        transferId: String,
        senderName: String
    ) {
        scope.launch(Dispatchers.Default) {
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
                    timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                )
                recordTransfer(item)

                val url = NSURL.URLWithString(downloadUrl) ?: run {
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    return@launch
                }

                val task = NSURLSession.sharedSession.downloadTaskWithURL(url) { tempLocalUrl, resp, err ->
                    if (err == null && tempLocalUrl != null) {
                        val fileManager = NSFileManager.defaultManager
                        val docDirs = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                        val docDir = docDirs.firstOrNull() as? NSURL
                        if (docDir != null) {
                            val destUrl = docDir.URLByAppendingPathComponent(fileName)
                            if (destUrl != null) {
                                fileManager.removeItemAtURL(destUrl, null)
                                val moved = fileManager.moveItemAtURL(tempLocalUrl, destUrl, null)
                                val destPath = destUrl.path
                                if (moved && destPath != null) {
                                    val finalSize = if (fileSize > 0) fileSize else (fileManager.attributesOfItemAtPath(destPath, null)?.get(NSFileSize) as? Long ?: 0L)
                                    updateTransferProgress(transferId, finalSize, TransferStatus.COMPLETED, destPath)
                                    dispatch_async(dispatch_get_main_queue()) {
                                        val copy = currentAppCopy()
                                        showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                                    }
                                    return@downloadTaskWithURL
                                }
                            }
                        }
                    }
                    updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                }
                task.resume()
            } catch (_: Throwable) {
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun downloadStagedFile(
        senderHost: String,
        senderPort: Int,
        senderName: String,
        transferId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ) {
        scope.launch(Dispatchers.Default) {
            try {
                if (senderHost.isBlank()) return@launch

                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock < 0) return@launch

                memScoped {
                    val timeout = alloc<timeval>()
                    timeout.tv_sec = 20
                    timeout.tv_usec = 0
                    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())
                    val nosigpipe = alloc<IntVar>()
                    nosigpipe.value = 1
                    setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, nosigpipe.ptr, sizeOf<IntVar>().convert())

                    val serverAddr = alloc<sockaddr_in>()
                    serverAddr.sin_family = AF_INET.convert()
                    serverAddr.sin_port = htons(senderPort.toUShort())
                    val parsedIp = parseIpv4(senderHost) ?: run {
                        close(sock)
                        return@memScoped
                    }
                    serverAddr.sin_addr.s_addr = parsedIp.convert()

                    if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                        close(sock)
                        return@memScoped
                    }

                    val idBytes = writeUTF(transferId)
                    val modelBytes = writeUTF(UIDevice.currentDevice.name)
                    val reqPacket = ByteArray(2 + idBytes.size + modelBytes.size)
                    var idx = 0
                    reqPacket[idx++] = PROTOCOL_VERSION
                    reqPacket[idx++] = TYPE_REQUEST_FILE_DOWNLOAD
                    idBytes.copyInto(reqPacket, idx); idx += idBytes.size
                    modelBytes.copyInto(reqPacket, idx); idx += modelBytes.size

                    val sent = reqPacket.usePinned { pinned ->
                        send(sock, pinned.addressOf(0), reqPacket.size.convert(), 0)
                    }
                    if (sent <= 0) {
                        close(sock)
                        return@memScoped
                    }

                    val resp = ByteArray(1)
                    val r = resp.usePinned { recv(sock, it.addressOf(0), 1.convert(), 0) }
                    if (r <= 0 || resp[0] != RESP_ACCEPTED) {
                        close(sock)
                        return@memScoped
                    }

                    val sizeBuf = ByteArray(8)
                    val rSize = sizeBuf.usePinned { recv(sock, it.addressOf(0), 8.convert(), 0) }
                    val incomingSize = if (rSize.toInt() == 8) {
                        ((sizeBuf[0].toLong() and 0xFF) shl 56) or
                        ((sizeBuf[1].toLong() and 0xFF) shl 48) or
                        ((sizeBuf[2].toLong() and 0xFF) shl 40) or
                        ((sizeBuf[3].toLong() and 0xFF) shl 32) or
                        ((sizeBuf[4].toLong() and 0xFF) shl 24) or
                        ((sizeBuf[5].toLong() and 0xFF) shl 16) or
                        ((sizeBuf[6].toLong() and 0xFF) shl 8) or
                        (sizeBuf[7].toLong() and 0xFF)
                    } else fileSize

                    val finalSize = if (incomingSize > 0) incomingSize else fileSize

                    val item = TransferItem(
                        id = transferId,
                        fileName = fileName,
                        fileSizeBytes = finalSize,
                        mimeType = mimeType,
                        bytesTransferred = 0,
                        isSent = false,
                        peerName = senderName,
                        status = TransferStatus.IN_PROGRESS,
                        timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                    )
                    recordTransfer(item)

                    val savedPath = saveIncomingStream(fileName, finalSize, transferId, sock)
                    close(sock)

                    if (savedPath != null) {
                        updateTransferProgress(transferId, finalSize, TransferStatus.COMPLETED, savedPath)
                        dispatch_async(dispatch_get_main_queue()) {
                            val copy = currentAppCopy()
                            showNativeToast(copy.fileReceivedSuccessfully.replace("%s", fileName))
                        }
                    } else {
                        updateTransferProgress(transferId, 0, TransferStatus.FAILED)
                    }
                }
            } catch (_: Exception) {
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
            }
        }
    }
}
