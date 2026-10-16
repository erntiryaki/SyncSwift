package net.syncswift.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class DocumentPickerDelegate(
    private val onFileSelected: (name: String, bytes: ByteArray, mimeType: String) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val canAccess = url.startAccessingSecurityScopedResource()
        try {
            val fileName = url.lastPathComponent ?: "file"
            val fileData = NSData.dataWithContentsOfURL(url) ?: return
            val length = fileData.length.toInt()
            if (length <= 0) return

            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), fileData.bytes, fileData.length)
            }

            val ext = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
            onFileSelected(fileName, bytes, mimeType)
        } finally {
            if (canAccess) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

private var activePickerDelegate: DocumentPickerDelegate? = null

@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit {
    return remember(onFileSelected) {
        {
            val delegate = DocumentPickerDelegate(onFileSelected)
            activePickerDelegate = delegate
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeItem, UTTypeContent, UTTypeData),
                asCopy = true
            )
            picker.delegate = delegate
            picker.allowsMultipleSelection = false

            val scenes = UIApplication.sharedApplication.connectedScenes
            var rootVc: UIViewController? = null
            for (scene in scenes) {
                val windowScene = scene as? UIWindowScene ?: continue
                val win = windowScene.windows.mapNotNull { it as? UIWindow }.firstOrNull { it.isKeyWindow() }
                    ?: windowScene.windows.firstOrNull() as? UIWindow
                if (win?.rootViewController != null) {
                    rootVc = win.rootViewController
                    break
                }
            }
            if (rootVc == null) {
                rootVc = (UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow)?.rootViewController
            }

            var presenter = rootVc
            while (presenter?.presentedViewController != null) {
                presenter = presenter.presentedViewController
            }
            presenter?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
