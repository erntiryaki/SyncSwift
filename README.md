# SyncSwift v2

<p align="center">
  <b>Zero-Knowledge, High-Speed Cross-Platform File Sharing & Real-Time Universal Mesh</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Windows%20%7C%20macOS-38BDF8?style=flat-square" alt="Platforms" />
  <img src="https://img.shields.io/badge/Mobile-Compose%20Multiplatform%202.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Compose Multiplatform" />
  <img src="https://img.shields.io/badge/Desktop-Tauri%20v2%20(Rust%20%2B%20React)-F59E0B?style=flat-square&logo=rust&logoColor=white" alt="Tauri v2" />
  <img src="https://img.shields.io/badge/Crypto-Ed25519%20%7C%20X25519%20%7C%20ChaCha20--Poly1305-10B981?style=flat-square" alt="Crypto" />
  <img src="https://img.shields.io/badge/Backend-Self--Hosted%20Supabase%20%2B%20MinIO%20%2B%20Coturn-6366F1?style=flat-square&logo=docker&logoColor=white" alt="Backend" />
  <img src="https://img.shields.io/badge/License-Proprietary-slate?style=flat-square" alt="License" />
</p>

---

## ⚡ Overview

**SyncSwift v2** is a secure, decentralized multi-device file transfer and synchronization suite. Engineered from the ground up to replace legacy unauthenticated protocols with a state-of-the-art **Zero-Knowledge** cryptographic architecture:

- **Mobile (Android & iOS):** Built with **Jetpack Compose Multiplatform** and a dedicated `:shared` KMP engine.
- **Desktop (Windows & macOS):** Built on **Tauri v2**, combining a high-performance **Rust** background agent and system tray engine with a sleek **React 18 / TypeScript** frontend.
- **Backend (VPS):** 100% self-hosted dockerized infrastructure featuring **Supabase** (Postgres + RLS + GoTrue Auth), **MinIO** S3 object storage, and **Coturn** WebRTC STUN/TURN signaling.

---

## ✨ Key Capabilities

### 1. 🔀 3-Tier Route Fallback Engine
SyncSwift dynamically assesses network topology and routes file transfers through the fastest possible channel:
1. **Tier 1 — Direct LAN Mesh:** Zero-configuration mDNS discovery over raw TLS / ChaCha20 streams. Transfers reach full local Wi-Fi speeds without ever leaving your router.
2. **Tier 2 — WebRTC P2P DataChannels:** Direct peer-to-peer tunnels across disparate networks using self-hosted Coturn STUN/TURN NAT traversal.
3. **Tier 3 — Encrypted Asynchronous Relay (MinIO):** If the receiving device is currently offline, payloads are chunked, client-encrypted with a random Data Encryption Key (DEK), and staged in temporary S3 storage (72-hour TTL). The receiver automatically downloads and decrypts upon reconnecting.

### 2. 🔐 Zero-Knowledge Cryptography
- **Device Identity:** Every device generates an **Ed25519** keypair for signatures and an **X25519** keypair for Diffie-Hellman key agreement.
- **Chunk AEAD Framing:** Files are streamed in 1 MiB chunks authenticated with **ChaCha20-Poly1305** using authenticated additional data (AAD headers).
- **Zero Client Secrets:** The dangerous `SUPABASE_SERVICE_ROLE_KEY` and cloud provider secrets have been strictly eradicated from client binaries. All database mutations rely on Postgres **Row-Level Security (RLS)** and atomic `SECURITY DEFINER` RPCs.
- **Personal Vault & Recovery:** 256-bit recovery keys allow full device restoration and client-side zero-knowledge encrypted vault storage.

### 3. 📋 Real-Time Universal Clipboard
Copy text, URLs, or notes on your phone and paste them on your desktop within milliseconds, fully encrypted in transit.

### 4. 🖥️ Native Desktop Agent & System Tray
- Runs silently in the system tray with single-instance lock and autostart capabilities.
- Mini-tray quick actions panel for instant drag-and-drop transfers and paired device status.
- Background folder sync rules (one-way, bi-directional, delete protection).

### 5. 🌍 Multi-Language Localization
Native localization support for **9 languages**: English, Türkçe, Deutsch, Français, Español, Italiano, Português, Русский, and العربية (including RTL layout support).

---

## 🏗️ Architecture & Technology Stack

| Layer | Technologies |
|---|---|
| **Mobile Client** | Kotlin Multiplatform 2.1, Compose Multiplatform 1.7, Kotlin Coroutines, Kotlinx Serialization |
| **Shared Core (`:shared`)** | KMP Core: Ed25519/X25519 crypto, ChaCha20-Poly1305 AEAD, mDNS discovery, transfer queue |
| **Desktop Client (`desktopApp`)** | Tauri v2, Rust Cargo Workspace (`syncswift-core`, `agent`), React 18, TypeScript, Vite |
| **Database & Auth (VPS)** | Self-Hosted Supabase: PostgreSQL 15, GoTrue Auth (Email OTP + OAuth), Kong Gateway, Realtime WS |
| **Object Storage (VPS)** | Self-Hosted MinIO: S3 API compatibility (`transfers-temp` and `vault` buckets) |
| **NAT Traversal (VPS)** | Self-Hosted Coturn: WebRTC STUN/TURN server |
| **Monetization** | RevenueCat SDK (Google Play Billing & Apple StoreKit) + Google AdMob |

---

## 📂 Repository Layout

```
SyncSwift/
├── composeApp/                 # Android & iOS UI (Compose Multiplatform)
│   └── src/                    # Screens, themes, shims, native platform bindings
├── shared/                     # Cross-platform core engine (Kotlin Multiplatform)
│   └── src/commonMain/kotlin/net/syncswift/
│       ├── crypto/             # Ed25519, X25519, ChaCha20-Poly1305, RecoveryKeyManager
│       ├── discovery/          # LanDiscoveryEngine & mDNS peer resolution
│       ├── pairing/            # Pairing state machine, SAS code generation
│       ├── transfer/           # 1 MiB chunk streaming engine & queue manager
│       ├── relay/              # 3-tier route selector (LAN -> WebRTC -> MinIO)
│       ├── storage/            # Personal Vault zero-knowledge manager
│       └── clipboard/          # Encrypted clipboard synchronization
├── desktopApp/                 # Tauri v2 Desktop Application
│   ├── src/                    # React 18 + TypeScript UI (Cards, Tray, Modals)
│   └── src-tauri/              # Rust workspace
│       ├── crates/syncswift-core/  # Rust crypto primitives (dalek, chacha20poly1305)
│       ├── crates/agent/           # Background watcher & sync rules engine
│       └── src/                    # Tauri commands, tray menu, window management
├── backend/
│   └── supabase/migrations/    # Hardened Postgres RLS policies & SECURITY DEFINER RPCs
├── infra/                      # VPS Docker infrastructure (Supabase, MinIO, Coturn)
│   ├── docker-compose.yml
│   ├── kong.yml
│   └── .env.example
├── docs/                       # Architecture Decision Records (ADR), wire protocol spec
├── tests/vectors/              # Cross-platform crypto validation test vectors
├── .env.local.example          # Client environment template
└── README.md
```

---

## 🚀 Getting Started

### 1. Prerequisites
- **JDK 17+** & **Android Studio** Ladybug / Meerkat
- **Node.js 20+** & **npm**
- **Rust 1.78+** (with `cargo`)
- **Docker & Docker Compose** (for VPS backend deployment)

### 2. Client Setup (`.env.local`)
Copy the template and configure your client keys:
```bash
cp .env.local.example .env.local
```
Fill in your public client settings:
```ini
SUPABASE_URL=https://api.syncswift.net
SUPABASE_ANON_KEY=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
GOOGLE_WEB_CLIENT_ID=your-google-web-client-id.apps.googleusercontent.com
REVENUECAT_API_KEY=goog_...
REVENUECAT_IOS_API_KEY=appl_...
```

### 3. Running the Mobile App (Android)
```bash
# Run unit tests across shared core
.\gradlew.bat :shared:testDebugUnitTest

# Assemble Android debug build
.\gradlew.bat :composeApp:assembleDebug
```

### 4. Running the Desktop App (Tauri v2)
```bash
cd desktopApp

# Install UI dependencies
npm install

# Run frontend tests & build
npm run build

# Run Rust crypto & agent test suite
cargo test --workspace --manifest-path src-tauri/Cargo.toml

# Launch desktop app with hot reload
npm run tauri dev
```

### 5. Running the VPS Infrastructure
On your server:
```bash
cd infra
cp .env.example .env
# Edit .env with your generated JWT_SECRET, passwords, and domain
docker compose up -d
```

---

## 🛡️ Security Architecture & Practices

1. **No Embedded Service Keys:** Client applications never receive administrative credentials. All database interactions occur via authenticated JWT tokens and `SECURITY DEFINER` RPC functions with strict caller identity verification.
2. **Zero-Knowledge Cloud Relays:** Payloads stored in MinIO are encrypted client-side using ephemeral keys before upload. The VPS backend operates as a blind, zero-knowledge store.
3. **Audit Logging:** Every pairing approval, permission grant, and revocation is recorded in an immutable audit ledger (`audit_events`).

---

## 🌐 Official Channels & Support

- **Website:** [syncswift.net](https://syncswift.net)
- **API Endpoint:** [api.syncswift.net](https://api.syncswift.net)
- **Support:** [support@syncswift.net](mailto:support@syncswift.net)

---

<p align="center">
  <sub>© 2026 SyncSwift Team. Built with Kotlin Multiplatform, Tauri v2, and Rust.</sub>
</p>
