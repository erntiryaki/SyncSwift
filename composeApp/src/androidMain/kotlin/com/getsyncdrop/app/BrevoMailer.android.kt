package com.getsyncdrop.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual object BrevoMailer {
    actual suspend fun sendOtpEmail(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncDropConfig.BREVO_API_KEY.isBlank()) {
            Log.w("BrevoMailer", "Brevo API Key blank. Simulated send to $email: $code")
            return@withContext true
        }

        try {
            Log.d("BrevoMailer", "Sending Brevo OTP Email to $email with code: $code")
            val url = URL("https://api.brevo.com/v3/smtp/email")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("api-key", SyncDropConfig.BREVO_API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            val payload = """
                {
                    "sender": { "name": "SyncDrop Security", "email": "erntiryaki@gmail.com" },
                    "to": [{ "email": "$email" }],
                    "subject": "SyncDrop - Doğrulama Kodunuz: $code",
                    "htmlContent": "<!DOCTYPE html><html><body style='font-family:sans-serif;background-color:#0F172A;color:#FFFFFF;padding:30px;'><h2 style='color:#38BDF8;'>SyncDrop Doğrulama Kodunuz</h2><p>Cihaz senkronizasyonunu tamamlamak için aşağıdaki 6 haneli kodu girin:</p><h1 style='letter-spacing:6px;color:#38BDF8;font-size:36px;'>$code</h1><p style='color:#94A3B8;'>Bu kod 10 dakika boyunca geçerlidir.</p></body></html>"
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.d("BrevoMailer", "Brevo API Response Code: $responseCode, Body: $responseText")
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e("BrevoMailer", "Failed to send Brevo email: ${e.message}", e)
            return@withContext false
        }
    }
}
