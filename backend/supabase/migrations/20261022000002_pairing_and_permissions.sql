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
