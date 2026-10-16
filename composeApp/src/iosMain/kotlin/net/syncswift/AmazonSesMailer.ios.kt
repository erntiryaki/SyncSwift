package net.syncswift

actual object AmazonSesMailer {
    actual suspend fun sendOtpEmail(email: String, code: String): Boolean {
        return true
    }
}
