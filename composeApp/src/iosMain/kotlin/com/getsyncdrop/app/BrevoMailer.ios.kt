package com.getsyncdrop.app

actual object BrevoMailer {
    actual suspend fun sendOtpEmail(email: String, code: String): Boolean {
        return true
    }
}
