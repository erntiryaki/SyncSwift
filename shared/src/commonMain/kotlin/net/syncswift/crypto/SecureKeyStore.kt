package net.syncswift.crypto

expect object SecureKeyStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
    fun remove(key: String)
    fun clear()
}
