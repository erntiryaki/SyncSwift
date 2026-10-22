# SyncSwift v2 — Task List

Plan: [planv2.md](planv2.md) · Şartname: [plan.md](plan.md)

## Faz 0 — Temizlik, Güvenlik Düzeltmesi ve İskelet
- [ ] 0.1 Mevcut değişiklikleri "pre-v2 snapshot" commit'i olarak al
- [ ] 0.2 Eski `desktopApp/` sil
- [ ] 0.3 Eski bağlantı katmanını sil (connection, discovery, transfer engine, LocalSend, webrtc, clipboard manager, workspace manager, FcmSyncManager)
- [ ] 0.4 Quick Access'i kaldır (manager + panel + Supabase çağrıları UI'dan)
- [ ] 0.5 `composeApp`'ten `jvm("desktop")` hedefini ve `compose.desktop` bloğunu kaldır
- [ ] 0.6 `supabase_schema.sql` → `backend/supabase/legacy/`
- [ ] 0.7 Auth uyumluluk katmanı (`legacy/`): ConnectionManager, DeviceDiscoveryManager, DiscoveredDevice, cleanDeviceDisplayName, FCM getSavedToken
- [ ] 0.8 Etkilenen ekranları empty-state ViewModel'lere bağla (UI birebir aynı)
- [ ] 0.9 K1: `auth-admin` Edge Function'ları (check-user, get-provider, sign-in check, update-password, update-profile, pro-status, delete-account, device ops)
- [ ] 0.10 K1: `SupabaseClient.android/ios.kt` admin çağrılarını Edge Function'a yönlendir (imzalar aynı)
- [ ] 0.11 K1: service role'ü `build.gradle.kts`, `AppConfig.*`, `.env` istemci tarafından kaldır
- [ ] 0.12 K1: mevcut tablolar için gerçek RLS migration'ı
- [ ] 0.13 K1: **[Kullanıcı]** Supabase'de service role / JWT secret rotate
- [ ] 0.14 `:shared` KMP modülü + libs.versions.toml (Ktor, serialization, SQLDelight, libsodium, coroutines)
- [ ] 0.15 Yeni Tauri v2 projesi (React + TS + Vite) + Rust workspace (`syncswift-core`, `agent`)
- [ ] 0.16 Masaüstü tasarım sistemi: CSS token'ları (dark/light), Plus Jakarta Sans, PillField/MainPill/Sheet/Card/Sidebar/TopBar
- [ ] 0.17 Tray ikonu + tray paneli penceresi, single-instance, autostart iskeleti
- [ ] 0.18 Masaüstü giriş ekranı (Supabase Auth: e-posta OTP + Google/Apple için tarayıcı ve deep link)
- [ ] 0.19 `infra/docker-compose.yml` iskeleti (signaling, coturn, minio, caddy)
- [ ] 0.20 `docs/protocol/v1.md` + `tests/vectors/` iskeleti
- [ ] 0.21 CI: Android assemble, shared test, cargo test, tauri build (Windows), migration lint
- [ ] 0.22 ADR-0001 mimari, ADR-0002 Tauri masaüstü, threat model, `.env.example`
- [ ] 0.23 Doğrulama + Faz 0 raporu

## Faz 1 — Cihaz Kimliği ve Secure Storage
- [ ] Ed25519/X25519 anahtar üretimi (Kotlin libsodium + Rust dalek)
- [ ] SecureKeyStore: Android Keystore / iOS Keychain / Windows DPAPI
- [ ] Migration: `devices` v2 (status, signing_pubkey, agreement_pubkey, last_seen, capabilities) + RLS
- [ ] Auth sonrası cihaz kaydı (SessionEventBus dinlenerek)
- [ ] Çapraz test vektörleri + rapor

## Faz 2 — Eşleştirme ve İzinler
- [ ] Durum makinesi PENDING/APPROVED/TRUSTED/REJECTED/CANCELLED/EXPIRED/REVOKED (atomik, idempotent, 5 dk TTL)
- [ ] `pairing_sessions`, `device_permissions`, `device_revocations`, `audit_events` + Edge Function'lar
- [ ] QR + SAS doğrulama; onay ekranı (mobil bottom sheet / masaüstü sheet)
- [ ] Cihazlar ekranı v2 (güvenilirlik, son görülme, izinler), revoke
- [ ] Race/yetkisiz erişim testleri + rapor

## Faz 3 — Kripto Çekirdeği
- [ ] Manifest + imza, chunk AEAD (XChaCha20-Poly1305, AAD, nonce)
- [ ] DEK sarma, anahtar sürümü
- [ ] Kurtarma anahtarı (üretim, şifreli yedek, restore) + UI
- [ ] Kotlin ↔ Rust test vektörleri + rapor

## Faz 4 — LAN Keşif ve Transfer Motoru
- [ ] mDNS (NSD / Bonjour / mdns-sd) + kimlik doğrulama
- [ ] Doğrulanmış el sıkışma + 1 MiB chunk stream
- [ ] Kuyruk/checkpoint (SQLDelight / SQLite), resume, atomik finalize
- [ ] Transfer durumları + Home/Transfers/FileDeviceSelect + masaüstü/tray UI bağlama
- [ ] Android ↔ Windows büyük dosya/resume/iptal testleri + rapor

## Faz 5 — P2P ve Relay
## Faz 6 — Geçici Şifreli Bulut
## Faz 7 — Kişisel Kasa
## Faz 8 — Windows Agent ve Sync Kuralları
## Faz 9 — Uzak Dosya Yöneticisi
## Faz 10 — Pano, Offline Pin, OS Entegrasyonları
## Faz 11 — Ortak Çalışma Alanı
## Faz 12 — RevenueCat ve Kota Uygulaması
## Faz 13 — Güvenlik, Performans, Yayın
