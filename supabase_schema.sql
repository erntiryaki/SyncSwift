-- ============================================================
-- SyncSwift Database Schema
-- Complete Supabase DDL for Authentication, Devices, Pairing & Workspaces
-- ============================================================

-- 1. Profiles Table
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- Enable RLS on profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read and write on profiles" 
ON public.profiles FOR ALL 
USING (true) 
WITH CHECK (true);

-- 2. Devices Table
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    device_type TEXT NOT NULL, -- 'Android', 'iOS', 'Windows', 'macOS'
    push_token TEXT,
    local_ip TEXT,
    port INTEGER DEFAULT 52424,
    is_online BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Enable RLS on devices
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read and write on devices" 
ON public.devices FOR ALL 
USING (true) 
WITH CHECK (true);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_push_token ON public.devices(push_token);

-- 3. Pairing Codes Table (Quick Access, Device Pairing & Workspace Invites)
CREATE TABLE IF NOT EXISTS public.pairing_codes (
    code TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    device_name TEXT NOT NULL,
    device_type TEXT NOT NULL,
    local_ip TEXT NOT NULL,
    port INTEGER NOT NULL DEFAULT 52424,
    push_token TEXT,
    status TEXT DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Enable RLS on pairing_codes
ALTER TABLE public.pairing_codes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read and write on pairing_codes" 
ON public.pairing_codes FOR ALL 
USING (true) 
WITH CHECK (true);

CREATE INDEX IF NOT EXISTS idx_pairing_codes_code ON public.pairing_codes(code);
CREATE INDEX IF NOT EXISTS idx_pairing_codes_device_id ON public.pairing_codes(device_id);

-- 4. Workspaces Table (Optional / Extensible for Cloud Workspaces)
CREATE TABLE IF NOT EXISTS public.workspaces (
    id TEXT PRIMARY KEY,
    owner_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    owner_name TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.workspaces ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow public read and write on workspaces" ON public.workspaces FOR ALL USING (true) WITH CHECK (true);

-- 5. Workspace Members Table
CREATE TABLE IF NOT EXISTS public.workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id TEXT NOT NULL,
    member_name TEXT NOT NULL,
    member_role TEXT DEFAULT 'MEMBER',
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE public.workspace_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow public read and write on workspace_members" ON public.workspace_members FOR ALL USING (true) WITH CHECK (true);
CREATE INDEX IF NOT EXISTS idx_workspace_members_ws_id ON public.workspace_members(workspace_id);
