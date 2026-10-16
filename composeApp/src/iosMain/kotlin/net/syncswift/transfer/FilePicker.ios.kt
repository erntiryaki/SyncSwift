package net.syncswift.transfer

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit {
    return {}
}
