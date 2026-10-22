package net.syncswift.crypto

expect object PlatformCrypto {
    fun generateSigningKeyPair(): SigningKeyPair
    fun generateAgreementKeyPair(): AgreementKeyPair
    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
    fun diffieHellman(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
}
