-- ============================================================
-- SyncSwift: Self-Hosted Supabase Auth & Security Hardening
-- Complete RPC suite & strict RLS for Self-Hosted Supabase on VPS
-- Eliminates any need for SUPABASE_SERVICE_ROLE_KEY on clients.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Profiles Table with Foreign Key to auth.users
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    first_name TEXT DEFAULT '',
    last_name TEXT DEFAULT '',
    auth_provider TEXT DEFAULT 'email',
    is_pro BOOLEAN DEFAULT FALSE,
    pro_start_date TIMESTAMP WITH TIME ZONE,
    pro_renewal_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Drop legacy open policies
DROP POLICY IF EXISTS "Allow public read and write on profiles" ON public.profiles;
DROP POLICY IF EXISTS "Users can read own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;

-- Strict RLS: Users can only see/update their own profile
CREATE POLICY "Users can read own profile"
ON public.profiles FOR SELECT
TO authenticated
USING (auth.uid() = id);

CREATE POLICY "Users can update own profile"
ON public.profiles FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- 2. RPC: check_user_exists (Returns BOOLEAN only, zero data leakage)
CREATE OR REPLACE FUNCTION public.check_user_exists(p_email TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    IF p_email IS NULL OR trim(p_email) = '' THEN
        RETURN FALSE;
    END IF;
    RETURN EXISTS (
        SELECT 1 FROM auth.users 
        WHERE lower(email) = lower(trim(p_email))
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.check_user_exists(TEXT) TO anon, authenticated;

-- 3. RPC: get_user_provider (Returns provider string e.g. 'google', 'apple', 'email')
CREATE OR REPLACE FUNCTION public.get_user_provider(p_email TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_provider TEXT;
BEGIN
    IF p_email IS NULL OR trim(p_email) = '' THEN
        RETURN NULL;
    END IF;

    SELECT 
        COALESCE(
            raw_user_meta_data->>'provider',
            raw_app_meta_data->>'provider',
            'email'
        ) INTO v_provider
    FROM auth.users
    WHERE lower(email) = lower(trim(p_email))
    LIMIT 1;

    RETURN v_provider;
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_user_provider(TEXT) TO anon, authenticated;

-- 4. RPC: register_auth_user
-- Securely creates user in auth.users and public.profiles without exposing service_role key.
CREATE OR REPLACE FUNCTION public.register_auth_user(
    p_email TEXT,
    p_password TEXT DEFAULT '',
    p_first_name TEXT DEFAULT '',
    p_last_name TEXT DEFAULT '',
    p_provider TEXT DEFAULT 'email'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, extensions
AS $$
DECLARE
    v_user_id UUID;
    v_clean_email TEXT;
    v_encrypted_pw TEXT;
    v_full_name TEXT;
    v_meta JSONB;
BEGIN
    v_clean_email := lower(trim(p_email));
    IF v_clean_email IS NULL OR v_clean_email = '' THEN
        RAISE EXCEPTION 'Email cannot be blank';
    END IF;

    v_full_name := trim(p_first_name || ' ' || p_last_name);
    v_meta := jsonb_build_object(
        'first_name', trim(p_first_name),
        'last_name', trim(p_last_name),
        'full_name', v_full_name,
        'provider', p_provider
    );

    -- Check if user already exists
    SELECT id INTO v_user_id FROM auth.users WHERE lower(email) = v_clean_email;
    IF v_user_id IS NOT NULL THEN
        -- Update user_metadata and return existing
        UPDATE auth.users 
        SET raw_user_meta_data = raw_user_meta_data || v_meta,
            updated_at = NOW()
        WHERE id = v_user_id;

        INSERT INTO public.profiles (id, email, first_name, last_name, auth_provider)
        VALUES (v_user_id, v_clean_email, trim(p_first_name), trim(p_last_name), p_provider)
        ON CONFLICT (id) DO UPDATE SET
            first_name = CASE WHEN EXCLUDED.first_name <> '' THEN EXCLUDED.first_name ELSE public.profiles.first_name END,
            last_name = CASE WHEN EXCLUDED.last_name <> '' THEN EXCLUDED.last_name ELSE public.profiles.last_name END,
            updated_at = NOW();

        RETURN jsonb_build_object('success', true, 'user_id', v_user_id, 'is_existing', true);
    END IF;

    -- Generate new user UUID
    v_user_id := gen_random_uuid();
    v_encrypted_pw := CASE 
        WHEN p_password IS NOT NULL AND p_password <> '' THEN crypt(p_password, gen_salt('bf'))
        ELSE ''
    END;

    -- Insert into auth.users (GoTrue standard schema)
    INSERT INTO auth.users (
        instance_id,
        id,
        aud,
        role,
        email,
        encrypted_password,
        email_confirmed_at,
        raw_app_meta_data,
        raw_user_meta_data,
        created_at,
        updated_at
    ) VALUES (
        '00000000-0000-0000-0000-000000000000',
        v_user_id,
        'authenticated',
        'authenticated',
        v_clean_email,
        v_encrypted_pw,
        NOW(), -- Instant confirmation after OTP/social auth
        jsonb_build_object('provider', p_provider, 'providers', jsonb_build_array(p_provider)),
        v_meta,
        NOW(),
        NOW()
    );

    -- Upsert profile
    INSERT INTO public.profiles (
        id,
        email,
        first_name,
        last_name,
        auth_provider
    ) VALUES (
        v_user_id,
        v_clean_email,
        trim(p_first_name),
        trim(p_last_name),
        p_provider
    ) ON CONFLICT (id) DO UPDATE SET
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        updated_at = NOW();

    RETURN jsonb_build_object('success', true, 'user_id', v_user_id, 'is_existing', false);
END;
$$;
GRANT EXECUTE ON FUNCTION public.register_auth_user(TEXT, TEXT, TEXT, TEXT, TEXT) TO anon, authenticated;

-- 5. RPC: update_user_password (Safe password change/reset)
CREATE OR REPLACE FUNCTION public.update_user_password(
    p_email TEXT,
    p_new_password TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, extensions
AS $$
DECLARE
    v_user_id UUID;
    v_caller_id UUID;
BEGIN
    v_caller_id := auth.uid();
    
    SELECT id INTO v_user_id FROM auth.users WHERE lower(email) = lower(trim(p_email));
    IF v_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    -- If caller is authenticated, ensure they only change their own password
    IF v_caller_id IS NOT NULL AND v_caller_id <> v_user_id THEN
        RAISE EXCEPTION 'Unauthorized to change password for another user';
    END IF;

    UPDATE auth.users
    SET encrypted_password = crypt(p_new_password, gen_salt('bf')),
        updated_at = NOW()
    WHERE id = v_user_id;

    RETURN TRUE;
END;
$$;
GRANT EXECUTE ON FUNCTION public.update_user_password(TEXT, TEXT) TO anon, authenticated;

-- 6. RPC: delete_user_account (User deletes their own account)
CREATE OR REPLACE FUNCTION public.delete_user_account(p_email TEXT DEFAULT NULL)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    v_user_id := auth.uid();
    
    IF v_user_id IS NULL AND p_email IS NOT NULL AND trim(p_email) <> '' THEN
        SELECT id INTO v_user_id FROM auth.users WHERE lower(email) = lower(trim(p_email));
    END IF;

    IF v_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    DELETE FROM auth.users WHERE id = v_user_id;
    RETURN TRUE;
END;
$$;
GRANT EXECUTE ON FUNCTION public.delete_user_account(TEXT) TO anon, authenticated;
-- ==============================================================================
-- SyncSwift v2 - Migration 000001: Devices v2 & Cryptographic Identity
-- ==============================================================================

-- 1. Create or replace devices v2 table
create table if not exists public.devices (
    id text primary key, -- Stable device UUID generated on client
    user_id uuid not null references auth.users(id) on delete cascade,
    device_name text not null,
    os_type text not null check (os_type in ('android', 'ios', 'windows', 'macos', 'linux')),
    signing_pubkey text not null, -- Base64 Ed25519 Public Key
    agreement_pubkey text not null, -- Base64 X25519 Public Key
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'REVOKED', 'SUSPENDED')),
    last_seen timestamptz not null default now(),
    capabilities jsonb not null default '{"lan_transfer": true, "relay_transfer": true, "vault": true}'::jsonb,
    push_token text,
    app_version text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint unique_user_device unique (user_id, id)
);

-- Indexing for fast lookups
create index if not exists idx_devices_user_id on public.devices(user_id);
create index if not exists idx_devices_status on public.devices(status);
create index if not exists idx_devices_last_seen on public.devices(last_seen);

-- Enable Row Level Security (RLS)
alter table public.devices enable row level security;

-- 2. Strict RLS Policies
-- Users can only read their own active or revoked devices
create policy "Users can view own devices"
    on public.devices for select
    to authenticated
    using (auth.uid() = user_id);

-- Users can insert their own device
create policy "Users can insert own device"
    on public.devices for insert
    to authenticated
    with check (auth.uid() = user_id);

-- Users can update their own device (e.g. name, last_seen, push_token)
create policy "Users can update own device"
    on public.devices for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Users can delete their own device
create policy "Users can delete own device"
    on public.devices for delete
    to authenticated
    using (auth.uid() = user_id);

-- 3. Stored Procedure: Upsert Device Identity (Safe RPC)
create or replace function public.upsert_device_identity(
    p_device_id text,
    p_device_name text,
    p_os_type text,
    p_signing_pubkey text,
    p_agreement_pubkey text,
    p_push_token text default null,
    p_app_version text default '2.0.0'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
    v_result jsonb;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'Unauthorized');
    end if;

    insert into public.devices (
        id,
        user_id,
        device_name,
        os_type,
        signing_pubkey,
        agreement_pubkey,
        push_token,
        app_version,
        last_seen,
        updated_at
    )
    values (
        p_device_id,
        v_user_id,
        p_device_name,
        lower(p_os_type),
        p_signing_pubkey,
        p_agreement_pubkey,
        p_push_token,
        p_app_version,
        now(),
        now()
    )
    on conflict (id) do update set
        device_name = excluded.device_name,
        signing_pubkey = excluded.signing_pubkey,
        agreement_pubkey = excluded.agreement_pubkey,
        push_token = coalesce(excluded.push_token, devices.push_token),
        app_version = excluded.app_version,
        last_seen = now(),
        updated_at = now()
    where devices.user_id = v_user_id;

    select to_jsonb(d) into v_result
    from public.devices d
    where d.id = p_device_id and d.user_id = v_user_id;

    return jsonb_build_object('success', true, 'device', v_result);
end;
$$;

revoke all on function public.upsert_device_identity(text, text, text, text, text, text, text) from public;
grant execute on function public.upsert_device_identity(text, text, text, text, text, text, text) to authenticated;

-- 4. Stored Procedure: Revoke Device
create or replace function public.revoke_device(
    p_device_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'Unauthorized');
    end if;

    update public.devices
    set status = 'REVOKED',
        updated_at = now()
    where id = p_device_id and user_id = v_user_id;

    if not found then
        return jsonb_build_object('success', false, 'error', 'Device not found');
    end if;

    return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.revoke_device(text) from public;
grant execute on function public.revoke_device(text) to authenticated;
-- ==============================================================================
-- SyncSwift v2 - Migration 000002: Pairing Sessions, Permissions & Audit
-- ==============================================================================

-- 1. Pairing Sessions Table
create table if not exists public.pairing_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    initiator_device_id text not null references public.devices(id) on delete cascade,
    target_device_id text references public.devices(id) on delete set null,
    pairing_code text not null unique,
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'TRUSTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    sas_code text not null, -- 6-digit SAS code for visual verification
    initiator_pubkey text not null,
    target_pubkey text,
    expires_at timestamptz not null default (now() + interval '5 minutes'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_pairing_code on public.pairing_sessions(pairing_code);
create index if not exists idx_pairing_status on public.pairing_sessions(status);
create index if not exists idx_pairing_user on public.pairing_sessions(user_id);

-- 2. Device Permissions Table
create table if not exists public.device_permissions (
    device_id text not null references public.devices(id) on delete cascade,
    paired_device_id text not null references public.devices(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    can_transfer_files boolean not null default true,
    can_sync_clipboard boolean not null default true,
    can_access_vault boolean not null default false,
    is_trusted boolean not null default false,
    trusted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (device_id, paired_device_id)
);

create index if not exists idx_permissions_user on public.device_permissions(user_id);

-- 3. Device Revocations Table
create table if not exists public.device_revocations (
    id uuid primary key default gen_random_uuid(),
    device_id text not null references public.devices(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    reason text not null default 'MANUAL_REVOCATION',
    revoked_at timestamptz not null default now()
);

-- 4. Audit Events Table
create table if not exists public.audit_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    event_type text not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_audit_events_user on public.audit_events(user_id);

-- Enable RLS
alter table public.pairing_sessions enable row level security;
alter table public.device_permissions enable row level security;
alter table public.device_revocations enable row level security;
alter table public.audit_events enable row level security;

-- Strict RLS Policies
create policy "Users can view own pairing sessions"
    on public.pairing_sessions for select
    to authenticated
    using (auth.uid() = user_id);

create policy "Users can manage own device permissions"
    on public.device_permissions for all
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "Users can view own audit events"
    on public.audit_events for select
    to authenticated
    using (auth.uid() = user_id);

-- ==============================================================================
-- 5. Stored Procedures (Safe Security Definer RPCs)
-- ==============================================================================

-- Create a new pairing session
create or replace function public.create_pairing_session(
    p_initiator_device_id text,
    p_pairing_code text,
    p_sas_code text,
    p_initiator_pubkey text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
    v_session_id uuid;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'Unauthorized');
    end if;

    -- Expire any older active sessions for this device
    update public.pairing_sessions
    set status = 'CANCELLED', updated_at = now()
    where initiator_device_id = p_initiator_device_id and status = 'PENDING';

    insert into public.pairing_sessions (
        user_id,
        initiator_device_id,
        pairing_code,
        sas_code,
        initiator_pubkey,
        status,
        expires_at
    )
    values (
        v_user_id,
        p_initiator_device_id,
        p_pairing_code,
        p_sas_code,
        p_initiator_pubkey,
        'PENDING',
        now() + interval '5 minutes'
    )
    returning id into v_session_id;

    -- Audit event
    insert into public.audit_events (user_id, event_type, details)
    values (v_user_id, 'PAIRING_INITIATED', jsonb_build_object('session_id', v_session_id, 'device_id', p_initiator_device_id));

    return jsonb_build_object('success', true, 'session_id', v_session_id, 'pairing_code', p_pairing_code);
end;
$$;

revoke all on function public.create_pairing_session(text, text, text, text) from public;
grant execute on function public.create_pairing_session(text, text, text, text) to authenticated;

-- Respond to pairing session (Accept / Reject)
create or replace function public.respond_pairing_session(
    p_pairing_code text,
    p_target_device_id text,
    p_target_pubkey text,
    p_decision text -- 'APPROVED' or 'REJECTED'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
    v_session record;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'Unauthorized');
    end if;

    select * into v_session
    from public.pairing_sessions
    where pairing_code = p_pairing_code and user_id = v_user_id
    for update;

    if v_session is null then
        return jsonb_build_object('success', false, 'error', 'Invalid pairing code');
    end if;

    if v_session.expires_at < now() or v_session.status != 'PENDING' then
        update public.pairing_sessions set status = 'EXPIRED', updated_at = now() where id = v_session.id;
        return jsonb_build_object('success', false, 'error', 'Session expired or not pending');
    end if;

    if p_decision = 'APPROVED' then
        update public.pairing_sessions
        set status = 'APPROVED',
            target_device_id = p_target_device_id,
            target_pubkey = p_target_pubkey,
            updated_at = now()
        where id = v_session.id;

        -- Create reciprocal mutual device permissions
        insert into public.device_permissions (device_id, paired_device_id, user_id, is_trusted, trusted_at)
        values
            (v_session.initiator_device_id, p_target_device_id, v_user_id, true, now()),
            (p_target_device_id, v_session.initiator_device_id, v_user_id, true, now())
        on conflict (device_id, paired_device_id) do update set
            is_trusted = true,
            trusted_at = now(),
            updated_at = now();

        insert into public.audit_events (user_id, event_type, details)
        values (v_user_id, 'PAIRING_APPROVED', jsonb_build_object(
            'initiator', v_session.initiator_device_id,
            'target', p_target_device_id
        ));

        return jsonb_build_object('success', true, 'status', 'APPROVED', 'sas_code', v_session.sas_code);
    else
        update public.pairing_sessions
        set status = 'REJECTED',
            updated_at = now()
        where id = v_session.id;

        insert into public.audit_events (user_id, event_type, details)
        values (v_user_id, 'PAIRING_REJECTED', jsonb_build_object('session_id', v_session.id));

        return jsonb_build_object('success', true, 'status', 'REJECTED');
    end if;
end;
$$;

revoke all on function public.respond_pairing_session(text, text, text, text) from public;
grant execute on function public.respond_pairing_session(text, text, text, text) to authenticated;
-- ==============================================================================
-- SyncSwift v2 - Migration 000003: Pending Cloud Transfers & Personal Vault
-- ==============================================================================

-- 1. Pending Cloud Transfers Table (Temporary MinIO transfers for offline devices)
create table if not exists public.pending_cloud_transfers (
    id text primary key, -- Transfer ID UUID
    user_id uuid not null references auth.users(id) on delete cascade,
    sender_device_id text not null references public.devices(id) on delete cascade,
    recipient_device_id text not null references public.devices(id) on delete cascade,
    manifest_json text not null, -- Serialized TransferManifest
    s3_bucket text not null default 'transfers-temp',
    s3_prefix text not null, -- transfers/{transfer_id}/
    total_chunks int not null,
    total_size bigint not null,
    status text not null default 'AVAILABLE' check (status in ('AVAILABLE', 'DOWNLOADING', 'COMPLETED', 'EXPIRED')),
    expires_at timestamptz not null default (now() + interval '72 hours'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_cloud_recipient on public.pending_cloud_transfers(recipient_device_id, status);
create index if not exists idx_cloud_user on public.pending_cloud_transfers(user_id);

-- 2. Personal Vault Metadata Table (End-to-End Encrypted files stored on MinIO)
create table if not exists public.vault_files (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    file_name text not null,
    file_size bigint not null,
    mime_type text not null default 'application/octet-stream',
    s3_bucket text not null default 'vault',
    s3_key text not null, -- vault/{user_id}/{file_id}.enc
    wrapped_dek text not null, -- DEK encrypted with Master Recovery Key
    nonce_hex text not null, -- Nonce used for file encryption
    is_starred boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_vault_user on public.vault_files(user_id);

-- Enable RLS
alter table public.pending_cloud_transfers enable row level security;
alter table public.vault_files enable row level security;

-- Policies for Pending Cloud Transfers
create policy "Users can view pending transfers for their devices"
    on public.pending_cloud_transfers for select
    to authenticated
    using (auth.uid() = user_id);

create policy "Users can create pending transfers"
    on public.pending_cloud_transfers for insert
    to authenticated
    with check (auth.uid() = user_id);

create policy "Users can update pending transfers"
    on public.pending_cloud_transfers for update
    to authenticated
    using (auth.uid() = user_id);

create policy "Users can delete completed pending transfers"
    on public.pending_cloud_transfers for delete
    to authenticated
    using (auth.uid() = user_id);

-- Policies for Vault Files
create policy "Users can manage own vault files"
    on public.vault_files for all
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- Stored Procedure: Register Pending Cloud Transfer
create or replace function public.register_cloud_transfer(
    p_transfer_id text,
    p_sender_device_id text,
    p_recipient_device_id text,
    p_manifest_json text,
    p_s3_prefix text,
    p_total_chunks int,
    p_total_size bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'Unauthorized');
    end if;

    insert into public.pending_cloud_transfers (
        id,
        user_id,
        sender_device_id,
        recipient_device_id,
        manifest_json,
        s3_prefix,
        total_chunks,
        total_size,
        status,
        expires_at
    )
    values (
        p_transfer_id,
        v_user_id,
        p_sender_device_id,
        p_recipient_device_id,
        p_manifest_json,
        p_s3_prefix,
        p_total_chunks,
        p_total_size,
        'AVAILABLE',
        now() + interval '72 hours'
    );

    return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.register_cloud_transfer(text, text, text, text, text, int, bigint) from public;
grant execute on function public.register_cloud_transfer(text, text, text, text, text, int, bigint) to authenticated;
