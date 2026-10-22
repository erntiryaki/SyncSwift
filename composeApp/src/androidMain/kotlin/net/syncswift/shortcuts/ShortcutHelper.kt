package net.syncswift.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import net.syncswift.MainActivity
import net.syncswift.currentAppCopy

object ShortcutHelper {
    const val ACTION_SEND_FILE = "net.syncswift.ACTION_SHORTCUT_SEND_FILE"
    const val ACTION_SEND_CLIPBOARD = "net.syncswift.ACTION_SHORTCUT_SEND_CLIPBOARD"

    fun updateShortcuts(context: Context, langCode: String? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            val copy = if (!langCode.isNullOrBlank()) {
                net.syncswift.copyForLanguageCode(langCode)
            } else {
                currentAppCopy()
            }

            val fileIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_SEND_FILE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val clipIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_SEND_CLIPBOARD
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val sendFileShortcut = ShortcutInfo.Builder(context, "shortcut_send_file")
                .setShortLabel(copy.shortcutSendFileShort)
                .setLongLabel(copy.shortcutSendFileLong)
                .setIcon(Icon.createWithResource(context, net.syncswift.R.drawable.ic_shortcut_send_file))
                .setIntent(fileIntent)
                .setRank(1)
                .build()

            val sendClipShortcut = ShortcutInfo.Builder(context, "shortcut_send_clipboard")
                .setShortLabel(copy.shortcutSendClipboardShort)
                .setLongLabel(copy.shortcutSendClipboardLong)
                .setIcon(Icon.createWithResource(context, net.syncswift.R.drawable.ic_shortcut_send_clipboard))
                .setIntent(clipIntent)
                .setRank(2)
                .build()

            val shortcutList = listOf(sendFileShortcut, sendClipShortcut)
            shortcutManager.dynamicShortcuts = shortcutList
            shortcutManager.updateShortcuts(shortcutList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
