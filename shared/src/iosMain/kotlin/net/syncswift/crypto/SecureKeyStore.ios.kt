package net.syncswift.crypto

import platform.Foundation.NSUserDefaults

actual object SecureKeyStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? {
        return defaults.stringForKey("sec_$key")
    }

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, "sec_$key")
    }

    actual fun getBytes(key: String): ByteArray? {
        val b64 = getString(key) ?: return null
        return Base64Util.decode(b64)
    }

    actual fun putBytes(key: String, value: ByteArray) {
        putString(key, Base64Util.encode(value))
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey("sec_$key")
    }

    actual fun clear() {
        // Will clear keys prefixed with sec_
    }
}
