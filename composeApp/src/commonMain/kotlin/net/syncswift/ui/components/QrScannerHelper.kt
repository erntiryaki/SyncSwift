package net.syncswift.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberQrScanner(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit

@Composable
expect fun rememberQrImagePicker(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit

expect object QrScannerHelper {
    fun isAvailable(): Boolean
}
