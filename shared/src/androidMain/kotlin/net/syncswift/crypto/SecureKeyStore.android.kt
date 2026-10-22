package net.syncswift.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual object SecureKeyStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                "syncswift_secure_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard private preferences if hardware keystore error occurs
            prefs = context.getSharedPreferences("syncswift_keys_fallback", Context.MODE_PRIVATE)
        }
    }

    actual fun getString(key: String): String? {
        return prefs?.getString(key, null)
    }

    actual fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    actual fun getBytes(key: String): ByteArray? {
        val b64 = getString(key) ?: return null
        return Base64Util.decode(b64)
    }

    actual fun putBytes(key: String, value: ByteArray) {
        putString(key, Base64Util.encode(value))
    }

    actual fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    actual fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
