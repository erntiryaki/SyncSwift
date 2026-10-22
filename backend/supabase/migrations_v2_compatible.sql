-- ==============================================================================
-- SyncSwift v2: Production Database Schema & RPC Migrations (Compatible)
-- Target: supabase-db-syncdropsupabase20261014 (PostgreSQL 15)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------------------------
-- 1. Profiles Table Hardening
-- ------------------------------------------------------------------------------
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

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS first_name TEXT DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS last_name TEXT DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS auth_provider TEXT DEFAULT 'email';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_pro BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS pro_start_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS pro_renewal_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read and write on profiles" ON public.profiles;
DROP POLICY IF EXISTS "Users can read own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
DROP POLICY IF EXISTS "Allow anon profile check" ON public.profiles;

CREATE POLICY "Users can read own profile"
ON public.profiles FOR SELECT
TO authenticated
USING (auth.uid() = id);

CREATE POLICY "Users can update own profile"
ON public.profiles FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- ------------------------------------------------------------------------------
-- 2. Devices Table Hardening & V2 Compatibility
-- ------------------------------------------------------------------------------
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS os_type TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS signing_pubkey TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS agreement_pubkey TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS capabilities JSONB DEFAULT '{"lan_transfer": true, "relay_transfer": true, "vault": true}'::jsonb;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS app_version TEXT DEFAULT '2.0.0';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();

-- Update missing values from legacy columns
UPDATE public.devices SET os_type = lower(device_type) WHERE os_type IS NULL AND device_type IS NOT NULL;
UPDATE public.devices SET last_seen = last_seen_at WHERE last_seen IS NULL AND last_seen_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_status ON public.devices(status);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON public.devices(last_seen);

ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all devices" ON public.devices;
DROP POLICY IF EXISTS "Allow public read and write on devices" ON public.devices;
DROP POLICY IF EXISTS "Users can view own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can insert own device" ON public.devices;
DROP POLICY IF EXISTS "Users can update own device" ON public.devices;
DROP POLICY IF EXISTS "Users can delete own device" ON public.devices;

CREATE POLICY "Users can view own devices"
ON public.devices FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own device"
ON public.devices FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own device"
ON public.devices FOR UPDATE
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own device"
ON public.devices FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 3. Pairing Sessions Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.pairing_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    initiator_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    target_device_id UUID REFERENCES public.devices(id) ON DELETE SET NULL,
    pairing_code TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'TRUSTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    sas_code TEXT NOT NULL,
    initiator_pubkey TEXT NOT NULL,
    target_pubkey TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() + INTERVAL '5 minutes'),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pairing_code ON public.pairing_sessions(pairing_code);
CREATE INDEX IF NOT EXISTS idx_pairing_status ON public.pairing_sessions(status);
CREATE INDEX IF NOT EXISTS idx_pairing_user ON public.pairing_sessions(user_id);

ALTER TABLE public.pairing_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view pairing sessions" ON public.pairing_sessions;
CREATE POLICY "Users can view pairing sessions"
ON public.pairing_sessions FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 4. Device Permissions Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.device_permissions (
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    paired_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    can_transfer_files BOOLEAN NOT NULL DEFAULT TRUE,
    can_sync_clipboard BOOLEAN NOT NULL DEFAULT TRUE,
    can_access_vault BOOLEAN NOT NULL DEFAULT FALSE,
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE,
    trusted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (device_id, paired_device_id)
);

CREATE INDEX IF NOT EXISTS idx_permissions_user ON public.device_permissions(user_id);
ALTER TABLE public.device_permissions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view permissions" ON public.device_permissions;
CREATE POLICY "Users can view permissions"
ON public.device_permissions FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 5. Device Revocations Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.device_revocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL DEFAULT 'MANUAL_REVOCATION',
    revoked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.device_revocations ENABLE ROW LEVEL SECURITY;

-- ------------------------------------------------------------------------------
-- 6. Audit Events Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    actor_device_id UUID,
    target_device_id UUID,
    metadata JSONB DEFAULT '{}'::jsonb,
    ip_address TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;

-- ------------------------------------------------------------------------------
-- 7. Pending Cloud Transfers Table (MinIO offline transfers)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.pending_cloud_transfers (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    recipient_device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    manifest_json TEXT NOT NULL,
    s3_bucket TEXT NOT NULL DEFAULT 'transfers-temp',
    s3_prefix TEXT NOT NULL,
    total_chunks INT NOT NULL,
    total_size BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'DOWNLOADING', 'COMPLETED', 'EXPIRED')),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() + INTERVAL '72 hours'),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cloud_recipient ON public.pending_cloud_transfers(recipient_device_id, status);
CREATE INDEX IF NOT EXISTS idx_cloud_user ON public.pending_cloud_transfers(user_id);

ALTER TABLE public.pending_cloud_transfers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view pending transfers for their devices" ON public.pending_cloud_transfers;
CREATE POLICY "Users can view pending transfers for their devices"
ON public.pending_cloud_transfers FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can create pending transfers" ON public.pending_cloud_transfers;
CREATE POLICY "Users can create pending transfers"
ON public.pending_cloud_transfers FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update pending transfers" ON public.pending_cloud_transfers;
CREATE POLICY "Users can update pending transfers"
ON public.pending_cloud_transfers FOR UPDATE
TO authenticated
USING (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 8. Personal Vault Metadata Table (End-to-End Encrypted files)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vault_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type TEXT NOT NULL DEFAULT 'application/octet-stream',
    s3_bucket TEXT NOT NULL DEFAULT 'vault',
    s3_key TEXT NOT NULL,
    wrapped_dek TEXT NOT NULL,
    nonce_hex TEXT NOT NULL,
    is_starred BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vault_user ON public.vault_files(user_id);
ALTER TABLE public.vault_files ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own vault files" ON public.vault_files;
CREATE POLICY "Users can view own vault files"
ON public.vault_files FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own vault files" ON public.vault_files;
CREATE POLICY "Users can insert own vault files"
ON public.vault_files FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own vault files" ON public.vault_files;
CREATE POLICY "Users can update own vault files"
ON public.vault_files FOR UPDATE
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete own vault files" ON public.vault_files;
CREATE POLICY "Users can delete own vault files"
ON public.vault_files FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 9. RPC Suite (Security Definer with zero leakage)
-- ------------------------------------------------------------------------------

-- check_user_exists
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

-- get_user_provider
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
        RETURN 'none';
    END IF;

    SELECT COALESCE(raw_app_meta_data->>'provider', 'email')
    INTO v_provider
    FROM auth.users
    WHERE lower(email) = lower(trim(p_email))
    LIMIT 1;

    RETURN COALESCE(v_provider, 'none');
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_user_provider(TEXT) TO anon, authenticated;

-- register_auth_user
CREATE OR REPLACE FUNCTION public.register_auth_user(
    p_email TEXT,
    p_password TEXT,
    p_first_name TEXT DEFAULT '',
    p_last_name TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
    v_clean_email TEXT;
BEGIN
    v_clean_email := lower(trim(p_email));
    IF v_clean_email IS NULL OR v_clean_email = '' THEN
        RETURN jsonb_build_object('success', false, 'error', 'Email is required');
    END IF;

    IF EXISTS (SELECT 1 FROM auth.users WHERE lower(email) = v_clean_email) THEN
        RETURN jsonb_build_object('success', false, 'error', 'User already exists');
    END IF;

    v_user_id := gen_random_uuid();

    INSERT INTO auth.users (
        instance_id,
        id,
        aud,
        role,
        email,
        encrypted_password,
        email_confirmed_at,
        created_at,
        updated_at,
        raw_app_meta_data,
        raw_user_meta_data,
        is_super_admin
    ) VALUES (
        '00000000-0000-0000-0000-000000000000',
        v_user_id,
        'authenticated',
        'authenticated',
        v_clean_email,
        crypt(p_password, gen_salt('bf', 10)),
        NOW(),
        NOW(),
        NOW(),
        '{"provider": "email", "providers": ["email"]}'::jsonb,
        jsonb_build_object('first_name', p_first_name, 'last_name', p_last_name),
        FALSE
    );

    INSERT INTO public.profiles (
        id,
        email,
        first_name,
        last_name,
        auth_provider,
        created_at,
        updated_at
    ) VALUES (
        v_user_id,
        v_clean_email,
        COALESCE(p_first_name, ''),
        COALESCE(p_last_name, ''),
        'email',
        NOW(),
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        updated_at = NOW();

    RETURN jsonb_build_object('success', true, 'user_id', v_user_id);
END;
$$;
GRANT EXECUTE ON FUNCTION public.register_auth_user(TEXT, TEXT, TEXT, TEXT) TO anon, authenticated;

-- upsert_device_identity
CREATE OR REPLACE FUNCTION public.upsert_device_identity(
    p_device_id TEXT,
    p_device_name TEXT,
    p_os_type TEXT,
    p_signing_pubkey TEXT DEFAULT '',
    p_agreement_pubkey TEXT DEFAULT '',
    p_push_token TEXT DEFAULT NULL,
    p_app_version TEXT DEFAULT '2.0.0'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_dev_uuid UUID;
    v_result JSONB;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized');
    END IF;

    BEGIN
        v_dev_uuid := p_device_id::UUID;
    EXCEPTION WHEN OTHERS THEN
        v_dev_uuid := gen_random_uuid();
    END;

    INSERT INTO public.devices (
        id,
        user_id,
        device_name,
        device_type,
        os_type,
        signing_pubkey,
        agreement_pubkey,
        push_token,
        app_version,
        last_seen,
        last_seen_at,
        is_online,
        updated_at
    )
    VALUES (
        v_dev_uuid,
        v_user_id,
        p_device_name,
        p_os_type,
        lower(p_os_type),
        p_signing_pubkey,
        p_agreement_pubkey,
        p_push_token,
        p_app_version,
        NOW(),
        NOW(),
        TRUE,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        device_name = EXCLUDED.device_name,
        device_type = EXCLUDED.device_type,
        os_type = EXCLUDED.os_type,
        signing_pubkey = COALESCE(NULLIF(EXCLUDED.signing_pubkey, ''), devices.signing_pubkey),
        agreement_pubkey = COALESCE(NULLIF(EXCLUDED.agreement_pubkey, ''), devices.agreement_pubkey),
        push_token = COALESCE(EXCLUDED.push_token, devices.push_token),
        app_version = EXCLUDED.app_version,
        last_seen = NOW(),
        last_seen_at = NOW(),
        is_online = TRUE,
        updated_at = NOW()
    WHERE devices.user_id = v_user_id;

    SELECT to_jsonb(d) INTO v_result
    FROM public.devices d
    WHERE d.id = v_dev_uuid AND d.user_id = v_user_id;

    RETURN jsonb_build_object('success', true, 'device', v_result);
END;
$$;
GRANT EXECUTE ON FUNCTION public.upsert_device_identity(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

-- revoke_device
CREATE OR REPLACE FUNCTION public.revoke_device(
    p_device_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_dev_uuid UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthorized');
    END IF;

    BEGIN
        v_dev_uuid := p_device_id::UUID;
    EXCEPTION WHEN OTHERS THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid device ID');
    END;

    UPDATE public.devices
    SET status = 'REVOKED',
        is_online = FALSE,
        updated_at = NOW()
    WHERE id = v_dev_uuid AND user_id = v_user_id;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Device not found');
    END IF;

    RETURN jsonb_build_object('success', true);
END;
$$;
GRANT EXECUTE ON FUNCTION public.revoke_device(TEXT) TO authenticated;
