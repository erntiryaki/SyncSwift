package net.syncswift.transfer

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.IncomingPairRequest
import net.syncswift.currentAppCopy
import net.syncswift.discovery.DeviceDiscoveryManager
import net.syncswift.discovery.cleanDeviceDisplayName
import net.syncswift.showNativeToast
import platform.Foundation.*
import platform.UIKit.UIPasteboard
import platform.darwin.*
import platform.posix.*
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
    private const val TYPE_PAIR_REQUEST: Byte = 3
    private const val ACK: Byte = 6
    private const val RESP_ACCEPTED: Byte = 7
    private const val RESP_REJECTED: Byte = 8

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
        _transfers.value = _transfers.value.map {
            if (it.id == id) {
                it.copy(bytesTransferred = bytes, status = status, localFilePath = localFilePath ?: it.localFilePath)
            } else it
        }
    }

    fun startForegroundServer() {
        if (isListening) return
        scope.launch(Dispatchers.Default) {
            try {
                serverSocketFd = socket(AF_INET, SOCK_STREAM, 0)
                if (serverSocketFd < 0) return@launch

                memScoped {
                    val opt = alloc<IntVar>()
                    opt.value = 1
                    setsockopt(serverSocketFd, SOL_SOCKET, SO_REUSEADDR, opt.ptr, sizeOf<IntVar>().convert())

                    val serverAddr = alloc<sockaddr_in>()
                    serverAddr.sin_family = AF_INET.convert()
                    serverAddr.sin_port = htons(52424.toUShort())
                    serverAddr.sin_addr.s_addr = INADDR_ANY

                    if (bind(serverSocketFd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                        close(serverSocketFd)
                        return@launch
                    }

                    if (listen(serverSocketFd, 10) < 0) {
                        close(serverSocketFd)
                        return@launch
                    }

                    isListening = true
                    println("[SyncSwift iOS] TransferEngine listening on port 52424")

                    while (isListening) {
                        val clientAddr = alloc<sockaddr_in>()
                        val clientLen = alloc<socklen_tVar>()
                        clientLen.value = sizeOf<sockaddr_in>().convert()

                        val clientSock = accept(serverSocketFd, clientAddr.ptr.reinterpret(), clientLen.ptr)
                        if (clientSock >= 0) {
                            handleIncomingClient(clientSock)
                        }
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
    private fun handleIncomingClient(clientSock: Int) {
        scope.launch(Dispatchers.Default) {
            try {
                memScoped {
                    val tv = alloc<timeval>()
                    tv.tv_sec = 35
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

                            val accepted = try {
                                withTimeout(30000) {
                                    request.deferredResponse.await()
                                }
                            } catch (e: Exception) {
                                ConnectionManager.cancelIncomingPairRequest()
                                false
                            }

                            val respByte = byteArrayOf(if (accepted) RESP_ACCEPTED else RESP_REJECTED)
                            respByte.usePinned { pinned ->
                                send(clientSock, pinned.addressOf(0), 1.convert(), 0)
                            }

                            if (accepted) {
                                ConnectionManager.markDeviceConnected("$senderHost:$senderPort")
                            }
                        }

                        TYPE_CLIPBOARD -> {
                            val str1 = readUTF(clientSock) ?: return@memScoped
                            // Check if sender also sent text (when str1 is senderName)
                            val str2 = readUTF(clientSock)
                            val receivedText = str2 ?: str1

                            applyIncomingClipboard(receivedText)

                            val ackByte = byteArrayOf(ACK)
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

    fun applyIncomingClipboard(text: String) {
        dispatch_async(dispatch_get_main_queue()) {
            try {
                UIPasteboard.generalPasteboard.string = text
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
        onError("iOS file sending not supported yet")
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
        scope.launch {
            val transferId = NSUUID().UUIDString
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

            val self = DeviceDiscoveryManager.getSelfDevice()
            val selfName = cleanDeviceDisplayName(self?.name ?: "iPhone")
            val nameBytes = selfName.encodeToByteArray()

            val size = 2 + (2 + nameBytes.size) + (2 + textBytes.size)
            val payload = ByteArray(size)
            var idx = 0
            payload[idx++] = PROTOCOL_VERSION
            payload[idx++] = TYPE_CLIPBOARD
            // writeUTF(selfName)
            payload[idx++] = ((nameBytes.size shr 8) and 0xFF).toByte()
            payload[idx++] = (nameBytes.size and 0xFF).toByte()
            nameBytes.copyInto(payload, idx); idx += nameBytes.size
            // writeUTF(text)
            payload[idx++] = ((textBytes.size shr 8) and 0xFF).toByte()
            payload[idx++] = (textBytes.size and 0xFF).toByte()
            textBytes.copyInto(payload, idx); idx += textBytes.size

            val socketOk = sendViaSocket(targetHost, targetPort, payload)
            if (socketOk) {
                val copy = currentAppCopy()
                updateTransferProgress(transferId, textBytes.size.toLong(), TransferStatus.COMPLETED)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                    onSuccess()
                }
                return@launch
            }

            // Fallback: FCM Push Relay
            val fcmSent = sendViaFcmRelay(targetDeviceName, targetHost, text)
            if (fcmSent) {
                val copy = currentAppCopy()
                updateTransferProgress(transferId, textBytes.size.toLong(), TransferStatus.COMPLETED)
                withContext(Dispatchers.Main) {
                    showNativeToast(copy.clipboardSentSuccess.replace("%s", text.take(30)))
                    onSuccess()
                }
            } else {
                val copy = currentAppCopy()
                updateTransferProgress(transferId, 0, TransferStatus.FAILED)
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
            timeout.tv_sec = 4
            timeout.tv_usec = 0
            setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
            setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, timeout.ptr, sizeOf<timeval>().convert())

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

    private fun sendViaFcmRelay(targetName: String, targetHost: String, text: String): Boolean {
        // Query push token & send push notification via syncswift API
        return try {
            val urlString = "https://api.syncswift.net/api/fcm/send"
            val url = NSURL.URLWithString(urlString) ?: return false
            val request = NSMutableURLRequest.requestWithURL(url)
            request.setHTTPMethod("POST")
            request.setValue("application/json", forHTTPHeaderField = "Content-Type")

            val jsonBody = """
                {
                    "targetName": "$targetName",
                    "targetHost": "$targetHost",
                    "title": "SyncSwift Ortak Pano",
                    "body": "${text.take(30).replace("\"", "\\\"")}",
                    "data": {
                        "type": "clipboard_sync",
                        "text": "${text.replace("\"", "\\\"").replace("\n", "\\n")}",
                        "senderName": "iOS Device"
                    }
                }
            """.trimIndent()

            val data = (jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            request.setHTTPBody(data)

            var success = false
            val semaphore = dispatch_semaphore_create(0)
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { _, response, error ->
                if (error == null && response != null) {
                    val httpResponse = response as? NSHTTPURLResponse
                    if (httpResponse?.statusCode?.toInt() in 200..299) {
                        success = true
                    }
                }
                dispatch_semaphore_signal(semaphore)
            }
            task.resume()
            dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 5_000_000_000L))
            success
        } catch (e: Exception) {
            false
        }
    }
}
