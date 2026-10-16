package net.syncswift.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberQrScanner(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    return {
        onError("Camera scanner is not available on this platform.")
    }
}

@Composable
actual fun rememberQrImagePicker(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    return {
        onError("Image scanner is not available on this platform.")
    }
}

actual object QrScannerHelper {
    actual fun isAvailable(): Boolean = false
}
