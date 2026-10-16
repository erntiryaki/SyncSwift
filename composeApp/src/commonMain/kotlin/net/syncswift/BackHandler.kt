package net.syncswift

import androidx.compose.runtime.Composable

@Composable
expect fun DoubleBackToExitHandler(exitMessage: String)

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
