package net.syncswift.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
actual fun rememberQrScanner(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        if (result != null && result.contents != null) {
            onSuccess(result.contents)
        } else if (result != null && result.contents == null) {
            onError("Cancelled")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("")
                setBeepEnabled(false)
                setOrientationLocked(true)
                setCaptureActivity(CustomQrScannerActivity::class.java)
            }
            scanLauncher.launch(options)
        } else {
            onError("Camera permission denied")
        }
    }

    return {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("")
                setBeepEnabled(false)
                setOrientationLocked(true)
                setCaptureActivity(CustomQrScannerActivity::class.java)
            }
            scanLauncher.launch(options)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
actual fun rememberQrImagePicker(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val width = bitmap.width
                        val height = bitmap.height
                        val pixels = IntArray(width * height)
                        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                        val source = RGBLuminanceSource(width, height, pixels)
                        try {
                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                            val qrResult = MultiFormatReader().decode(binaryBitmap)
                            onSuccess(qrResult.text)
                            return@rememberLauncherForActivityResult
                        } catch (e: Exception) {
                            try {
                                val altBinaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                                val qrResult = MultiFormatReader().decode(altBinaryBitmap)
                                onSuccess(qrResult.text)
                                return@rememberLauncherForActivityResult
                            } catch (e2: Exception) {
                                onError("NoQrFound")
                                return@rememberLauncherForActivityResult
                            }
                        }
                    }
                }
                onError("NoQrFound")
            } catch (e: Exception) {
                onError("NoQrFound")
            }
        }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        pickerLauncher.launch("image/*")
    }

    return {
        val allGranted = permissionsToRequest.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            pickerLauncher.launch("image/*")
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}

actual object QrScannerHelper {
    actual fun isAvailable(): Boolean = true
}
