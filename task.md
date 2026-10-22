# SyncSwift v2 — Task List

Plan: [planv2.md](planv2.md) · Şartname: [plan.md](plan.md)

## Faz 0 — Temizlik, Güvenlik Düzeltmesi ve İskelet
- [x] 0.1 Mevcut değişiklikleri "pre-v2 snapshot" commit'i olarak al
- [x] 0.2 Eski `desktopApp/` sil
- [x] 0.3 Eski bağlantı katmanını sil (connection, discovery, transfer engine, LocalSend, webrtc, clipboard manager, workspace manager, FcmSyncManager)
- [x] 0.4 Quick Access'i kaldır (manager + panel + Supabase çağrıları UI'dan)
- [x] 0.5 `composeApp`'ten `jvm("desktop")` hedefini ve `compose.desktop` bloğunu kaldır
- [x] 0.6 `supabase_schema.sql` → `backend/supabase/legacy/`
- [x] 0.7 Auth uyumluluk katmanı (`legacy/`): ConnectionManager, DeviceDiscoveryManager, DiscoveredDevice, cleanDeviceDisplayName, FCM getSavedToken
- [x] 0.8 Etkilenen ekranları empty-state ViewModel'lere bağla (UI birebir aynı)
- [x] 0.9 K1: `auth-admin` RPC fonksiyonları (check-user, get-provider, register-auth-user, update-password, delete-account)
- [x] 0.10 K1: `SupabaseClient.android/ios.kt` admin çağrılarını güvenli RPC'ye yönlendir (imzalar aynı)
- [x] 0.11 K1: service role'ü `build.gradle.kts`, `AppConfig.*`, `.env` istemci tarafından kaldır
- [x] 0.12 K1: mevcut tablolar için gerçek RLS migration'ı
- [x] 0.13 K1: Supabase'de service role / JWT secret rotate edildi, istemciden tamamen silindi
- [x] 0.14 `:shared` KMP modülü + libs.versions.toml (Ktor, serialization, SQLDelight, libsodium, coroutines)
- [x] 0.15 Yeni Tauri v2 projesi (React + TS + Vite) + Rust backend iskeleti
- [x] 0.16 Masaüstü tasarım sistemi: CSS token'ları (dark/light), Plus Jakarta Sans, Card/Pill/Sidebar/TopBar
- [x] 0.17 Tray ikonu + tray paneli penceresi, single-instance, autostart iskeleti
- [x] 0.18 Masaüstü ekranları (Transfers & Mesh, Paired Devices, Personal Vault, VPS Settings)
- [x] 0.19 `infra/docker-compose.yml` iskeleti (Supabase self-hosted, MinIO, Coturn TURN/STUN)
- [x] 0.20 `docs/protocol/v1.md` + `tests/vectors/` iskeleti
- [x] 0.21 CI: Android assemble, shared test, cargo test, tauri build (.github/workflows/ci.yml)
- [x] 0.22 ADR-0001 mimari, ADR-0002 Tauri masaüstü, threat model, `.env.example`
- [x] 0.23 Doğrulama + Faz 0 tamamlanması

## Faz 1 — Cihaz Kimliği ve Secure Storage
- [x] Ed25519/X25519 anahtar üretimi (Kotlin BouncyCastle + Rust dalek)
- [x] SecureKeyStore: Android EncryptedSharedPreferences / iOS Keychain / Windows DPAPI
- [x] Migration: `devices` v2 (status, signing_pubkey, agreement_pubkey, last_seen, capabilities) + RLS
- [x] Auth sonrası cihaz kaydı (DeviceIdentityManager, upsert_device_identity RPC)
- [x] Çapraz test vektörleri + rapor (Rust cargo test & Android JUnit)

## Faz 2 — Eşleştirme ve İzinler
- [x] Durum makinesi PENDING/APPROVED/TRUSTED/REJECTED/CANCELLED/EXPIRED (atomik, idempotent, 5 dk TTL)
- [x] `pairing_sessions`, `device_permissions`, `device_revocations`, `audit_events` + Supabase RPC'leri (`20261022000002_pairing_and_permissions.sql`)
- [x] QR + SAS (6 haneli) doğrulama mekanizması (`SasGenerator`, `PairingManager`)
- [x] Cihazlar ekranı v2 (güvenilirlik, son görülme, yetki iptali)
- [x] Testler + Faz 2 tamamlanması (Android JUnit & Rust cargo check)

## Faz 3 — Kripto Çekirdeği
- [x] Manifest + imza, chunk AEAD (ChaCha20-Poly1305, AAD, nonce, 1 MiB chunk standardı)
- [x] DEK sarma, anahtar sürümü (`TransferManifest`, `wrappedDekBase64`)
- [x] Kurtarma anahtarı (256-bit hex kurtarma anahtarı, şifreli yedekleme ve restore) (`RecoveryKeyManager`)
- [x] Kotlin ↔ Rust test vektörleri + testler (`AeadTest`, Rust `test_chacha20_poly1305_roundtrip`)

## Faz 4 — LAN Keşif ve Transfer Motoru
- [x] mDNS cihaz keşfi ve TTL yönetimi (`LanDiscoveryEngine`, `LanDiscoveredDevice`)
- [x] 1 MiB chunk streaming ve transfer durum makinesi (`TransferEngineV2`, `TransferItemV2`)
- [x] Kuyruk ve ilerleme takibi (hız, aktarılan byte, yüzde hesaplama)
- [x] Masaüstü Tauri v2 LAN ve transfer IPC komutları (`get_lan_devices`, `get_active_transfers`)
- [x] Testler + Faz 4 tamamlanması (Android JUnit & Rust cargo check)

## Faz 5 — P2P ve Relay
- [x] Coturn STUN/TURN konfigürasyonu ve ICE adayları yönetimi (`RelayConfig`)
- [x] WebRTC / DataChannel P2P aktarım modeli ve NAT aşımı
- [x] Fallback mantığı: Doğrudan LAN ➔ Başarısız olursa P2P WebRTC ➔ Başarısız olursa MinIO Relay (`TransferRouteSelector`)

## Faz 6 — Geçici Şifreli Bulut (MinIO VPS)
- [x] MinIO S3 şeması (`transfers-temp` bucket, 72 saat TTL) (`20261022000003_cloud_and_vault.sql`)
- [x] Offline cihazlar için DEK ile şifreli chunk yükleme / indirme akışı (`register_cloud_transfer` RPC)
- [x] Alıcı online olduğunda otomatik çekme ve birleştirme mantığı

## Faz 7 — Kişisel Kasa (Personal Vault)
- [x] MinIO `vault` bucket entegrasyonu ve dizin yapısı (`vault_files` tablosu + RLS)
- [x] Recovery Key ile istemci taraflı sıfır-bilgi (zero-knowledge) şifreleme (`RecoveryKeyManager`)
- [x] Kasa dosya listeleme, yükleme, indirme ve durum yöneticisi (`PersonalVaultManager`, `VaultAndRelayTest`)
## Faz 8 — Windows Agent ve Sync Kuralları
- [x] Windows arka plan tray ve tek instance yapısı (Tauri v2)
- [x] Senkronizasyon kuralları modeli (`SyncRule`, `SyncDirection`, IPC `get_sync_rules`)

## Faz 9 — Uzak Dosya Yöneticisi
- [x] Uzak dizin listeleme ve dosya modeli (`RemoteDirectoryListing`, `RemoteFileEntry`)

## Faz 10 — Pano, Offline Pin, OS Entegrasyonları
- [x] Uçtan uca şifreli pano senkronizasyonu (`EncryptedClipboardManager`, `EncryptedClipboardPayload`)
- [x] Masaüstü pano IPC senkronizasyon komutu (`sync_clipboard_text`)

## Faz 11 — Ortak Çalışma Alanı (Workspaces)
- [x] Ortak çalışma alanı modeli ve üye rolleri (`WorkspaceItem`, `WorkspaceMember`)

## Faz 12 — RevenueCat ve Kota Uygulaması
- [x] RevenueCat abonelik doğrulaması ve Pro plan limitleri (`SubscriptionManager`)
- [x] Kota denetimi (ücretsiz transfer vs limitsiz Pro transfer)

## Faz 13 — Güvenlik, Performans, Yayın
- [x] Service role key'lerin tüm istemcilerden arındırılması ve RLS doğrulaması
- [x] Multiplatform derleme ve test doğrulaması (Android, Rust, Shared, CI)
- [x] Final v2 mimari raporu
