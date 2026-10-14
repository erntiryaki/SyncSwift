# SyncDrop mobile app

Kotlin Multiplatform + Compose Multiplatform başlangıç projesi.

## Mevcut akış

`Onboarding → E-posta → (kayıtlı hesap: şifre | yeni hesap: OTP) → profil tamamlama → ana ekran`

- Android: Google ile giriş.
- iOS: Google ve Apple ile giriş.
- Hesap sağlayıcısı `IdentityProvider` ile kilitlenir; çapraz sağlayıcıyla oturum açmaya izin verilmez.
- İngilizce varsayılandır; Türkçe, Almanca, Fransızca, İspanyolca ve Arapça arayüz metinleri eklenmiştir.
- `BrevoOtpGateway` aktif geliştirme adaptörü, `AmazonSesOtpGateway` ise sonraki sağlayıcı geçişi için tanımlıdır. İkisi de canlı kullanım öncesi kendi sunucu tarafı endpoint'lerine ve güvenli saklanan kimlik bilgilerine bağlanmalıdır; API anahtarları istemciye konmaz.

## Yerel derleme

JDK 17 ile Android Studio'dan `composeApp` modülünü açıp `assembleDebug` çalıştırın. Bu makinede Android Studio'nun gömülü JDK'sı 25 olduğundan Gradle 8.14/Kotlin DSL ile uyumlu değildir; `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` seçilmelidir.
