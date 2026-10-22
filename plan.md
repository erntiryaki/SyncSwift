SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
SyncSwift
KAPSAMLI ÜRÜN ŞARTNAMESİ • TEKNİK MİMARİ • GÜVENLİK • ABONELİK •
ANTIGRAVITY GELİŞTİRME PLANI
Sürüm: 1.0 | Karar kesiti: 22 Ekim 2026 | Dil: Türkçe | Durum: Onaylanmış kararlar + açık teknik tasarım notları
Bu belge, erişilebilir sohbet kararlarının ayrıntılı ve uygulanabilir bir sentezidir. Sözcüğü sözcüğüne sohbet
dökümü değildir. Onaylanan gereksinimler ile henüz test/teknik doğrulama gerektiren öneriler ayrı
işaretlenmiştir. Eksiksizlik iddiası yalnızca eldeki erişilebilir konuşma içeriği için geçerlidir.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
Belgeyi nasıl kullanmalı?
 [ONAYLI] Kullanıcının açıkça kabul ettiği ürün davranışı veya ticari karar.
 [TASARIM] Uygulama için önerilen teknik uygulama; geliştirmeden önce test ve güvenlik incelemesi
gerektirir.
 [SINIR] İşletim sistemi, ağ veya güvenlik nedeniyle garanti edilemeyen davranış.
 [KARAR GEREKİYOR] Ürün kararı açık değil; varsayılanı uygulamaya gömmeyin.
 Antigravity’ye bu belgeyi bağlam olarak verin; her fazı ayrı görev ve kabul testiyle uygulayın.
İçindekiler
01. Karar defteri ve ürün ilkeleri
02. Hedef platformlar ve uygulama sınırları
03. Sistem mimarisi ve bileşenler
04. Hesap, cihaz kimliği ve eşleştirme
05. Cihaz yetkileri ve erişim iptali
06. Uçtan uca şifreleme ve tehdit modeli
07. Anahtar yönetimi ve kurtarma
08. Dosya transfer motoru
09. Ağ keşfi, P2P ve relay
10. Geçici şifreli bulut teslimatı
11. Kalıcı kişisel bulut kasası
12. Ortak çalışma alanı ve dosya görünürlüğü
13. Otomatik klasör ve indirme senkronizasyonu
14. Pano senkronizasyonu
15. Uzak Windows dosya yöneticisi
16. Çevrimdışı dosya ve klasörler
17. Sürümleme, çakışma, çöp kutusu
18. Mobil ve masaüstü işletim sistemi sınırları
19. Arayüz, ekranlar ve kullanıcı akışları
20. Free ve Pro abonelik matrisi
21. Fiyatlandırma, RevenueCat ve hak yönetimi
22. Kotalar, ölçüm ve faturalandırma
23. Veri modeli ve RLS ilkeleri
24. API, mesajlar ve iş kuyruğu
25. Performans, pil ve kaynak bütçesi
26. Güvenlik ve gizlilik kontrol listesi
27. Operasyon, gözlemlenebilirlik ve destek
28. Antigravity klasör yapısı ve kod standartları
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
29. Aşamalı geliştirme planı
30. Test senaryoları ve kabul kriterleri
31. Riskler, belirsizlikler ve açık kararlar
32. Yayın, ölçüm ve yol haritası
33. Kararların izlenebilirlik özeti
34. Antigravity için ana uygulama promptu
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
01. Karar defteri ve ürün ilkeleri
[ONAYLI] SyncSwift; Android, iOS ve Windows cihazları arasında güvenli dosya aktarımı, senkronizasyon, pano,
uzak dosya yönetimi ve şifreli bulut kasaları sağlayan bir üründür.
Ürün vizyonu
 Yerel ağdaki aktarım ücretsiz, hızlı ve sınırsız olmalıdır.
 Uzaktan kullanım, otomasyon, kalıcı bulut ve ekip çalışması Pro değerini oluşturur.
 Bütün dosya, pano, geçici bulut ve kasa içerikleri uçtan uca şifreli olmalıdır.
 Güvenlik özellikleri ödeme duvarının arkasına konulmaz.
 Kullanıcı arayüzü modern, sade, kurumsal ve yumuşak olmalıdır.
Öncelik sırası
 1. Güvenlik ve veri bütünlüğü; 2. Kullanıcı kontrolü; 3. Dosya kaybetmeme; 4. Bağlantı dayanıklılığı; 5.
Performans; 6. Sade arayüz.
 Sistem sessiz veri silme veya sürüm ezme yapmamalıdır.
 Çalışmayan OS yeteneği varmış gibi gösterilmemelidir.
02. Hedef platformlar ve uygulama sınırları
[ONAYLI] İlk hedef Android, iOS, Windows; macOS daha sonra. Ortak kod Kotlin Multiplatform ve Compose
Multiplatform temellidir.
Uygulamalar
 Android: paylaşım alma/gönderme, izinli dosya seçimi, pano ve indirme kısıtlarına uyum.
 iOS: belge seçici, paylaşım uzantısı ve izinli arka plan görevleri; keyfi arka plan taraması yok.
 Windows: Compose masaüstü istemcisi + kalıcı Windows Sync Agent; sistem oturumunda izinli dosya
işlemleri.
 macOS: çekirdek mimariye hazırlık; ilk sürümün yayın taahhüdü değildir.
Sınırlar
 Windows Agent genel yönetici ayrıcalıklarıyla çalışmak zorunda olmamalı; yalnızca kullanıcının açıkça
paylaştığı klasörlerde çalışmalıdır.
 Ortak kod ağ protokolü, durum makineleri, veritabanı modelleri ve iş kurallarını paylaşır; OS izinleri yerel
adaptörlerle yönetilir.
03. Sistem mimarisi ve bileşenler
[TASARIM] Kontrol düzlemi ve veri düzlemi ayrılır. Supabase kullanıcı/cihaz/izin meta verisini, VPS bağlantı
sinyalleme ve relay hizmetini, S3 uyumlu nesne depolama şifreli blobları tutar.
Temel servisler
 Supabase Auth: hesap, oturum, kimlik doğrulama.
 PostgreSQL + RLS: cihaz kayıtları, yetkiler, paylaşım kuralları, iş durumu, abonelik özeti.
 Ktor: Kotlin ağ istemcisi, protokol ve servis API çağrıları.
 SQLDelight: çevrimdışı istemci durumu, kuyruk, manifest, cache meta verisi.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
 VPS/Docker: WebSocket sinyalleme, gerektiğinde TURN/relay, temizleme işçileri.
 S3 uyumlu depolama: E2EE geçici dosyalar ve kalıcı kasalar.
 RevenueCat: Android/iOS mağaza abonelikleri; backend yetki kaynağı doğrulaması.
Mimari sınır
 Sunucu dosya veya pano içeriğinin düz metnini okuyamaz.
 Windows Agent açıkça izinli yerel dosyaları düz metin görebilir; yerel diskin tamamı E2EE ile otomatik
korunmuş sayılmaz.
 Sunucu meta verileri (IP, boyut, zaman, hesap ilişkisi) kısmen görebilir; sıfır meta veri iddiası yapılmaz.
04. Hesap, cihaz kimliği ve eşleştirme
[ONAYLI] Yeni cihaz, aynı hesaba giriş yapsa bile mevcut güvenilir cihazdan onay almadan dosya, pano, kasa
anahtarları ve uzak klasörlere erişemez.
Durum makinesi
 PENDING: doğrulanmış hesap altında bekleyen cihaz; erişim kapalı.
 APPROVED: güvenilir cihaz onayladı, kriptografik anahtar aktarımı tamamlanıyor.
 TRUSTED: cihaz aktif, kapsamlı izin kontrolü uygulanıyor.
 REJECTED: mevcut cihaz tarafından reddedildi.
 CANCELLED: isteyen cihaz iptal etti.
 EXPIRED: önerilen 5 dakikalık tek kullanımlık eşleştirme süresi doldu.
 REVOKED: önceden güvenilir cihaz erişimi kesildi.
Eşleştirme akışı
 Cihaz yerel asimetrik anahtar çiftleri üretir ve güvenli anahtar deposunda saklar.
 Yeni cihaz Supabase Auth ile kimlik doğrular; PENDING kaydı ve tek kullanımlık süreli eşleştirme oturumu
oluşturur.
 Güvenilir cihaz istek ayrıntısını gösterir; QR veya kısa doğrulama koduyla fiziksel doğrulama seçeneği
sunulur.
 Onay/ret/iptal eşzamanlı geldiğinde tek atomik durum geçişi kazanır; işlem idempotenttir.
 Onay sonrasında imzalı cihaz kimliği doğrulanır, anahtarlar sadece yetkili cihazlara sarılarak dağıtılır.
İlk cihaz ve kayıp cihaz
 İlk cihaz için e-posta doğrulaması, oturum güvenliği ve güvenilir cihaz başlatma koruması gerekir.
 Tüm cihazlar kaybolursa kullanıcı tarafından saklanan kurtarma anahtarı olmadan eski şifreli veri
kurtarılamaz.
 Eşleştirme isteklerine hız sınırı, kötüye kullanım uyarısı ve denetim kaydı uygulanır.
05. Cihaz yetkileri ve erişim iptali
[ONAYLI] Cihaz bazında gönderme, alma, pano, uzak dosya yönetimi ve kasa erişimi ayrı yönetilir; uzak yönetim
varsayılan kapalıdır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
İzinler
 device.send, device.receive, clipboard.sync, remote.browse, remote.write, vault.personal, workspace.access
gibi ayrı izinler.
 Klasör başına read-only/full access; kullanıcı ve cihaz kesişimindeki en dar izin geçerlidir.
 Cihaz ekranı çevrimdışı kayıtları da gösterir; OS, son görülme, sürüm, yetenek ve son güvenlik olayı
görüntülenir.
Erişim iptali
 REVOKED durumu bütün hassas API ve relay işlemlerinde sunucu tarafında kontrol edilir.
 Aktif bağlantılar kapatılır, yeni veri anahtarları ve kasa anahtarları etkilenen üyeler için döndürülür.
 Çevrimdışı cihaza bir sonraki bağlantıda uygulama verilerini silme talimatı gönderilir; teslim/uygulama
garanti edilemez.
 Önceden indirilmiş veya dışa aktarılmış kopyalar geri alınamaz; ürün bu sınırı açıkça belirtir.
 Supabase çıkış işlemi tek başına verilmiş JWT erişimlerini anında sonlandırmayabilir; ayrı revocation
kontrolü zorunludur.
06. Uçtan uca şifreleme ve tehdit modeli
[ONAYLI] Dosyalar, pano içerikleri, geçici bulut nesneleri, kişisel ve ortak kasalar E2EE olacaktır. [TASARIM]
Olgun kripto kütüphaneleri kullanılır.
Tehdit modeli
 Ağ dinleyicisi, VPS operatörü, depolama sağlayıcısı, çalınan token, yetkisiz eşleştirme, kötü niyetli cihaz,
replay, path traversal, sunucu ele geçirilmesi.
 Uç nokta zararlısı ve kullanıcı tarafından dışa aktarılmış düz metin kopyalar E2EE tarafından bütünüyle
engellenemez.
 TLS bağlantı korumasıdır; E2EE içerik koruması ayrıca yapılır.
Kriptografik bileşenler
 Cihaz kimliği için Ed25519 imza anahtarları; anahtar anlaşması için X25519 ve doğrulanmış el sıkışma.
 Dosya/mesaj şifrelemesi için XChaCha20-Poly1305 veya denetlenmiş eşdeğer AEAD; HKDF-SHA256 anahtar
türetme.
 Kurtarma parolasıyla türetme gerekiyorsa Argon2id; yüksek entropili rastgele kurtarma anahtarı tercih
edilir.
 Büyük dosyalar için bağımsız doğrulanabilir parça AEAD, manifest imzası, bağlam ilişkili ek veri (AAD) ve
benzersiz nonce.
 libsodium secretstream tek başına rastgele erişimli parça resume garantisi sayılmaz; tasarım test edilmelidir.
Veri koruma
 Dosya adı, dizin yolu, pano metni ve hassas meta veriler mümkün olduğunca şifrelenir.
 İçerik anahtarları sunucuya düz metin gitmez.
 Her dosya için bağımsız DEK; DEK yetkili cihaz veya kasa anahtarlarıyla sarılır.
 Hash/manifest bütünlüğü ve replay önleme; yetkisiz cihazın imzası kabul edilmez.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
07. Anahtar yönetimi ve kurtarma
[ONAYLI] Hem güvenilir cihazla kurtarma hem kullanıcı kontrollü kurtarma anahtarı vardır.
Anahtar hiyerarşisi
 Cihaz kimliği imza ve anlaşma anahtarları OS güvenli deposunda saklanır.
 Kişisel kasa anahtarı hesap cihazlarına yetki bazlı sarılır.
 Ortak alan anahtarı her üyeye/cihaza ayrı sarılır; üyelik değişiminde uygun anahtar döndürme gerekir.
 Dosya DEK ve manifest anahtar sürümü içerir; sürümleme geçmiş şifreli nesneleri açıkça bağlar.
Kurtarma yolları
 Güvenilir mevcut cihaz yeni cihazı onaylar ve gerekli anahtarları güvenli kanaldan verir.
 Kullanıcı yüksek entropili kurtarma anahtarını kaydeder; sunucuda yalnızca bu anahtarla açılabilen şifreli
yedek tutulur.
 Kurtarma işleminde ek kimlik doğrulama, bildirim ve şüpheli işlem gecikmesi/rate limit uygulanır.
 Parola sıfırlama E2EE anahtarlarını otomatik geri getirmez.
Anahtar döndürme
 Cihaz iptali ve ortak alan üyeliği kaldırmada yeni yazılan veriler için yeni anahtar sürümü zorunludur.
 Eski şifreli nesnelerin yeniden şifrelenmesi erişim politikasına göre planlanır; eski cihazın daha önce
öğrendiği sırlar geri alınamaz.
 Anahtar yedeği, restore, eşzamanlı üyelik değişimi ve yarış durumları entegrasyon testlerinde doğrulanır.
08. Dosya transfer motoru
[ONAYLI] Aktarımlar devam ettirilebilir, şifreli, kuyruklu ve güvenilir olmalıdır.
Transfer aşamaları
 Dosya seçimi veya otomatik tetik; kaynak yetkisi; hedef kapasite/izin doğrulaması.
 Manifest: transfer_id, content_id, sürüm, parça sayısı, boyut, parça hashleri/etiketleri, şifreleme anahtar
sürümü.
 Önerilen ilk parça boyutu 1 MiB; ağ ve cihaz profiline göre ölçümle ayarlanır.
 Parçalar akış halinde şifrelenir; tamamlanan parçalar SQLDelight üzerinde checkpoint olarak tutulur.
 Eksik parça listesiyle resume; teslim sonrası bütünlük kontrolü, atomik son dosya adı ve alıcı onayı.
 Çift işlenme önleme: idempotency key, transfer state machine ve retry/backoff.
Transfer durumları
 QUEUED, PREPARING, CONNECTING, TRANSFERRING, VERIFYING, DELIVERED, PAUSED, RETRY_WAIT,
FAILED, CANCELLED, EXPIRED.
 Aynı dosyanın farklı hedeflere teslimi hedef başına ayrı onay ve kuyruk durumuyla izlenir.
Hata yönetimi
 Ağ değişimi, pil kısıtı, uygulama kapanması, hedef dolması, kaynak dosya değişmesi ve yetki iptalinde
kontrollü duraklama.
 Diskte kısmi dosyalar görünür nihai dosya olarak sunulmaz.
 İlerleme yüzdesi doğrulanmış bayt üzerinden hesaplanır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
09. Ağ keşfi, P2P ve relay
[ONAYLI] Bağlantı önceliği yerel ağ, mümkünse doğrudan internet P2P, son çare relay.
Keşif
 LAN cihaz keşfi için mDNS/DNS-SD; keşif sonucu yetki ve cihaz kimliği doğrulaması yapılmadan güvenilir
sayılmaz.
 Dış ağ bağlantıları için sinyalleme ve ICE/STUN/TURN benzeri uygun mekanizmalar.
 Doğrudan bağlantı mümkün değilse TLS ile korunan E2EE relay; relay düz metni açamaz.
Güvenilirlik
 Bağlantı türü kullanıcıya LAN / P2P / Relay olarak gösterilebilir.
 Ağ değişiminde aktarım yeniden başlatılmadan devam eder; bağlantı yolu değişse bile kriptografik oturum
bağlamı korunur.
 Relay bant genişliği ölçümü sunucu tarafında güvenilir sayılır; istemcinin raporuna körü körüne güvenilmez.
10. Geçici şifreli bulut teslimatı
[ONAYLI] Çevrimdışı hedefe gönderimde geçici E2EE staging kullanılır; varsayılan üst saklama süresi 7 gündür.
Akış
 Gönderici yetkili alıcıya sarılmış anahtarla dosyayı şifreleyerek nesne depolamaya yükler.
 Backend hedef başına pending delivery oluşturur; hedef çevrimiçi olduğunda bildirim/çekme yapılır.
 Alıcı indirir, doğrular ve teslim onayı verir; çoklu alıcıda her biri ayrı izlenir.
 Tüm alıcılar teslim aldıysa veya TTL dolduysa temizleme işçisi nesneleri siler.
Kısıtlar
 7 gün azami saklama penceresidir; sonsuz çevrimdışı teslim garantisi yoktur.
 Geçici bulutun depolama ve trafik tüketimi kotadan bağımsız sınırsız sunulmaz.
 Başarısız teslim, iptal, quota, yarım kalan yükleme ve cleanup idempotent olmalıdır.
11. Kalıcı kişisel bulut kasası
[ONAYLI] Şifreli kişisel kasa kalıcı depolamadır; Free 2 GB, Pro 20 GB toplam bulut kotasına dahildir.
İşlevler
 Dosya/klasör oluşturma, yükleme, indirme, paylaşım hedefi seçme, sürüm geçmişi, çöp kutusu.
 Bulut varsayılan kalıcı kasa kaynağıdır; otomatik yerel indirme opsiyoneldir.
 Nesne içeriği ve hassas adlar E2EE; metadata minimum tutulur.
Kota davranışı
 Kota aşılınca yeni kalıcı yükleme durur; mevcut dosyaların okuma/indirmesi sürer.
 Kullanıcı kota doluluk ve paylaşılan alan tüketimini açıkça görür.
 Silinen dosyaların kotadan ne zaman düşeceği çöp kutusu ve retention kuralına bağlı açık tanımlanmalıdır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
12. Ortak çalışma alanı ve dosya görünürlüğü
[ONAYLI] Pro: 1 ortak çalışma alanı, 5 üye; roller admin/editor/viewer ve dosya bazlı görünürlük.
İşlevler
 Davet ve üyelik yönetimi, dosya/klasör yükleme, indirme, paylaşım, sürüm ve erişim günlüğü.
 Her dosya için görünür kullanıcı/rol kapsamı; üst klasör yetkisi ile dosya özel kuralının kesişimi.
 Varsayılan cloud-only; kullanıcı isterse yerel otomatik indirme veya çevrimdışı pin.
 Üye çıkarıldığında gelecekteki anahtar dağıtımı durur ve gerekli kasa anahtarları döndürülür.
Kota yorumu
 20 GB Pro alanı kişisel + kullanıcıya tahsis edilmiş ortak çalışma alanı toplamıdır; ortak alan için ikinci
ücretsiz 20 GB yoktur.
 Çalışma alanı sahibi kotadan sorumludur; davetli kullanıcının kendi kotası otomatik paylaşılmaz. [TASARIM]
 Üye sayısı en fazla 5 kişi olarak uygulanır; sahibi dahil olup olmayacağı ürün metninde netleştirilmelidir.
[KARAR GEREKİYOR]
13. Otomatik klasör ve indirme senkronizasyonu
[ONAYLI] Seçili klasör senkronizasyonu ve yeni indirilen dosyaları algılama birbirinden bağımsız açılır/kapanır.
Klasör eşitleme
 Free: 1 senkronize klasör; Pro: sınırsız tanımlı klasör (cihaz kaynaklarına tabidir).
 Her kural için kaynak, hedef cihaz, tek yönlü/çift yönlü/yedekleme modu, dosya türü, boyut, Wi-Fi ve şarj
şartı.
 Silme yayılımı varsayılan kapalı; açıkça onay gerektirir.
 Dosya kimliği, kaynak olay kimliği ve sürüm vektörü ile döngü önleme.
İndirme algılama
 Windows: izinli dizinleri FileSystemWatcher benzeri mekanizmayla izleme ve yeniden tarama.
 Android: izin verilen dosya/medya erişimlerine ve arka plan sınırlarına uyum.
 iOS: keyfi Downloads klasörü ve sürekli arka plan izlemesi yok; kullanıcı paylaşımı/belge seçimi esas.
 Yeni indirilenler varsayılan tek yönlü kopyalanır; silmeler karşı tarafa yayılmaz.
14. Pano senkronizasyonu
[ONAYLI] Metin, URL ve görsel; dosya panosu yerine dosya transfer motoru kullanılır.
Plan davranışı
 Free: metin + URL; geçmişte son 5 öğe.
 Pro: metin + URL + görsel; geçmiş 7 güne kadar.
 Görsel için başlangıç üst sınır önerisi 10 MB; kalite korunur.
OS kısıtları
 Windows Agent pano olaylarını izleyebilir ve izinli otomatik yapıştırma/pano güncellemesi yapabilir.
 Android 10+ arka plan clipboard erişimi sınırlıdır; kullanıcı etkileşimi ve sistem izinleri esas alınır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
 iOS arka planda sınırsız pano izleme/yazma sunmaz; gelen içerik uygulama içinde gösterilir, kullanıcı
dokunarak kopyalar.
Güvenlik
 Her pano olayı E2EE; event_id + kaynak cihaz + içerik hash + recently-applied kayıtlarıyla sonsuz döngü
önlenir.
 Hassas metin filtreleme yalnızca mümkün olan kaynaklarda best-effort; parola yakalanmaz garantisi
verilmez.
 Pano geçmişi silme ve cihaz bazlı pano devre dışı bırakma sağlanır.
15. Uzak Windows dosya yöneticisi
[ONAYLI] Windows Agent açıkça paylaşılan klasörleri Android/iOS üzerinden yönetir.
Free ve Pro ayrımı
 Free: izin verilmiş uzak klasörleri listeleme, önizleme ve indirme (salt-okunur).
 Pro: yükleme, yeniden adlandırma, taşıma, kopyalama, yeni klasör, silme/çöp kutusu, çoklu seçim ve arama.
Güvenlik
 Remote manager varsayılan OFF; her klasör ayrı açık paylaşım onayı ister.
 İstemci doğrudan keyfi işletim sistemi yolu gönderip çalıştıramaz; Agent paylaşılan kök içinde canonical path
kontrolü yapar.
 Symlink/junction ve path traversal kaçışları engellenir; OS ACL ve kullanıcı oturumu yetkileri aşılmaz.
 Her komut için request_id, izin kontrolü, sürüm/ETag önkoşulu ve denetim kaydı.
Önizleme ve çevrimdışı durum
 Resim/PDF/video ve desteklenen belge önizlemeleri; küçük resim ve sayfalı listeleme.
 Windows kapalıysa yalnızca önceden bulutta saklanan veya açıkça çevrimdışı sabitlenmiş dosyalar
erişilebilir.
 Uzak silme varsayılan geri alınabilir çöp kutusuna gider.
16. Çevrimdışı dosya ve klasörler
[ONAYLI] Kullanıcı uzak dosyaları veya klasörleri çevrimdışı erişim için yerel korumalı depoya sabitleyebilir.
Davranış
 Remote-only, downloading, offline-ready, newer-version, conflict durumları gösterilir.
 İsteğe bağlı Wi-Fi-only, otomatik güncelleme ve klasöre sonradan eklenen dosyaları getirme.
 Önerilen başlangıç çevrimdışı kota 5 GB; ürünün bulut kotasıyla karıştırılmaz.
 Sabitlenmiş dosyalar cache temizliğiyle sessizce silinmez; kota dolunca yeni indirmeler durur.
 Çevrimdışı düzenleme varsayılan ayrı kopya üretir; sessiz çift yönlü yazma yok.
Senkronizasyon
 Kaynak çevrimiçiyken manifest karşılaştırılır, değişen parçalar indirilir.
 Kaynak kapalıyken güncellik garantisi verilmez; son eşitlenme zamanı gösterilir.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
17. Sürümleme, çakışma, çöp kutusu
[ONAYLI] Veri kaybını önlemek için sürüm ve çakışma mantığı gerekir. Pro sürüm geçmişi 30 gündür.
Sürüm modeli
 content_id, version_id, parent_version, modified_at, device_id ve doğrulanmış manifest ile değişiklikler takip
edilir.
 İki cihaz eşzamanlı değiştirirse sessiz overwrite yapılmaz; conflict copy veya kullanıcıya seçim sunulur.
 Yeniden adlandırma/taşıma ayrı metadata işlemleri; idempotent ve yetki kontrollü.
Silme
 Klasör sync için delete propagation varsayılan OFF.
 Kasa ve uzak yönetici silmeleri mümkünse geri alınabilir çöp kutusuna taşınır.
 Retention ve kota hesaplamasında çöp kutusu davranışı ürün politikasında açık olmalıdır.
18. Mobil ve masaüstü işletim sistemi sınırları
[SINIR] Platform davranışları aynı değildir; ortak UI yalnızca gerçek desteklenen özellikleri sunmalıdır.
Android
 Dosya seçici ve izinli dizinler; kapsamlı depolama izni yalnızca gerçekten gerekli ve mağaza politikasına
uygunsa.
 Arka plan görevlerinde Foreground Service/WorkManager gibi uygun mekanizmalar ve kullanıcı bildirimi.
 Clipboard arka plan okuma kısıtları ve üretici pil optimizasyonları test edilir.
iOS
 Document Picker, Files integration ve Share Extension; erişim verilen dosya URL scope sınırları.
 BGTaskScheduler garanti sürekli çalışma sunmaz; pano veya Downloads otomatik izlemesi iddia edilmez.
 Gelen aktarım bildirimleri ve kullanıcı başlatmalı görevlerle güvenilir akış.
Windows
 Windows Agent kullanıcı oturumuyla başlar; güncelleme, çökme sonrası toparlama ve servis sağlığı izlenir.
 Dosya watcher olay kaybına karşı periyodik tarama; clipboard listener ve güvenli IPC.
19. Arayüz, ekranlar ve kullanıcı akışları
[ONAYLI] Kurumsal-yumuşak, modern, sade, kutu yoğunluğu düşük ve gösterişli efektlerden uzak UI.
Ana ekranler
 Karşılama/oturum, cihaz eşleştirme, cihazlar, gönder/al, aktarım kuyruğu, dosyalar, kişisel kasa, ortak alan,
uzak bilgisayar, pano geçmişi, senkronizasyon kuralları, ayarlar, abonelik.
 Cihaz kartlarında güvenilirlik, çevrimiçi durum, son görülme, erişim izinleri.
 Dosya satırlarında boyut, son değişiklik, kaynağı, offline durumu, paylaşım görünürlüğü.
Temel kullanıcı akışları
 Yeni kullanıcı ilk cihazını kurar, kurtarma anahtarını kaydeder, güvenlik özetini onaylar.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
 İkinci cihaz giriş yapar, PENDING gösterilir, güvenilir cihazdan onay gelir.
 Kullanıcı dosya seçer, hedefi seçer, LAN/P2P/relay otomatik seçilir, transfer devam eder.
 Kullanıcı Windows klasörünü paylaşır, uzaktan salt-okunur veya tam yetki verir.
 Kullanıcı Pro satın alır, entitlement güncellenir, ilgili özellikler hesap çapında açılır.
UX ilkeleri
 Güvenlik ve yetki soruları anlaşılır, tekrarsız ve zamanında gösterilir.
 Aktarım hatalarında dosya kaybı yok, anlaşılır yeniden dene/duraklat/iptal eylemleri vardır.
 İzin verilmeyen OS özelliği için boş anahtar yerine açıklama gösterilir.
20. Free ve Pro abonelik matrisi
[ONAYLI] İki plan: Free ve Pro. Güvenlik temel hak olarak ücretsizdir.
Özellik Free Pro
Kayıtlı cihaz 3 10
LAN aktarımı Sınırsız Sınırsız
Doğrudan P2P Sınırsız Sınırsız
LAN dosya boyutu Sınırsız Sınırsız
Aylık relay 2 GB 50 GB
Kalıcı şifreli bulut 2 GB 20 GB
Otomatik senkron klasörü 1 Sınırsız
Yeni indirme algılama Yok Var (OS izinliyse)
Pano Metin, URL Metin, URL, görsel
Pano geçmişi Son 5 öğe 7 gün
Uzak dosya yöneticisi Görüntüle, indir Tam yönetim
Çevrimdışı klasör sabitleme Yok Var
Ortak çalışma alanı Yok 1
Çalışma alanı üyeleri Yok 5
Sürüm geçmişi Yok 30 gün
E2EE Var Var
Güvenli eşleştirme Var Var
Cihaz iptal etme Var Var
Not: “Sınırsız” ağ, OS, disk, güvenlik ve adil kullanım sınırlarından bağımsız sonsuz kapasite taahhüdü değildir;
yerel transfer için sunucu kota sınırı konmaz.
21. Fiyatlandırma, RevenueCat ve hak yönetimi
Bölge Pro aylık Pro yıllık
Türkiye 149,99 TL 999,99 TL
USD $2.99 $19.99
EUR €2.69 €17.99
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
[ONAYLI] Bu rakamlar sabit mağaza fiyat hedefleridir; kurla anlık otomatik güncellenmez. Ülke vergileri ve
mağaza fiyat kademeleri nihai tüketici fiyatını etkileyebilir.
21.1 RevenueCat teknik akış
[ONAYLI] RevenueCat ile mobil mağaza aboneliği; aynı Supabase hesabında Windows erişimi.
Ürün tanımları
 Entitlement: pro; offering: default; ürün kimlikleri: pro_monthly, pro_yearly.
 Android Google Play Billing ve iOS App Store satın alımları RevenueCat üzerinden yönetilir.
 Windows doğrudan mobil SDK kullanmaz; backend plan/entitlement API çağrısı yapar.
Sunucu güvenliği
 RevenueCat webhook imza/yetki doğrulaması ve event deduplication.
 Abonelik durumu, yenileme, iptal, ödeme başarısızlığı, grace period, refund ve restore olayları işlenir.
 İstemcinin gönderdiği pro=true bayrağına güvenilmez.
 Kullanıcı hesap eşleştirmesi Supabase Auth UUID ile yapılır; yanlış hesaba entitlement bağlanması önlenir.
Satın alma deneyimi
 Paywall, aylık/yıllık fiyat, deneme varsa koşullar, satın al, geri yükle, aboneliği yönet, şartlar/gizlilik.
 Uygulama mağazalarının platforma özgü ödeme ve dış bağlantı kurallarına uyulur.
22. Kotalar, ölçüm ve faturalandırma
[ONAYLI] Free 2 GB bulut ve 2 GB/ay relay; Pro 20 GB bulut ve 50 GB/ay relay.
Ölçüm
 Kalıcı bulut kapasitesi: hesap/çalışma alanı sahibine tahsis edilen şifreli nesnelerin muhasebeleştirilmiş byte
toplamı.
 Relay: sunucudan gerçek geçen dosya verisi; LAN ve doğrudan P2P düşülmez.
 İstemci tarafından iddia edilen tüketim değil, sunucu kaydı esas alınır.
 Geçici staging saklama/indirme için ayrıca sınır ve tüketim kuralları gerekir; bu belge sınırsız ücretsiz staging
onaylamaz.
Kota aşımı
 Bulut dolunca yeni yükleme durur; mevcut dosyalar okunur/indirilir.
 Relay dolunca LAN/P2P sürer, VPS relay aktarımı bir sonraki dönem veya yükseltme bekler.
 Cihaz limiti dolunca yeni cihaz güvenilir yapılamaz; cihaz kaldırma veya yükseltme istenir.
 Pro iptalinde ücretsiz limite düşüş; 30 günlük geçiş süresinde fazla bulut verisi okuma/indirme korunur.
Dönem
 Ücretsiz hesap relay sayacı aylık dönemle yenilenir.
 Ücretli hesap relay sayacı abonelik dönemiyle eşleştirilir; yıllık plan için aylık 50 GB kullanım dilimi
tanımlanır. [TASARIM]
 Kota artırımı, rollover, satın alınabilir ek paket ve adil kullanım henüz onaylanmamıştır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
23. Veri modeli ve RLS ilkeleri
[TASARIM] Örnek PostgreSQL varlıkları; gerçek migration öncesinde veri yaşam döngüsü ve indeksler gözden
geçirilmelidir.
Çekirdek tablolar
 users/profile; devices(id, user_id, status, signing_pubkey, agreement_pubkey, last_seen); pairing_sessions(id,
requester, approver, expires_at, state).
 device_permissions; shared_roots; remote_grants; device_revocations; audit_events.
 transfers; transfer_recipients; transfer_chunks; delivery_objects; upload_sessions.
 vaults; vault_members; vault_keys_wrapped; files; file_versions; file_visibility; trash_items.
 sync_rules; sync_checkpoints; clipboard_events_metadata; offline_pins; client_capabilities.
 subscriptions; entitlement_snapshots; quota_usage; quota_periods; billing_events.
RLS ve yetki
 Her satırda kullanıcı/çalışma alanı kapsamı doğrulanır; sadece RLS ile yetinilmeyen hassas endpointlerde
sunucu yetki kontrolü tekrarlanır.
 Revoked cihazlara şifreli nesne URL imzalama dahil yeni veri erişimi verilmez.
 Signed URL süreleri kısa; yetki kontrolünden sonra oluşturulur; nesne anahtarı tahmin edilemez.
 Service role anahtarları istemciye gömülmez.
24. API, mesajlar ve iş kuyruğu
[TASARIM] JSON/Protobuf şemaları versiyonlanır; mesajlar yetkili cihaz tarafından imzalanır ve gerektiğinde
E2EE içerik taşır.
Örnek uç noktalar
 POST /devices/pairing/request; POST /devices/pairing/{id}/approve|reject|cancel; POST /devices/{id}/revoke.
 GET /devices; PATCH /devices/{id}/permissions; POST /transfers; POST /transfers/{id}/resume; POST
/transfers/{id}/ack.
 GET /remote/roots; POST /remote/commands; GET /vault/files; POST /vault/uploads; GET
/subscription/entitlements; GET /quota.
İş kuyrukları
 Transfer queue: öncelik, retry/backoff, checksum, resumable offset, cancellation.
 Clipboard queue: küçük olaylara öncelik, TTL, deduplication.
 Cleanup queue: staging expiry, abandoned uploads, trash retention, revoked key rotation tasks.
Sözleşme kuralları
 Tüm mutasyonlarda request_id/idempotency_key; sürüm kontrolü; audit event.
 API dönüşlerinde makinece okunabilir hata kodu, kullanıcıya gösterilecek güvenli açıklama.
 Backend plaintext dosya adı veya içerik istemez; arama için izinli cihaz tarafı indeksleme tercih edilir.
25. Performans, pil ve kaynak bütçesi
[TASARIM] Performans sayısal hedefleri ilk beta ölçümleriyle belirlenir.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
Temel strateji
 1 MiB başlangıç chunk, streaming encryption, sınırlı eşzamanlı parça ve bağlantı sayısı.
 İşletim sistemi kaynak/termal/batarya durumuna göre eşzamanlılık düşürülür.
 Küçük pano olayları büyük dosya aktarımının arkasında beklemez.
 Windows watcher için debouncing ve periyodik reconciliation; gereksiz tam dizin taraması azaltılır.
Metrikler
 P50/P95 eşleştirme süresi, LAN throughput, P2P başarı oranı, relay fallback oranı.
 Aktarım tamamlama oranı, resume başarı oranı, parça tekrar gönderimi, mobil pil tüketimi, bellek zirvesi.
 Bulut byte-gün maliyeti, relay çıkış GB maliyeti, Pro kullanıcı başı maliyet.
26. Güvenlik ve gizlilik kontrol listesi
[TASARIM] Ürün yayını öncesi kapatılması gereken güvenlik kontrolleri.
Kripto ve anahtar
 Denetlenmiş kütüphaneler; nonce tekrarını engelleme; AEAD AAD; key zeroization imkanları.
 Kayıp anahtar ve yanlış kurtarma anahtarı senaryoları; backup ve restore testleri.
 Cihaz kimliği değişimi, key rotation, workspace üyelik çıkarma.
API ve OS
 RLS testleri, IDOR, yetki yükseltme, path traversal, symlink escape, dosya uzantısı ve MIME aldatması.
 Webhook tekrar gönderimi, sahte RevenueCat olayları, expired JWT, revoked device, signed URL sızıntısı.
 Dosya önizleme sandbox, zip bomb, büyük dosya, bozuk manifest ve disk doluluğu.
Gizlilik
 Minimum telemetri; pano/dosya içeriği loglanmaz.
 Kullanıcıya metadata görünürlüğü ve offline wipe sınırları açık anlatılır.
 Kullanıcı hesabı silme, veri dışa aktarma, saklama ve silme politikası.
27. Operasyon, gözlemlenebilirlik ve destek
[TASARIM] Kullanıcı verisinin gizliliğini bozmayan operasyon altyapısı.
Dağıtım
 Docker servisleri için healthcheck, log rotasyonu, secrets manager, otomatik restart ve yedekleme.
 Supabase şema migration ve RLS testleri CI’da koşar.
 Nesne depolama lifecycle kuralları ve orphan cleanup için günlük doğrulama.
Destek
 İçerik okumadan transfer_id, hata kodu, bağlantı yolu, anonim performans metrikleriyle sorun giderme.
 Kullanıcı onaylı tanılama paketi; gizli anahtar veya düz metin pano asla dahil edilmez.
 Olay müdahale planı: servis kesintisi, hesap ele geçirme, anahtar sızıntısı, faturalandırma hatası.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
28. Antigravity klasör yapısı ve kod standartları
[TASARIM] Modüller küçük başlayıp ihtiyaçla ayrılmalı; gereksiz Gradle modülü patlaması önlenmelidir.
Örnek yapı
 shared/core/{auth,devices,networking,encryption,database,permissions}
 shared/transfer/{protocol,chunks,resume,queue}
 shared/sync/{engine,rules,conflicts,watchers}
 shared/remote-files/{models,operations,previews,permissions}
 shared/offline/{repository,download,manifest,scheduler,quota}
 shared/workspaces/{members,sharing,files,keys}
 androidApp/, iosApp/, windowsApp/, windowsAgent/, backend/, infra/, docs/, tests/
Kod standartları
 CommonMain içinde platform API kullanma; expect/actual veya arayüz adaptörleri.
 UI state tek yönlü, deterministik; background task lifecycle açık.
 Şifreleme arayüzü test vektörleriyle platformlar arası eşdeğer doğrulanır.
 Her özellik için migration, domain model, API contract, UI, test ve hata durumları tamamlanır.
29. Aşamalı geliştirme planı
[ONAYLI yön] Tek seferde devasa uygulama istemek yerine faz faz ilerleme. Aşağıdaki sıralama teknik öneridir.
Faz Kapsam Çıkış kriteri
0 Monorepo, KMP iskeleti, CI, UI kit,
env ve tehdit modeli
Android/iOS/Windows boş
kabuklar açılır; test pipeline yeşil.
1 Supabase Auth, cihaz anahtarları,
secure storage
Hesap + anahtar yaşam döngüsü
testleri.
2 Pairing durum makinesi, QR/SAS,
cihaz izinleri
Onay/ret/iptal/race ve yetkisiz
erişim testleri.
3 Kripto manifest, AEAD chunk,
doğrulama, recovery prototipi
Platformlar arası şifrele/çöz test
vektörleri.
4 LAN discovery ve E2EE transfer
motoru
Büyük dosya, resume, bütünlük ve
iptal.
5 P2P signaling, NAT traversal, relay
fallback
Gerçek ağ senaryoları, quota
ölçümü.
6 Geçici şifreli bulut, TTL ve teslim
onayı
Offline teslim, cleanup, multirecipient.
7 Kişisel kasa, 2/20 GB kota, anahtar
yedekleme
Kurtarma ve kalıcı dosya
sürümleri.
8 Windows Agent, folder watcher ve
sync rules
Tek/çift yönlü ve delete-off testleri.
9 Uzak dosya yöneticisi ve yetkiler Path traversal, read/write, çöp
kutusu.
10 Pano, çevrimdışı pin, mobil OS
entegrasyonları
Android/iOS kısıtları ve manuel
fallback.
11 Ortak alan, rol/görünürlük, üye Anahtar rotasyonu ve erişim
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
çıkarma testleri.
12 RevenueCat, Free/Pro kota
enforcement, paywall
Restore/refund/expiration/
webhook testleri.
13 Güvenlik incelemesi, performans,
beta, mağaza yayını
Release gate ve geri dönüş planı.
30. Test senaryoları ve kabul kriterleri
[TASARIM] Her senaryo otomatik veya manuel test case olarak Antigravity backloguna aktarılmalıdır.
Cihaz ve güvenlik
 Yeni cihaz PENDING iken hiçbir dosya, pano, kasa anahtarı veya remote root alamaz.
 Approve ve reject aynı anda gelirse tek sonuç oluşur; iptal edilmiş oturum yeniden kullanılamaz.
 Revoked cihaz bağlantı kursa bile API, relay ve signed URL alamaz; offline wipe garanti edilmez.
 Kurtarma anahtarı kayıpsa ve güvenilir cihaz yoksa eski E2EE içerik açılamaz.
Dosya ve sync
 10 GB dosya aktarımında ağ kopar, kaldığı yerden devam eder, hash eşleşir.
 Aynı dosya iki cihazda değişir; sessiz veri kaybı yoktur.
 Delete propagation OFF iken bir cihazdaki silme diğerini silmez.
 Offline Windows kapalıyken remote-only dosya erişilemez; pinli dosya erişilir.
Abonelik
 Free 3 cihaz, Pro 10 cihaz sınırı server-side uygulanır.
 Free 2 GB / Pro 20 GB depolama ve Free 2 GB / Pro 50 GB relay ölçümü aşılmaz.
 LAN/P2P trafik relay kotasına yazılmaz.
 RevenueCat aboneliği iptal/refund/restore olunca bütün cihazlarda doğru plan görülür.
 Pro bitince 30 günlük fazla bulut veri indirme süresi korunur.
Mobil
 iOS arka planda pano okuma vaadi yok; uygulama içi gelen öğe manuel kopyalanabilir.
 Android izin reddi sonrası anlaşılır geri dönüş; pil tasarrufunda kuyruk tutarlılığı.
31. Riskler, belirsizlikler ve açık kararlar
Onaylanmış iş hedefleri ile teknik uygulama garantileri birbirine karıştırılmamalıdır.
Maliyet riski
 Global $2.99 / €2.69 aylık fiyatlar ile 20 GB depolama + 50 GB relay kârlılığı gerçek kullanımda
doğrulanmalıdır.
 Geçici bulut staging’in ayrı saklama/transfer kotası ve ücretlendirme ölçüsü henüz sayısal olarak
kesinleşmedi.
 Egress, storage operations, VAT, mağaza komisyonu, RevenueCat ve ücretsiz kullanıcı sübvansiyonu
hesaplanmalıdır.
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
Ürün kararları
 Ortak çalışma alanındaki 5 üye sahibini içeriyor mu?
 Pro çevrimdışı sabitleme kotası önerilen 5 GB mı; kullanıcı değiştirebilir mi?
 Pano görsel boyut sınırı önerilen 10 MB mı?
 Çöp kutusu saklama ve kotadan düşme politikası nedir?
 Yıllık abonelik relay dönemi aylık dilimler halinde mi yenilenecek?
Teknik risk
 iOS ve Android arka plan kısıtları; Windows Agent imzalama/güncelleme; NAT geçiş başarı oranı.
 E2EE arama ve dosya adı şifreleme UX maliyeti.
 Anahtar kurtarma, revocation ve workspace üye çıkarma güvenlik incelemesi.
32. Yayın, ölçüm ve yol haritası
[TASARIM] Önce güvenli temel transfer, sonra bulut/uzak yönetim ve Pro büyümesi.
Alfa
 Windows Android LAN, pairing, E2EE chunk transfer, devam ettirme. ↔
 Temel cihaz listesi ve hata raporlama.
Kapalı beta
 iOS temel akışları, P2P/relay, kasa, Windows remote manager, sync ve RevenueCat sandbox.
 Maliyet ölçümü, OS uyumluluğu, gerçek kullanıcı geri bildirimi.
Genel yayın
 Gizlilik metni, abonelik koşulları, geri yükleme, hesap silme, destek ve incident playbook.
 Sürüm kontrollü protokol; eski istemcilerle güvenli uyumluluk.
33. Kararların izlenebilirlik özeti
Konu Karar / durum
Platform Android+iOS+Windows ilk hedef; macOS sonra
UI/Kod KMP + Compose Multiplatform; Windows Agent ayrı
İçerik güvenliği Bütün dosya, pano, geçici bulut, kasa E2EE
Eşleştirme Yeni cihaz PENDING, güvenilir cihaz onayı gerekli
Kurtarma Güvenilir cihaz + kullanıcı kurtarma anahtarı
Revocation Erişim kesme + anahtar döndürme + best-effort wipe
Transfer LAN > P2P > relay; resume, chunk, hash
Sync Klasör ve yeni indirme ayrı; silme yayılımı OFF
Pano Metin, URL, görsel; OS kısıtları
Remote Windows Agent, açık paylaşım, yetkili dosya yönetimi
Offline Seçili dosya/klasör pinleme
Vault Kişisel + Pro ortak alan
SyncSwift | Ürün ve Teknik Şartname | 22.10.2026
Planlar Free + Pro; RevenueCat
Fiyat TRY 149,99 / ay; 999,99 / yıl
Fiyat USD $2.99 / ay; $19.99 / yıl
Fiyat EUR €2.69 / ay; €17.99 / yıl
Bulut Free 2 GB; Pro 20 GB
Relay Free 2 GB/ay; Pro 50 GB/ay
Cihaz Free 3; Pro 10
Diğer Pro Sınırsız sync klasörü, full remote, görsel pano, offline
klasör, 1 ortak alan/5 üye, 30 gün sürüm
34. Antigravity için ana uygulama promptu
Aşağıdaki metin, Antigravity’ye ana görev olarak yapıştırılabilir. Her fazı ayrı commit ve test çıktısıyla
tamamlatın.
ROL: Kıdemli Kotlin Multiplatform, Windows filesystem, dağıtık sistemler ve uygulamalı kriptografi mühendisi
olarak çalış. SyncSwift ürününü bu şartnamedeki ONAYLI gereksinimlere sadık kalarak fazlar halinde geliştir.
KURAL 1: Hiçbir onaylı özelliği kaldırma veya ücretsiz/Pro sınırlarını değiştirme. Belirsiz konuları varsayılanla
gizleme; KARAR GEREKİYOR olarak raporla.
KURAL 2: E2EE için kendi kripto algoritmanı yazma. Denetlenmiş kütüphane, test vektörü, nonce güvenliği,
manifest bütünlüğü, yetki doğrulaması ve kurtarma testleri zorunlu.
KURAL 3: Her fazda mimari karar kaydı (ADR), migration, API contract, unit/integration testleri, kullanıcı akışı,
hata durumları ve çalıştırma komutları üret.
KURAL 4: iOS/Android platform sınırlarını aşabildiğini iddia etme. OS-specific adapters kullan; izin reddi ve
background failure fallback geliştir.
KURAL 5: Yeni cihaz güvenilir cihaz onayı olmadan içerik veya anahtar alamaz. REVOKED cihazlar hiçbir hassas
endpointten yeni erişim alamaz.
KURAL 6: Free 2 GB bulut/2 GB aylık relay/3 cihaz; Pro 20 GB bulut/50 GB aylık relay/10 cihaz. LAN ve doğrudan
P2P aktarımı relay kotası tüketmez.
KURAL 7: RevenueCat entitlement pro, offering default, pro_monthly/pro_yearly. TRY 149,99/999,99; USD
2.99/19.99; EUR 2.69/17.99. Sunucu doğrulaması ve Windows hesap çapında erişim.
KURAL 8: Windows remote manager yalnızca kullanıcının paylaştığı kök klasörlerde; canonical path, symlink
escape, ACL ve idempotent commands.
KURAL 9: Her faz bitiminde tamamlananlar, açık riskler, test sonuçları, sonraki faz ve kullanıcı onayı gerektiren
kararlar raporu ver. Sonraki faza otomatik geçme.
BAŞLANGIÇ: Önce mevcut repo yapısını incele, eksiklerini raporla, faz 0 için uygulanabilir dosya ağacı ve küçük
commit planı öner. Kullanıcı onayından sonra yalnızca faz 0’ı uygula.
Son not
Bu doküman bir geliştirme şartnamesidir; hukuki, mağaza politikası veya kriptografik güvenlik denetiminin
yerini tutmaz. Uygulama yayına çıkmadan önce güvenlik gözden geçirmesi, mobil mağaza kuralları ve gerçek
altyapı maliyet testleri yapılmalıdır.