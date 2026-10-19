package net.syncswift.ui.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.AppLanguage

object TermsAgreements {

    fun getMembershipTerms(language: AppLanguage): List<Pair<String, String>> {
        return if (language == AppLanguage.Turkish) {
            listOf(
                "1. Hizmet Kapsamı" to "SyncSwift, kullanıcıların farklı işletim sistemlerine sahip cihazları (Android, iOS, macOS, Windows, Linux) arasında güvenli, hızlı ve doğrudan dosya transferi, ortak pano paylaşımı ve bildirim senkronizasyonu sağlayan bir ekosistemdir.",
                "2. Hesap ve Güvenlik" to "Kullanıcılar hesaplarının güvenliğini sağlamakla yükümlüdür. E-posta adresi veya kimlik sağlayıcı (Google, Apple) ile oluşturulan hesaplar kişiye özeldir. Üçüncü şahıslarla paylaşılamaz.",
                "3. Kabul Edilebilir Kullanım" to "Hizmet yalnızca yasal amaçlarla kullanılabilir. Başkalarının fikri mülkiyet haklarını, gizliliğini ihlal eden veya kötü amaçlı yazılım içeren içeriklerin aktarımı kesinlikle yasaktır.",
                "4. Fikri Mülkiyet" to "SyncSwift yazılımı, logoları, tasarımları ve markası telif hakları ile korunmaktadır. Önceden yazılı izin alınmaksızın kopyalanamaz veya dağıtılamaz.",
                "5. Değişiklikler ve Fesih" to "SyncSwift, hizmet koşullarını önceden bildirimde bulunarak güncelleme ve kurallara uymayan hesapları askıya alma veya sonlandırma hakkını saklı tutar."
            )
        } else {
            listOf(
                "1. Scope of Service" to "SyncSwift is a seamless cross-device synchronization ecosystem enabling direct, secure file transfers, real-time shared clipboard, and notification delivery across Android, iOS, macOS, Windows, and Linux devices.",
                "2. Account & Security" to "You are responsible for maintaining the confidentiality of your account credentials. Accounts created via email or social identity providers (Google, Apple) are personal and non-transferable.",
                "3. Acceptable Use" to "SyncSwift must only be used for lawful purposes. You agree not to transmit illegal content, malware, or materials infringing on third-party intellectual property.",
                "4. Intellectual Property" to "SyncSwift software, trademarks, graphics, and codebase are protected under international copyright and intellectual property laws.",
                "5. Modifications & Termination" to "We reserve the right to modify these terms with reasonable notice and terminate accounts violating these standards."
            )
        }
    }

    fun getUsageTerms(language: AppLanguage): List<Pair<String, String>> {
        return if (language == AppLanguage.Turkish) {
            listOf(
                "1. Veri Gizliliği ve Toplama" to "SyncSwift, kullanıcı gizliliğini birinci öncelik olarak kabul eder. Cihaz eşleşmesi ve oturum açma amacıyla yalnızca e-posta ve cihaz adı gibi asgari düzeyde bilgi işlenir.",
                "2. Uçtan Uca Şifreleme" to "Cihazlar arasında aktarılan dosyalar, kopyalanan pano metinleri ve medya içerikleri yerel ağ veya P2P bağlantılar üzerinde uçtan uca şifrelenir. SyncSwift sunucuları bu içerikleri göremez veya saklamaz.",
                "3. Yerel Ağ ve Bluetooth İzinleri" to "Uygulama, çevrenizdeki kendi cihazlarınızı keşfetmek ve bağlanmak için yerel ağ (Wi-Fi/mDNS) ve Bluetooth izinlerini kullanır. Konum verisi toplanmaz veya üçüncü taraflarla paylaşılmaz.",
                "4. Kullanıcı Verilerinin Silinmesi" to "Kullanıcılar diledikleri zaman hesaplarını ve ilişkili tüm cihaz kayıtlarını 'Hesabımı Sil' seçeneği ile kalıcı olarak silebilirler."
            )
        } else {
            listOf(
                "1. Data Privacy & Minimization" to "SyncSwift strictly prioritizes user privacy. We only process minimal data required for authentication and device pairing (such as email and device hostname).",
                "2. End-to-End Encryption" to "All transferred files, live clipboard items, and media payloads are transmitted with end-to-end encryption across local networks or direct P2P connections. SyncSwift servers cannot read or store your content.",
                "3. Network & Bluetooth Permissions" to "Local network (Wi-Fi/mDNS) and Bluetooth permissions are utilized exclusively for nearby peer discovery and transfer handshakes. No location tracking is performed.",
                "4. Account & Data Deletion" to "Users may permanently delete their accounts and all associated device records at any time via Account settings."
            )
        }
    }
}

@Composable
fun TermsSheetContent(
    sections: List<Pair<String, String>>,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        sections.forEach { (heading, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = heading,
                    color = AuthColors.textPrimary(isDark),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = body,
                    color = AuthColors.textSecondary(isDark),
                    fontSize = 13.sp,
                    lineHeight = 18.5.sp
                )
            }
        }
    }
}
