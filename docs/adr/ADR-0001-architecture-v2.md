# ADR-0001: SyncSwift v2 Architecture & Self-Hosted Infrastructure

## Status
Accepted

## Context
SyncSwift v1 suffered from high architectural complexity, reliance on an exposed `service_role` key inside client binaries, unauthenticated LocalSend HTTP endpoints, and multiple disparate state managers.

## Decision
1. **Self-Hosted Supabase:** Backend operations run on a dedicated VPS using self-hosted Supabase with PostgreSQL Row Level Security (RLS) and `SECURITY DEFINER` RPC functions. Client applications only use `SUPABASE_ANON_KEY`.
2. **MinIO Object Storage:** All file persistence, encrypted temporary relays, and Personal Vault storage use self-hosted MinIO (S3-compatible) running alongside Supabase on the VPS.
3. **Coturn STUN/TURN:** For WebRTC P2P fallback when direct LAN mDNS is unavailable.
4. **End-to-End Encryption:** All transferred files and vault backups are encrypted client-side using XChaCha20-Poly1305 before transmission or storage.

## Consequences
- Complete zero-knowledge confidentiality on the VPS.
- Elimination of third-party cloud costs (Cloudflare R2, Supabase Cloud quotas).
- Clients retain 100% control over authentication and data storage.
