### 1. Ana Mimari ve Temel Kurulum Promptu (System / Master Prompt)

> **Rol:** Sen kıdemli bir Full-Stack ve Çapraz Platform Sistem Mimarı/Geliştiricisisin.
> **Proje:** "SyncSwift" — Cihazlar arası kesintisiz, düşük gecikmeli evrensel pano (clipboard), P2P dosya senkronizasyonu ve mobil-masaüstü cihaz köprüsü sağlayan bir SaaS platformu.
> **Amaç:** Ücretsiz (Freemium) ve Pro katmanlarına sahip, arka planda (system tray / background worker) sessizce çalışan, minimal arayüzlü ve yüksek performanslı bir istemci-sunucu mimarisi kurmak.
> **Temel Kurallar:**
> 1. Kodları modüler, test edilebilir ve SOLID prensiplerine uygun yaz.
> 2. Veri transferlerinde uçtan uca şifreleme (E2EE) standartlarını gözet.
> 3. Arka plan kaynak tüketimini (CPU/RAM/Pil) minimumda tutacak event-driven yapılar kur.
> 4. Pro ve Ücretsiz ayrımını hem istemci tarafında hem de sunucu tarafında (feature flag / entitlement kontrolü) doğrula.
> 5. Arayüzü karmaşıklaştırmadan, arka planda sistem tepsisinde (tray) sessiz çalışan minimal bir UX/UI mimarisi kurgula.
> 
> 
> Şimdi sırasıyla özellikleri modül modül inşa edeceğiz. İlk olarak veri modellerini ve yetkilendirme (Ücretsiz/Pro) yapısını tanımlayarak başla.

---

### 2. Modüler Uygulama Promptları (Adım Adım Girdi İçin)

#### Adım 1: Freemium & Lisans Mantığı

> **Prompt:**
> "SyncSwift için kullanıcı yetkilendirme ve lisans kontrol katmanını (Entitlement Manager) oluştur.
> * **Free Plan:** Maksimum 2 kayıtlı cihaz (1 mobil + 1 masaüstü), manuel aktarım modu, son 24 saatlik geçici pano geçmişi.
> * **Pro Plan:** Sınırsız cihaz bağlama, tablet desteği, arka plan otomatik eşitleme, anlık bildirim/arama senkronizasyonu, sınırsız geçmiş arama, E2EE ve P2P büyük dosya modu.
> Kullanıcı oturum açtığında geçerli abonelik durumunu doğrulayan ve yetkisiz çağrılarda hata fırlatan güvenli bir servis katmanı yaz."
> 
> 

#### Adım 2: Pano Yöneticisi ve Sıfır Tıklama Otomatik Eşitleme

> **Prompt:**
> "İstemci tarafında çalışan Pano Dinleyicisi (Clipboard Monitor) modülünü geliştir.
> 1. Sistem panosundaki değişiklikleri işletim sistemi olayları üzerinden yakala (polling yerine event-based tercih et).
> 2. Eğer kullanıcı **Pro** ise ve 'Sıfır Tıklama / Otomatik Eşitleme' açıksa, kopyalanan metni şifreleyerek bağlı diğer cihazlara WebSocket üzerinden gecikmesiz ilet.
> 3. Eğer kullanıcı **Free** ise içeriği otomatik iletme; arayüzde 'Gönder' butonu tetiklendiğinde tek seferlik aktar.
> 4. Sonsuz döngü koruması ekle: Uygulamanın kendi yazdığı pano içeriğini tekrar algılayıp sunucuya göndermesini engelle."
> 
> 

#### Adım 3: İşletim Sistemi Yerel Paylaşım Sayfası (Native Share Sheet Entegrasyonu)

> **Prompt:**
> "Mobil (Android ve iOS) ve masaüstü işletim sistemlerinin yerel 'Paylaş' (Share Sheet) menüsü entegrasyonunu kodla:
> 1. Herhangi bir uygulamadan (Galeri, Dosyalar, Tarayıcı vb.) 'Paylaş' tıklandığında SyncSwift hedef seçenek olarak listelensin.
> 2. Kullanıcı SyncSwift'i seçtiğinde arkaplanda bağlı olan aktif cihazları listeyen hafif bir alt sayfa (bottom sheet) açılsın veya varsayılan masaüstü cihaza tek tıkla doğrudan aktarım başlasın.
> 3. Gönderilen veri tipi (URL, metin, PDF, görsel, video) otomatik ayrıştırılıp uygun transfer kuyruğuna (kısa veri ise WebSocket/E2EE, büyük dosya ise P2P) yönlendirilsin."
> 
> 

#### Adım 4: Telefon Bildirimleri ve Gelen Arama Köprüsü (Pro Özellik)

> **Prompt:**
> "Telefondaki gelen bildirimleri ve gelen aramaları masaüstüne anlık ileten `DeviceBridgeNotifier` modülünü yaz:
> 1. **Bildirim Senkronizasyonu:** Telefona gelen seçili uygulama bildirimlerini (WhatsApp, SMS vb.) dinle, şifreli soket üzerinden masaüstüne aktar ve masaüstü ekranının sağ üst/alt köşesinde kayan minimal bir kart (floating toast) olarak göster.
> 2. **Gelen Arama Uyarısı:** Telefona arama geldiğinde anlık olarak masaüstü ekranında özel bir kayan kart aç: Arayanın adı/numarası ve telefona dokunmadan bilgisayardan 'Sessize Al' veya 'Hızlı Mesajla Reddet' komutu gönderebilecek aksiyon butonları barındırsın.
> 3. İstemci gizlilik ayarı: Kullanıcının hangi uygulamaların bildirimlerini masaüstüne yansıtacağını seçebileceği yerel bir filtre listesi ekle."
> 
> 

#### Adım 5: Akıllı İçerik Algılama ve Hızlı Eylemler (Smart Actions)

> **Prompt:**
> "Panoya kopyalanan metni analiz edip sınıflandıran bir `ContentDetector` modülü yaz:
> * **URL:** Geçerli bir link ise hedef masaüstü cihazda 'Varsayılan tarayıcıda hemen sekme olarak aç' aksiyonu tetikle.
> * **Telefon Numarası:** Regex ile doğrula, tek tıkla arama veya WhatsApp mesajı başlatma şablonu oluştur.
> * **Renk Kodu:** HEX veya RGB formatındaysa renk önizleme paleti sun ve RGB/HEX dönüşüm butonları ekle.
> * **Geliştirici Formatları:** Terminal çıktıları veya çok satırlı kod bloklarını algıla, Markdown ve zengin metin (rich text) formatını koruyarak aktar.
> Tespit edilen türe göre minimal bir hızlı aksiyon objesi döndüren temiz bir sınıf yapısı kur."
> 
> 

#### Adım 6: Görsel Pano ve Ekran Görüntüsü Köprüsü (OCR Destekli)

> **Prompt:**
> "Görsel pano ve ekran görüntüsü senkronizasyon modülünü geliştir:
> 1. Telefonda ekran görüntüsü alındığında veya görsel kopyalandığında bunu anında bilgisayara kayan minimal bir pencere (toast/floating card) olarak düşür.
> 2. Kullanıcı görseli panoya aldığında, Pro kullanıcılara yerel/hafif bir OCR servisi üzerinden görseldeki metni ayıklayıp tek tıkla metin olarak kopyalama opsiyonu sun."
> 
> 

#### Adım 7: Güvenlik ve Kendini İmha Eden Paylaşım (Self-Destruct)

> **Prompt:**
> "Hassas veriler (tek kullanımlık şifreler, kod parçaları, kimlik görselleri) için `BurnAfterReading` / `EphemeralSync` özelliğini kodla:
> 1. Kullanıcı tek tıkla bir paylaşımı 'Geçici' olarak işaretleyebilmeli.
> 2. Belirlenen TTL (varsayılan 5 dakika) dolduğunda veya hedef cihaz panoya yapıştırdığı anda veri hem bellekten hem de yerel önbellekten tamamen silinmeli.
> 3. İlgili veri için bellek üstünde güvenli temizleme (zeroize / memory wipe) mantığını uygula."
> 
> 

#### Adım 8: P2P Dosya Aktarımı (Büyük Dosyalar İçin Doğrudan Akış)

> **Prompt:**
> "Büyük boyutlu dosyalar ve arşivler için WebRTC tabanlı P2P veri kanalı (DataChannel) altyapısını tasarla:
> 1. Sunucuyu sadece sinyal (signaling - SDP/ICE değişimi) için kullan, dosya baytlarını asla merkezi sunucuya yükleme.
> 2. Dosyaları sabit boyutlu chunk'lara (parçalara) bölerek cihaza doğrudan akış (streaming) ile aktar ve alıcı tarafta birleştir.
> 3. Aktarım ilerleme durumunu (yüzde, anlık hız) yayınlayan bir stream yapısı kur."
> 
> 

#### Adım 9: Kalıcı Arama, Favoriler ve Şablon Panolar

> **Prompt:**
> "Pano geçmişi ve sık kullanılanlar için yerel depolama ve arama modülünü kodla:
> 1. SQLite veya yerel anahtar-değer deposu kullanarak pano geçmişini şifreli olarak sakla (Free: 24 saat, Pro: sınırsız).
> 2. Sık kullanılan metinler (IBAN, fatura adresi, hazır şablonlar) için 'Sabitleme / Favori' yapısı kur ve bunlara global kısayol tuşları (hotkey) ata.
> 3. Pano geçmişi üzerinde hızlı ve tam metin araması (Full-Text Search) yapabilen arama motoru sınıfını oluştur."
> 
> 

#### Adım 10: Sistem Tepsisi (System Tray) ve Minimal UI Mimarisi

> **Prompt:**
> "Uygulamanın işletim sistemiyle görünmez ve pürüzsüz entegrasyonu için UI kabuğunu geliştir:
> 1. Uygulama açıldığında ekranda pencere açmak yerine sistem tepsisinde (tray icon) sessizce başlasın.
> 2. Hızlı bir global kısayol tuşu ile (örneğin Alt+V veya Cmd+Shift+V) ekranın ortasında açılan, klavye ile tamamen kontrol edilebilen, Raycast benzeri minimal bir pano arama penceresi oluştur.
> 3. Gelen aramaları, bildirimleri ve aktarım durumlarını ekranın köşesinde dikkat dağıtmayan, kaybolan bildirimler (floating card / HUD) olarak göster."