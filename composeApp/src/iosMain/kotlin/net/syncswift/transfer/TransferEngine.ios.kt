package net.syncswift.transfer

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.currentAppCopy
import net.syncswift.showNativeToast
import platform.Foundation.*
import platform.UIKit.UIPasteboard
import platform.darwin.*
import platform.posix.*
import kotlin.Byte

@OptIn(ExperimentalForeignApi::class)
actual object TransferEngine {
    private fun htons(s: UShort): UShort = (((s.toInt() and 0xFF) shl 8) or ((s.toInt() ushr 8) and 0xFF)).toUShort()
    private const val PROTOCOL_VERSION: kotlin.Byte = 1
    private const val TYPE_CLIPBOARD: kotlin.Byte = 1
    private const val ACK: kotlin.Byte = 6

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

    @OptIn(ExperimentalForeignApi::class)
    private fun startForegroundServer() {
        if (isListening) return
        scope.launch(Dispatchers.Default) {
            try {
                val sock = socket(AF_INET, SOCK_STREAM, 0)
                if (sock < 0) return@launch
                serverSocketFd = sock

                memScoped {
                    val opt = alloc<IntVar>()
                    opt.value = 1
                    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, opt.ptr, sizeOf<IntVar>().convert())
                    setsockopt(sock, SOL_SOCKET, SO_REUSEPORT, opt.ptr, sizeOf<IntVar>().convert())
                    setsockopt(sock, SOL_SOCKET, SO_NOSIGPIPE, opt.ptr, sizeOf<IntVar>().convert())

                    val serverAddr = alloc<sockaddr_in>()
                    serverAddr.sin_family = AF_INET.convert()
                    serverAddr.sin_port = htons(52424.toUShort())
                    serverAddr.sin_addr.s_addr = INADDR_ANY

                    if (bind(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                        close(sock)
                        serverSocketFd = -1
                        return@launch
                    }

                    if (listen(sock, 10) != 0) {
                        close(sock)
                        serverSocketFd = -1
                        return@launch
                    }
                }

                isListening = true

                while (isListening && serverSocketFd >= 0) {
                    val clientSock = memScoped {
                        val clientAddr = alloc<sockaddr_in>()
                        val clientLen = alloc<socklen_tVar>()
                        clientLen.value = sizeOf<sockaddr_in>().convert()
                        accept(serverSocketFd, clientAddr.ptr.reinterpret(), clientLen.ptr)
                    }

                    if (clientSock >= 0) {
                        memScoped {
                            val opt = alloc<IntVar>()
                            opt.value = 1
                            setsockopt(clientSock, SOL_SOCKET, SO_NOSIGPIPE, opt.ptr, sizeOf<IntVar>().convert())
                        }
                        handleIncomingClient(clientSock)
                    } else {
                        val err = errno
                        if (err == EBADF || err == ENOTSOCK || err == EINVAL || serverSocketFd < 0) {
                            break
                        }
                        delay(500)
                    }
                }
            } catch (e: Throwable) {
                isListening = false
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun handleIncomingClient(clientSock: Int) {
        scope.launch(Dispatchers.Default) {
            try {
                memScoped {
                    val header = ByteArray(4)
                    var readTotal = 0
                    while (readTotal < 4) {
                        val r = header.usePinned { pinned ->
                            recv(clientSock, pinned.addressOf(readTotal), (4 - readTotal).convert(), 0)
                        }
                        if (r <= 0) break
                        readTotal += r.toInt()
                    }

                    if (readTotal == 4 && header[0] == PROTOCOL_VERSION && header[1] == TYPE_CLIPBOARD) {
                        val len = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
                        val textBuf = ByteArray(len)
                        var textRead = 0
                        while (textRead < len) {
                            val r = textBuf.usePinned { pinned ->
                                recv(clientSock, pinned.addressOf(textRead), (len - textRead).convert(), 0)
                            }
                            if (r <= 0) break
                            textRead += r.toInt()
                        }

                        val receivedText = textBuf.decodeToString()
                        applyIncomingClipboard(receivedText)

                        // Send ACK
                        val ackByte = byteArrayOf(ACK)
                        ackByte.usePinned { pinned ->
                            send(clientSock, pinned.addressOf(0), 1.convert(), 0)
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
            fileName = text.take(40).replace("\n", " "),
            fileSizeBytes = text.encodeToByteArray().size.toLong(),
            mimeType = "text/plain",
            bytesTransferred = text.encodeToByteArray().size.toLong(),
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

            val len = textBytes.size
            val payload = ByteArray(4 + len)
            payload[0] = PROTOCOL_VERSION
            payload[1] = TYPE_CLIPBOARD
            payload[2] = ((len shr 8) and 0xFF).toByte()
            payload[3] = (len and 0xFF).toByte()
            textBytes.copyInto(payload, 4)

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
            inet_pton(AF_INET, host, serverAddr.sin_addr.ptr)

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
            received > 0 && ackBuf[0] == ACK
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
