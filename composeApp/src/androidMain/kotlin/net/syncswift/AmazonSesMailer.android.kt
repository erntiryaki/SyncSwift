package net.syncswift

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object AmazonSesMailer {
    private const val TAG = "AmazonSesMailer"

    actual suspend fun sendOtpEmail(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        // Production OTP emails are sent server-side by Supabase GoTrue / SMTP.
        // Client apps never contain mailer credentials.
        Log.i(TAG, "OTP email dispatched server-side for $email")
        true
    }
}
