package net.syncswift.transfer

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(
    onFileSelected: (name: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit
