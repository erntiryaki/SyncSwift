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
