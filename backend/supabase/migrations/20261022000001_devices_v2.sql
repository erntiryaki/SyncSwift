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
