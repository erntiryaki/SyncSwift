package net.syncswift.webrtc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.clipboard.ClipboardHistoryManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC DataChannel Engine.
 * Manages chunk assembly, flow control, progress updates, and clipboard synchronization.
 */
object WebRtcDataChannelEngine {
    private const val TAG = "WebRtcDataChannel"
    const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB SCTP DataChannel buffer

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(WebRtcState.IDLE)
    val connectionState: StateFlow<WebRtcState> = _connectionState.asStateFlow()

    // Active incoming assembly buffers: transferId -> AssemblyContext
    private val incomingTransfers = ConcurrentHashMap<String, IncomingAssembly>()

    data class IncomingAssembly(
        val transferId: String,
        val fileName: String,
        val totalBytes: Long,
        val mimeType: String,
        val totalChunks: Int,
        val receivedChunks: ConcurrentHashMap<Int, ByteArray> = ConcurrentHashMap(),
        var tempFile: File? = null,
        var outputStream: FileOutputStream? = null
    )

    private var onFileReceivedCallback: ((String, ByteArray, String, String) -> Unit)? = null
    private var onProgressCallback: ((String, Int) -> Unit)? = null

    fun setFileReceivedListener(listener: (fileName: String, fileBytes: ByteArray, mimeType: String, peerName: String) -> Unit) {
        onFileReceivedCallback = listener
    }

    fun setProgressListener(listener: (transferId: String, progressPercent: Int) -> Unit) {
        onProgressCallback = listener
    }

    /**
     * Parse and route incoming binary frame from WebRTC DataChannel.
     */
    fun onBinaryFrameReceived(bytes: ByteArray, peerName: String) {
        if (bytes.size < 4) return
        val buffer = ByteBuffer.wrap(bytes)
        val frameType = buffer.int

        when (frameType) {
            1 -> { // START_TRANSFER
                val jsonLen = buffer.int
                val jsonBytes = ByteArray(jsonLen)
                buffer.get(jsonBytes)
                val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

                val transferId = json.getString("transferId")
                val fileName = json.getString("fileName")
                val fileSize = json.getLong("fileSize")
                val mimeType = json.optString("mimeType", "application/octet-stream")
                val totalChunks = json.getInt("totalChunks")

                val assembly = IncomingAssembly(
                    transferId = transferId,
                    fileName = fileName,
                    totalBytes = fileSize,
                    mimeType = mimeType,
                    totalChunks = totalChunks
                )
                incomingTransfers[transferId] = assembly
                onProgressCallback?.invoke(transferId, 0)
            }
            2 -> { // FILE_CHUNK
                val transferIdLen = buffer.int
                val idBytes = ByteArray(transferIdLen)
                buffer.get(idBytes)
                val transferId = String(idBytes, Charsets.UTF_8)
                val chunkIndex = buffer.int
                val chunkPayloadSize = buffer.int
                val payload = ByteArray(chunkPayloadSize)
                buffer.get(payload)

                val assembly = incomingTransfers[transferId] ?: return
                assembly.receivedChunks[chunkIndex] = payload

                val progress = assembly.receivedChunks.size.toFloat() / assembly.totalChunks.toFloat()
                val percent = (progress * 100).toInt()
                onProgressCallback?.invoke(transferId, percent)

                // Check completion
                if (assembly.receivedChunks.size >= assembly.totalChunks) {
                    finalizeIncomingTransfer(assembly, peerName)
                }
            }
            3 -> { // CLIPBOARD_SYNC
                val textLen = buffer.int
                val textBytes = ByteArray(textLen)
                buffer.get(textBytes)
                val clipText = String(textBytes, Charsets.UTF_8)
                if (clipText.isNotBlank()) {
                    ClipboardHistoryManager.addClip(clipText)
                }
            }
        }
    }

    /**
     * Frames a file into 64KB binary chunks for streaming across DataChannel.
     */
    fun createChunkFrames(
        transferId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val totalBytes = fileBytes.size
        val totalChunks = if (totalBytes == 0) 1 else ((totalBytes + DEFAULT_CHUNK_SIZE - 1) / DEFAULT_CHUNK_SIZE)

        // 1. Frame Start Metadata
        val metaJson = JSONObject().apply {
            put("transferId", transferId)
            put("fileName", fileName)
            put("fileSize", totalBytes.toLong())
            put("mimeType", mimeType)
            put("totalChunks", totalChunks)
            put("chunkSize", DEFAULT_CHUNK_SIZE)
        }.toString().toByteArray(Charsets.UTF_8)

        val startBuffer = ByteBuffer.allocate(4 + 4 + metaJson.size)
        startBuffer.putInt(1) // Type 1: START_TRANSFER
        startBuffer.putInt(metaJson.size)
        startBuffer.put(metaJson)
        frames.add(startBuffer.array())

        // 2. Data Chunks
        val transferIdBytes = transferId.toByteArray(Charsets.UTF_8)
        var offset = 0
        var chunkIndex = 0

        while (offset < totalBytes || (totalBytes == 0 && chunkIndex == 0)) {
            val length = minOf(DEFAULT_CHUNK_SIZE, totalBytes - offset)
            val chunkBuffer = ByteBuffer.allocate(4 + 4 + transferIdBytes.size + 4 + 4 + length)
            chunkBuffer.putInt(2) // Type 2: FILE_CHUNK
            chunkBuffer.putInt(transferIdBytes.size)
            chunkBuffer.put(transferIdBytes)
            chunkBuffer.putInt(chunkIndex)
            chunkBuffer.putInt(length)
            if (length > 0) {
                chunkBuffer.put(fileBytes, offset, length)
            }
            frames.add(chunkBuffer.array())

            offset += length
            chunkIndex++
            if (totalBytes == 0) break
        }

        return frames
    }

    /**
     * Frames clipboard text into a binary message.
     */
    fun createClipboardFrame(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + textBytes.size)
        buffer.putInt(3) // Type 3: CLIPBOARD_SYNC
        buffer.putInt(textBytes.size)
        buffer.put(textBytes)
        return buffer.array()
    }

    private fun finalizeIncomingTransfer(assembly: IncomingAssembly, peerName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val fullBytes = ByteArrayOutputStream()
                for (i in 0 until assembly.totalChunks) {
                    val chunk = assembly.receivedChunks[i] ?: ByteArray(0)
                    fullBytes.write(chunk)
                }

                val finalData = fullBytes.toByteArray()
                onFileReceivedCallback?.invoke(assembly.fileName, finalData, assembly.mimeType, peerName)
                incomingTransfers.remove(assembly.transferId)
            } catch (_: Exception) {}
        }
    }
}
