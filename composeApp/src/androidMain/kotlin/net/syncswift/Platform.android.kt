package net.syncswift

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

actual fun currentPlatform(): PlatformFlavor = PlatformFlavor.Android
actual fun currentTimeMs(): Long = System.currentTimeMillis()
actual fun getSystemLanguageCode(): String = java.util.Locale.getDefault().language ?: "en"
actual fun getSystemCountryCode(): String {
    return try {
        java.util.Locale.getDefault().country?.uppercase() ?: ""
    } catch (_: Throwable) {
        ""
    }
}
actual fun showNativeToast(message: String) {
    ToastNotifier.show(message)
}

@androidx.compose.runtime.Composable
actual fun UpdateSystemBars(isDark: Boolean, statusBarColor: androidx.compose.ui.graphics.Color?) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val colorArgb = statusBarColor?.let {
            val a = (it.alpha * 255.0f + 0.5f).toInt()
            val r = (it.red * 255.0f + 0.5f).toInt()
            val g = (it.green * 255.0f + 0.5f).toInt()
            val b = (it.blue * 255.0f + 0.5f).toInt()
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        androidx.compose.runtime.DisposableEffect(isDark, colorArgb) {
            // Check if we are inside a Dialog and configure its window edge-to-edge
            var parent = view.parent
            while (parent != null) {
                if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                    val dialogWindow = parent.window
                    dialogWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                    dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                    dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                    dialogWindow.setLayout(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT
                    )
                    val dialogInsets = androidx.core.view.WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                    dialogInsets.isAppearanceLightStatusBars = !isDark
                    dialogInsets.isAppearanceLightNavigationBars = !isDark
                    break
                }
                parent = parent.parent
            }

            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is android.app.Activity) break
                ctx = ctx.baseContext
            }
            val activity = (ctx as? android.app.Activity) ?: SessionManager.getCurrentActivity()
            val window = activity?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
                if (colorArgb != null) {
                    window.statusBarColor = colorArgb
                }
            }
            onDispose { }
        }
    }
}

@androidx.compose.runtime.Composable
actual fun SheetSystemBars(active: Boolean, isDark: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return

    val darkBackdropColor = android.graphics.Color.parseColor("#161F30")

    val configureDialogWindow: () -> Unit = {
        var parent = view.parent
        while (parent != null) {
            if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                val dialogWindow = parent.window
                dialogWindow.setWindowAnimations(0)
                dialogWindow.attributes = dialogWindow.attributes.apply {
                    windowAnimations = 0
                }
                dialogWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                dialogWindow.statusBarColor = darkBackdropColor
                dialogWindow.navigationBarColor = darkBackdropColor
                dialogWindow.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                dialogWindow.setGravity(android.view.Gravity.BOTTOM)
                val dialogInsets = androidx.core.view.WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                dialogInsets.isAppearanceLightStatusBars = false
                dialogInsets.isAppearanceLightNavigationBars = false
                break
            }
            parent = parent.parent
        }
    }

    if (active) {
        androidx.compose.runtime.SideEffect {
            configureDialogWindow()
        }
    }

    androidx.compose.runtime.DisposableEffect(active, isDark, view) {
        if (active) {
            configureDialogWindow()
            view.post { configureDialogWindow() }

            val activity = SessionManager.getCurrentActivity()
            val actWindow = activity?.window
            if (actWindow != null) {
                actWindow.statusBarColor = darkBackdropColor
                val actInsets = androidx.core.view.WindowCompat.getInsetsController(actWindow, actWindow.decorView)
                actInsets.isAppearanceLightStatusBars = false
                actInsets.isAppearanceLightNavigationBars = false
            }
        }

        onDispose {
            val activity = SessionManager.getCurrentActivity()
            val actWindow = activity?.window
            if (actWindow != null) {
                actWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                val actInsets = androidx.core.view.WindowCompat.getInsetsController(actWindow, actWindow.decorView)
                actInsets.isAppearanceLightStatusBars = !isDark
                actInsets.isAppearanceLightNavigationBars = !isDark
            }
        }
    }
}

actual fun setSystemBarsDirect(isDark: Boolean, statusBarColor: androidx.compose.ui.graphics.Color?) {
    val activity = SessionManager.getCurrentActivity() ?: return
    val window = activity.window ?: return
    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = !isDark
    insetsController.isAppearanceLightNavigationBars = !isDark
    if (statusBarColor != null) {
        val a = (statusBarColor.alpha * 255.0f + 0.5f).toInt()
        val r = (statusBarColor.red * 255.0f + 0.5f).toInt()
        val g = (statusBarColor.green * 255.0f + 0.5f).toInt()
        val b = (statusBarColor.blue * 255.0f + 0.5f).toInt()
        window.statusBarColor = (a shl 24) or (r shl 16) or (g shl 8) or b
    } else {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
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

        // 1. DownloadManager (Standard Android Downloads viewer)
        try {
            val dmIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(dmIntent)
            return
        } catch (_: Exception) {}

        // 2. DocumentsUI / System Files at Downloads/SyncSwift
        try {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FSyncSwift")
            val docIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(docIntent)
            return
        } catch (_: Exception) {}

        // 3. Samsung "Dosyalarım" (My Files) Native Intent
        try {
            val samsungIntent = Intent("com.sec.android.app.myfiles.VIEW_DIRECTORY").apply {
                data = Uri.parse("file://${targetDir.absolutePath}")
                putExtra("folder_path", targetDir.absolutePath)
                putExtra("START_PATH", targetDir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(samsungIntent)
            return
        } catch (_: Exception) {}

        try {
            val samsungLaunch = ctx.packageManager.getLaunchIntentForPackage("com.sec.android.app.myfiles")?.apply {
                putExtra("folder_path", targetDir.absolutePath)
                putExtra("START_PATH", targetDir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (samsungLaunch != null) {
                ctx.startActivity(samsungLaunch)
                return
            }
        } catch (_: Exception) {}

        // 4. Xiaomi File Explorer
        try {
            val miIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.mi.android.globalFileexplorer")
                putExtra("explorer_path", targetDir.absolutePath)
                setDataAndType(Uri.fromFile(targetDir), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(miIntent)
            return
        } catch (_: Exception) {}

        // 5. Google Files (Files by Google)
        try {
            val gFiles = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (gFiles != null) {
                ctx.startActivity(gFiles)
                return
            }
        } catch (_: Exception) {}

        // 6. Generic File Provider / Folder
        try {
            val dirUri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", targetDir)
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dirUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(genericIntent)
            return
        } catch (_: Exception) {}

        // 7. If folder cannot be opened directly, open the file itself
        if (!filePathOrUri.isNullOrBlank()) {
            openFile(filePathOrUri, fileName, "*/*")
            return
        }

        showNativeToast("Downloads/SyncSwift")
    } catch (e: Exception) {
        Log.e("PlatformAndroid", "Failed to open file location: ${e.message}", e)
        if (!filePathOrUri.isNullOrBlank()) {
            openFile(filePathOrUri, fileName, "*/*")
        } else {
            showNativeToast("Downloads/SyncSwift")
        }
    }
}

actual fun formatEpochToDate(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

actual fun getCurrentPaywallSlotKey(): String {
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val slot = hour / 3
    return "$year-$month-$day-$slot"
}
