package net.syncswift

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object AmazonSesMailer {
    private const val TAG = "AmazonSesMailer"

    actual suspend fun sendOtpEmail(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val accessKey = PlatformConfig.awsSesAccessKeyId
        val secretKey = PlatformConfig.awsSesSecretAccessKey
        val region = PlatformConfig.awsSesRegion.ifBlank { "eu-central-1" }
        val senderEmail = PlatformConfig.awsSesSenderEmail.ifBlank { "noreply@syncswift.net" }

        if (accessKey.isBlank() || secretKey.isBlank()) {
            Log.w(TAG, "AWS SES credentials blank. Falling back to Brevo...")
            return@withContext BrevoMailer.sendOtpEmail(email, code)
        }

        try {
            val service = "ses"
            val host = "email.$region.amazonaws.com"
            val endpoint = "https://$host/v2/email/outbound-emails"

            val subject = "SyncSwift - Doğrulama Kodunuz: $code"
            val htmlBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background-color:#0F172A;color:#FFFFFF;padding:32px;'><div style='max-width:480px;margin:0 auto;background:#1E293B;padding:32px;border-radius:16px;border:1px solid #334155;'><h2 style='color:#38D5E2;margin-top:0;'>SyncSwift Doğrulama Kodunuz</h2><p style='color:#E2E8F0;font-size:15px;line-height:22px;'>Cihaz senkronizasyonunu tamamlamak için aşağıdaki 6 haneli kodu girin:</p><div style='text-align:center;padding:20px;background:#0F172A;border-radius:12px;margin:24px 0;'><span style='letter-spacing:8px;color:#38D5E2;font-size:36px;font-weight:bold;font-family:monospace;'>$code</span></div><p style='color:#94A3B8;font-size:13px;margin-bottom:0;'>Bu kod 10 dakika boyunca geçerlidir. Eğer bu talebi siz yapmadıysanız bu e-postayı güvenle yok sayabilirsiniz.</p></div></body></html>"

            val escapedHtml = htmlBody.replace("\"", "\\\"")
            val payload = """{"FromEmailAddress":"$senderEmail","Destination":{"ToAddresses":["$email"]},"Content":{"Simple":{"Subject":{"Data":"$subject","Charset":"UTF-8"},"Body":{"Html":{"Data":"$escapedHtml","Charset":"UTF-8"}}}}}"""

            val now = Date()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val timeFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

            val dateStamp = dateFormat.format(now)
            val amzDate = timeFormat.format(now)

            val canonicalUri = "/v2/email/outbound-emails"
            val canonicalQuery = ""
            val payloadHash = sha256Hex(payload.toByteArray(StandardCharsets.UTF_8))

            val canonicalHeaders = "content-type:application/json\nhost:$host\nx-amz-date:$amzDate\n"
            val signedHeaders = "content-type;host;x-amz-date"
            val canonicalRequest = "POST\n$canonicalUri\n$canonicalQuery\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

            val algorithm = "AWS4-HMAC-SHA256"
            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))}"

            val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
            val signature = hmacHex(signingKey, stringToSign)

            val authHeader = "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("host", host)
            conn.setRequestProperty("x-amz-date", amzDate)
            conn.setRequestProperty("Authorization", authHeader)
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            Log.d(TAG, "Amazon SES response: $responseCode - $responseBody")
            val success = responseCode in 200..299
            if (!success) {
                Log.w(TAG, "Amazon SES send failed with $responseCode, trying Brevo fallback...")
                return@withContext BrevoMailer.sendOtpEmail(email, code)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Amazon SES error: ${e.message}", e)
            return@withContext BrevoMailer.sendOtpEmail(email, code)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }
}
