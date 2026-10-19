package net.syncswift.ui.auth.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import net.syncswift.ui.components.SyncSwiftBottomSheet
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun AuthBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String = "",
    titleIcon: (@Composable () -> Unit)? = null,
    isDark: Boolean = LocalSyncSwiftColors.current.isDark,
    content: @Composable ColumnScope.() -> Unit
) {
    SyncSwiftBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        titleIcon = titleIcon,
        isDark = isDark,
        content = content
    )
}
