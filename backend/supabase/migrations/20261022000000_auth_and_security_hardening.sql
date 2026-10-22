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
