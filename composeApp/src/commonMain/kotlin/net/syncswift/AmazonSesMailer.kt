package net.syncswift

expect object AmazonSesMailer {
    suspend fun sendOtpEmail(email: String, code: String): Boolean
}
