# SyncDrop — Ürün, Mimari ve Yol Haritası Spesifikasyon Dokümanı

## 1. Ürün Vizyonu ve Özeti

**SyncDrop**, Apple ekosistemindeki "AirDrop" ve "Universal Clipboard" sadeliğini ve hızını tüm platformlara (iOS, Android, Windows, macOS) taşıyan modern, gizlilik odaklı, yerel ağda doğrudan (P2P) ve uzaktan tünelleme ile çalışan bir dosya ve pano senkronizasyon ekosistemidir.

* **Ana Odak:** Yüksek aktarım hızı, sıfır karmaşa, yerel ağda doğrudan cihazdan cihaza (P2P) aktarım ve uzaktan senkronizasyon için güvenli bulut röleleri.
* **Platform Kapsamı:** iOS, Android, macOS ve Windows (Web istemcisi yer almaz; ekosistem tamamen native ve masaüstü istemcilerine odaklıdır).
* **Güvenlik Yaklaşımı:** Sıfır-Bilgi (Zero-Knowledge Architecture) ve Uçtan Uca Şifreleme (E2EE - Curve25519 / AES-256-GCM / TLS 1.3). Aktarılan dosyalar ve pano içerikleri geliştirici veya sunucu sağlayıcıları dahil hiç kimse tarafından okunamaz.

---

## 2. Marka Kimliği & Görsel Standartlar

### 2.1 Logo & Renk Hiyerarşisi

* **Logo Varlığı:** `logo.png` (Senkronizasyon simgesi "S" harfi, Wi-Fi sinyalli "Drop" tipografisi, sloganlı ve app icon formatı).
* **Ana Renk (Primary):** Soft koyu lacivert / slate navy (`#1E293B` / `#172554`)
* **İkincil Renk (Secondary):** Soft siyah / kömür (`#27272A` / `#18181B`)
* **Aksan Renk (Accent):** Modern koyu mavi tonları
* **Zemin (Background):** Saf beyaz (`#FFFFFF`) ve açık temada mat açık gri zemin kartları (`#F8FAFC`).

### 2.2 Tasarım Dili (UI/UX Felsefesi)

* **Flat & Minimalist:** Kesinlikle parıltılı, 3D efektli, ışıklı, neon kenarlıklı veya abartılı gölgeli bileşenler kullanılmayacaktır. Tamamen düz, sade, endüstri standardı modern kurumsal arayüz prensibi uygulanacaktır.
* **Tipografi:** Projedeki mobil uygulamada olduğu gibi **Manrope** modern yazı tipi ailesi (Regular, Medium, SemiBold, Bold) kullanılacaktır.
* **Masaüstü Entegrasyonu:** Windows ve macOS için Tauri v2 tabanlı masaüstü istemcisi. Girişte sağ tarafta dinamik QR kod ve 6 haneli eşleşme kodu yer alır; sistem tepsisinde (Windows'ta sağ altta, macOS'ta sağ üstte) minimal açılır panel bulunur.
* **Bildirimler ve Uyarılar:** Özel gösterişli alert/modal popup'lar yerine işletim sisteminin kendi native dialog/uyarı pencereleri (iOS UIAlertController, Android Material AlertDialog, macOS/Windows yerel dialog API'leri) tercih edilecektir.

### 2.3 Sosyal Medya & Alan Adı

* **Resmi Kullanıcı Adı:** `@getsyncdrop` (Instagram, X, GitHub)
* **Web Sitesi:** `getsyncdrop.com` (Alternatif: `syncdrop.app`)
* **Resmi Slogan:** `SEAMLESS CROSS-DEVICE SYNC`

---

## 3. Fiyatlandırma ve Gelir Modeli

*(Ad-Supported Freemium)*

Abonelik altyapısı, mağaza gelirleri ve çapraz platform hak doğrulaması RevenueCat üzerinden yönetilir.

* **Global:**
* Ücretsiz / Reklam Destekli
* Pro Aylık Plan: **$2.99 / ay**
* Pro Yıllık Plan: **$19.99 / yıl**


* **Türkiye:**
* Ücretsiz / Reklam Destekli
* Pro Aylık Plan: **99.99 TL / ay**
* Pro Yıllık Plan: **649.99 TL / yıl**



---

## 4. Paketler ve Dosya Paylaşım Sınırları

### 4.1 Free Katmanı (Reklam Destekli & Temel Seviye)

* **Reklam Yerleşimi:**
* **Mobil (iOS & Android):** Aktarım öncesi/sonrasında tetiklenen geçiş (interstitial) reklamlar ve ana liste ekranı altında temiz, gölgesiz banner reklamlar (Google AdMob).
* **Masaüstü (macOS & Windows):** Transfer tamamlandığında arayüzün alt köşesinde beliren düz, sade sponsorlu mini bilgilendirme alanı.


* **Maksimum Tekil Dosya Boyutu:** 250 MB.
* **Günlük Toplam Transfer Kotası:** 2 GB / gün.
* **Ağ Kapsamı:** Yalnızca Yerel Ağ (Local P2P - tüm cihazların aynı Wi-Fi/LAN ağında bulunması zorunludur).
* **Eşzamanlı Dosya Gönderimi:** Tek seferde en fazla 5 dosya (Klasör gönderimi kapalıdır).
* **Pano Senkronizasyonu:** Yalnızca tekil, anlık son kopyalanan 1 öğe (Geçmiş tutulmaz).
* **Transfer Tipi:** Canlı/eşzamanlı aktarım (İki cihazın da o sırada açık ve onay vermiş olması gerekir).

### 4.2 Pro Katmanı (Reklamsız & Tam Kapasite)

* **Reklam Durumu:** Tüm platformlarda %100 temiz ve reklamsız arayüz.
* **Maksimum Tekil Dosya Boyutu:** Bulut röle üzerinden 50 GB, yerel ağda **Sınırsız**.
* **Günlük Toplam Transfer Kotası:** Sınırsız.
* **Ağ Kapsamı:** Hem Yerel Ağ (Local P2P) Hem Uzaktan Aktarım (Remote WebRTC/TURN - mobil veri ve farklı ağlar arası aktarım).
* **Eşzamanlı Dosya & Klasör:** Sınırsız sayıda dosya ve hiyerarşik klasör yapısı korunarak transfer.
* **Pano Geçmişi:** Son 30 günlük aranabilir bulut pano geçmişi (Cloud Clipboard History).
* **Asenkron Saklama (Drop & Forget):** Hedef cihaz kapalıyken Cloudflare R2'ye uçtan uca şifrelenmiş yükleme ve cihaz açıldığında otomatik indirme (24 saatlik TTL ömrü).
* **Otomatik Eşleşme (Auto-Receive):** Kullanıcının kendi doğrulanmış cihazları arasında onay penceresi beklemeden arka planda sessiz indirme.
* **Masaüstü Drop-Zone:** macOS Menü Çubuğu ve Windows Görev Çubuğunda her an hazır, sürükle-bırak hedefli minimalist mini panel.
* **Öncelikli TURN Bant Genişliği:** Kısıtlayıcı güvenlik duvarları ve simetrik NAT arkasında kesintisiz yüksek bant genişliği.

---

## 5. Uçtan Uca Teknik Mimari (Tech Stack)

### 5.1 Mimari Şema

```text
         ┌────────────────────────────────────────────────────────┐
         │              SYNCDROP YAZILIM EKOSİSTEMİ               │
         └───────────────────────────┬────────────────────────────┘
                                     │
             ┌───────────────────────┼────────────────────────────┐
             │                       │                            │
             ▼                       ▼                            ▼
┌─────────────────────────┐ ┌─────────────────────────┐ ┌─────────────────────────┐
│   MOBİL (iOS & Android) │ │ MASAÜSTÜ (macOS & Win)  │ │ WEB SİTESİ (Tanıtım)    │
│ - Kotlin Multiplatform  │ │ - Tauri v2 (Rust Core)  │ │ - Next.js (TypeScript)  │
│ - Compose Multiplatform │ │ - React / TS (Düz UI)   │ │ - Tailwind CSS          │
│ - Nunito Font Ailesi    │ │ - Nunito Font Ailesi    │ │ - Vercel / Cloudflare   │
│ - Google AdMob (Free)   │ │ - Native Tray & Hotkeys │ │ - İndirme & Tanıtım     │
└────────────┬────────────┘ └────────────┬────────────┘ └─────────────────────────┘
             │                           │
             └─────────────┬─────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            BACKEND & ALTYAPI KATMANI                            │
├───────────────────────────────────┬─────────────────────────────────────────────┤
│ Veritabanı & Auth: Supabase (PG)  │ E-Posta Altyapısı: Amazon SES (OTP & İşlem) │
│ Sinyalleşme: Supabase Realtime    │ Bildirim: APNs (iOS) & FCM (Android)        │
│ Abonelik: RevenueCat (In-App)     │ Yerel Keşif: mDNS / Bonjour (ZeroConf)      │
│ P2P & Röle: WebRTC + Coturn (VPS) │ Geçici Depolama: Cloudflare R2 (24s TTL)    │
└───────────────────────────────────┴─────────────────────────────────────────────┘

```

#### A. Mobil İstemci: Kotlin Multiplatform (KMP) + Compose Multiplatform

* **Mimari:** İş mantığı, P2P soket yöneticisi, WebRTC tüneli, şifreleme motoru ve pano dinleme katmanları ortak Kotlin modülünde (`shared`) tutulur.
* **UI Motoru:** Compose Multiplatform ile tamamen flat, minimalist ve `/fonts` altındaki Nunito fontlarıyla derlenen modern arayüz.
* **Platform Özel Entegrasyonları (`expect`/`actual`):**
* **iOS:** APNs Silent Push yakalama, Share Extension (Fotoğraflar ve Dosyalar uygulamasından tek tıkla SyncDrop'a gönderme), UIPasteboard.
* **Android:** Kesintisiz veri akışı için Foreground Service, ClipboardManager, Intent Filter (`SendTo`).


* **Reklam Entegrasyonu:** Free sürüm için Google AdMob KMP bağlayıcısı.

#### B. Masaüstü İstemci: Tauri v2 (Rust + React / TypeScript)

* **Hafiflik & Performans:** Düşük bellek tüketimi (15–25 MB RAM) ile arka planda sessiz çalışma.
* **Sistem Entegrasyonu:** macOS Menü Çubuğu / Windows Sistem Tepsisi (Tray) popover pencereleri, global kısayollar (`Cmd+Shift+V` / `Ctrl+Shift+V`) ve Rust tabanlı yerel pano izleyicisi.
* **UI:** Düz (flat), gölgesiz, modern kutucuklar ve Nunito tipografisi.

#### C. Web Sitesi (Yalnızca Tanıtım & İndirme)

* **Altyapı:** Next.js (React + TypeScript) + Tailwind CSS.
* **Barındırma:** Vercel veya Cloudflare Pages.
* **İşlev:** WebDrop arayüzü barındırmaz. Sadece minimalist açılış sayfası (Landing Page), fiyatlandırma karşılaştırması ve işletim sistemini otomatik tespit eden indirme butonları yer alır.

---

### 5.2 Backend, Veritabanı ve Ağ Altyapısı

* **Veritabanı & Kimlik Doğrulama:** Supabase (PostgreSQL)
  Kullanıcı oturumları, cihaz eşleme tablosu (`devices`), aktif uçtan uca açık anahtarlar (`public_keys`) ve kullanıcı profilleri PostgreSQL üzerinde barındırılır.
* **Sinyalleşme:** Supabase Realtime (WebSocket)
  WebRTC SDP ve ICE Candidate el sıkışmalarını ek bir sinyalleşme sunucusuna gerek duymadan yönetir.
* **İşlemsel E-Posta:** **Amazon SES (Simple Email Service)**
* **6 Haneli OTP Kodları:** E-posta ile kayıt ve giriş işlemlerinde güvenli tek kullanımlık şifrelerin (OTP) yüksek teslimat oranıyla iletilmesi.
* **Kullanıcı Karşılama:** Kayıt sonrası şık ve minimalist karşılama (onboarding) e-postaları.
* **İşlemsel Bildirimler:** Güvenlik uyarıları, yeni cihaz eşleşme bildirimleri ve fatura/abonelik bilgilendirmeleri Amazon SES API / SMTP üzerinden tetiklenir.


* **Abonelik Altyapısı:** RevenueCat
  App Store ve Google Play faturalandırmasını yönetir; kullanıcının Pro statüsünü Supabase webhook'ları üzerinden canlı olarak günceller.
* **Yerel Ağ Keşfi:** mDNS / Bonjour (ZeroConf)
  Aynı Wi-Fi ağındaki cihazların sunucuya gitmeden birbirini milisaniyeler içinde keşfetmesi ve doğrudan yerel TCP/TLS soketleri üzerinden veri akışı sağlaması.
* **Uzaktan Aktarım & NAT Traversal:** WebRTC DataChannels + Coturn (VPS)
  Cihazlar arası P2P tüneli; kısıtlayıcı simetrik NAT arkasında kalan cihazlar için adanmış Coturn rölesi.
* **Geçici Dosya Saklama (Asenkron Transfer - Pro):** Cloudflare R2
  Alıcı çevrimdışıyken istemcide şifrelenmiş dosyaları barındırır (24 saat TTL, sıfır egress maliyeti).
* **Anlık Bildirim (Push Notification):** APNs & FCM
  Arka plandaki mobil cihazları uyandırmak ve panoyu sessizce senkronize etmek için yüksek öncelikli silent push altyapısı.

---

## 6. Güvenlik ve Gizlilik Spesifikasyonu

* **Uçtan Uca Şifreleme (E2EE):** Her cihaz ilk kurulumda yerel olarak bir Curve25519/Ed25519 anahtar çifti üretir; açık anahtar Supabase'e kaydedilir, özel anahtar asla cihazdan dışarı çıkmaz.
* **Oturum Güvenliği:** Transfer esnasında Diffie-Hellman anahtar değişimiyle tek kullanımlık simetrik oturum anahtarları üretilir ve veri paketleri AES-256-GCM ile şifrelenir.
* **Sıfır-Bilgi Prensibi (Zero-Knowledge):** Coturn rölesi veya Cloudflare R2 üzerinden geçen veriler istemci tarafında şifrelenmiş ham baytlardır; şifre çözme anahtarı yalnızca hedef cihazın belleğinde oluşturulur.

---

## 7. Kullanıcı Deneyimi, Giriş Akışları ve Kritik Kurallar

### 7.1 Kimlik Doğrulama & Profil Senkronizasyon Akışı

* **Giriş Yöntemleri:** Google Sign-In, Apple Sign-In ve E-Posta ile Giriş.
* **Google Sign-In Entegrasyonu:** Android tarafında SHA-1 parmak izi üretilip onay alındıktan sonra Google Cloud Console bağlantısı tamamlanacaktır.
* **E-Posta ile Giriş:** Kullanıcı e-postasını girdikten sonra **Amazon SES** üzerinden 6 haneli güvenli OTP doğrulama kodu gönderilir; kod doğrulandıktan sonra oturum açılır.
* **Profil Tamamlama Ekranı:**
* Sosyal girişler veya e-posta OTP doğrulamasının hemen ardından, eksik bilgi varsa minimalist bir "Profilini Tamamla" ekranı (İsim, Soyisim, E-posta) açılır.
* Form gönderildiğinde bilgiler hem özel Supabase kullanıcı profili tablosuna (`public.profiles`) hem de doğrudan `auth.users` metadata katmanına senkronize olarak yazılır.



### 7.2 Onboarding (İlk Karşılama) Deneyimi

* **3 Adımlı Sade Akış:**
1. *Cihazlar Arasında Özgürlük:* Dosyalarını kablosuz ve sınırsız paylaş.
2. *Ortak Pano:* Bir cihazda kopyala, diğerinde anında yapıştır.
3. *Uçtan Uca Gizlilik:* Dosyaların yalnızca senin cihazların arasında kalsın.


* **İzin İsteme Stratejisi:**
* Onboarding akışının son adımında kullanıcıya neden ihtiyaç duyulduğu sade bir dille açıklanarak Bildirim İzni istenir.
* Yerel Ağ İzni (iOS Local Network) ve Depolama/Galeri İzinleri, kullanıcı ilgili eylemi (cihaz arama veya dosya gönderme) ilk kez tetiklediğinde (JIT - Just-In-Time) native sistem diyaloglarıyla istenir.



### 7.3 Hata ve Sistem Uyarıları

* Arayüzde özel tasarlanmış süslü modal kutucuklar yerine, platformun kendi native diyalogları (iOS `UIAlertController`, Android `MaterialAlertDialog`, Masaüstünde OS native mesaj kutusu) kullanılır.

---

## 8. Faz Faz Geliştirme Yol Haritası

* **Faz 1: Çekirdek Protokol & Yerel Ağ Motoru (KMP & Rust)**
* Ortak JSON protokol şemasının ve veri paket formatının (Header + Chunk payload) netleştirilmesi.
* mDNS / Bonjour yerel ağ cihaz keşif kütüphanesinin kurulması.
* Cihazlar arası yerel TCP/TLS soket bağlantısı ve bayt akışı (dosya transfer prototipi).
* Curve25519 & AES-256-GCM uçtan uca şifreleme katmanının entegrasyonu.


* **Faz 2: Kimlik Doğrulama, Profil & Amazon SES Entegrasyonu**
* Supabase Auth şemasının oluşturulması (`auth.users` ve `public.profiles` senkronizasyon trigger'ları).
* Amazon SES SMTP / API ile 6 haneli e-posta OTP gönderim servisinin kurulması.
* SHA-1 parmak izi alımı ve Google Cloud Console / Apple Developer hesap bağlantıları.
* Profil tamamlama mantığının uçtan uca kodlanması.


* **Faz 3: İstemci Arayüzleri, Onboarding & İzinler**
* Mobil (Compose Multiplatform): Nunito fontları, flat tasarım dili, 3 adımlı onboarding ve native izin yöneticisi.
* Masaüstü (Tauri v2): Sistem tepsisi (tray), menü çubuğu popover penceresi, global kısayollar ve pano dinleyicisi.
* Google AdMob SDK entegrasyonu (Free mobil sürüm için banner & interstitial).


* **Faz 4: Uzaktan Transfer, WebRTC & Bulut Altyapısı**
* Supabase Realtime üzerinden WebRTC sinyalleşme kanalının kurulması.
* Coturn (STUN/TURN) röle sunucusunun VPS üzerinde Docker ile yapılandırılması.
* Cloudflare R2 asenkron transfer (Drop & Forget) bucket ve 24 saatlik yaşam süresi (TTL) otomasyonu.
* APNs & FCM yüksek öncelikli sessiz bildirim altyapısının mobil istemcilere bağlanması.


* **Faz 5: Gelir Modeli, Landing Page & Mağaza Yayını**
* RevenueCat SDK'nın KMP ve masaüstü tarafına entegre edilmesi, paket limit kontrollerinin kilitlenmesi.
* Next.js + Tailwind CSS ile minimalist tanıtım ve indirme web sitesinin (`getsyncdrop.com`) yayına alınması.
* App Store, Google Play Store ve masaüstü imzalı kurulum paketlerinin (`.dmg`, `.exe`) hazırlanması.
