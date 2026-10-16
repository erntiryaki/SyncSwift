package net.syncswift

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual object BrevoMailer {
    actual suspend fun sendOtpEmail(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        if (SyncSwiftConfig.BREVO_API_KEY.isBlank()) {
            Log.w("BrevoMailer", "Brevo API Key blank. Simulated send to $email: $code")
            return@withContext true
        }

        try {
            val senderEmail = SyncSwiftConfig.BREVO_SENDER_EMAIL.ifBlank { "noreply@aqsesoft.com" }
            Log.d("BrevoMailer", "Sending Brevo OTP Email from $senderEmail to $email with code: $code")
            val url = URL("https://api.brevo.com/v3/smtp/email")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("api-key", SyncSwiftConfig.BREVO_API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            val payload = """
                {
                    "sender": { "name": "SyncSwift Security", "email": "$senderEmail" },
                    "to": [{ "email": "$email" }],
                    "subject": "SyncSwift - Doğrulama Kodunuz: $code",
                    "htmlContent": "<!DOCTYPE html><html><body style='font-family:sans-serif;background-color:#0F172A;color:#FFFFFF;padding:32px;'><div style='max-width:480px;margin:0 auto;background:#1E293B;padding:32px;border-radius:16px;border:1px solid #334155;'><h2 style='color:#38BDF8;margin-top:0;'>SyncSwift Doğrulama Kodunuz</h2><p style='color:#E2E8F0;font-size:15px;line-height:22px;'>Cihaz senkronizasyonunu tamamlamak için aşağıdaki 6 haneli kodu girin:</p><div style='text-align:center;padding:20px;background:#0F172A;border-radius:12px;margin:24px 0;'><span style='letter-spacing:8px;color:#38BDF8;font-size:36px;font-weight:bold;font-family:monospace;'>$code</span></div><p style='color:#94A3B8;font-size:13px;margin-bottom:0;'>Bu kod 10 dakika boyunca geçerlidir. Eğer bu talebi siz yapmadıysanız bu e-postayı güvenle yok sayabilirsiniz.</p></div></body></html>"
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
