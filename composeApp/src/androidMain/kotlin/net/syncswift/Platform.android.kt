package net.syncswift

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Android
actual fun currentTimeMs(): Long = System.currentTimeMillis()
actual fun getSystemLanguageCode(): String = java.util.Locale.getDefault().language ?: "en"
actual fun showNativeToast(message: String) {
    SessionManager.getAppContext()?.let { ctx ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@androidx.compose.runtime.Composable
actual fun UpdateSystemBars(isDark: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }
}

actual fun openFile(filePathOrUri: String?, fileName: String, mimeType: String) {
    val ctx = SessionManager.getAppContext() ?: return
    try {
        var uri: Uri? = null
        if (!filePathOrUri.isNullOrBlank()) {
            if (filePathOrUri.startsWith("content://")) {
                uri = Uri.parse(filePathOrUri)
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) {
                    uri = try {
                        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }
                }
            }
        }

        // If URI is still null, look in Downloads/SyncSwift
        if (uri == null) {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SyncSwift")
            val candidateFile = File(publicDir, fileName)
            if (candidateFile.exists()) {
                uri = try {
                    androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", candidateFile)
                } catch (e: Exception) {
                    Uri.fromFile(candidateFile)
                }
            } else {
                val appDir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SyncSwift")
                val appFile = File(appDir, fileName)
                if (appFile.exists()) {
                    uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", appFile)
                }
            }
        }

        if (uri != null) {
            val resolvedMime = if (mimeType.isNotBlank() && mimeType != "*/*") {
                mimeType
            } else {
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileName)
                ) ?: "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } else {
            showNativeToast("File: $fileName")
        }
    } catch (e: Exception) {
        Log.e("PlatformAndroid", "Failed to open file: ${e.message}", e)
        showNativeToast("Unable to open file: ${e.message}")
    }
}

actual fun showFileLocation(filePathOrUri: String?, fileName: String) {
    val ctx = SessionManager.getAppContext() ?: return
    try {
        val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SyncSwift")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val dirUri = try {
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", targetDir)
        } catch (e: Exception) {
            Uri.fromFile(targetDir)
        }

        val folderIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(dirUri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (folderIntent.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(folderIntent)
            return
        }

        // Fallback: Open system Downloads
        val downloadsIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (downloadsIntent.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(downloadsIntent)
        } else {
            showNativeToast("Downloads/SyncSwift")
        }
    } catch (e: Exception) {
        Log.e("PlatformAndroid", "Failed to open file location: ${e.message}", e)
        try {
            val downloadsIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(downloadsIntent)
        } catch (_: Exception) {
            showNativeToast("Downloads/SyncSwift")
        }
    }
}
