-- Meta CAPI match keys for server-side Purchase attribution.
-- Device data is collected while the app is open; Cashfree webhooks send Purchase later.
-- Adapted for this app: public.users (not profiles). Writes are service_role-only
-- via the save-meta-match-data Edge Function (no Supabase Auth / auth.uid()).

alter table public.users
  add column if not exists meta_anon_id text;

comment on column public.users.meta_anon_id is
  'Facebook SDK anonymous app device GUID; used as CAPI user_data.anon_id';

create table if not exists public.meta_match_data (
  user_id uuid primary key references public.users (id) on delete cascade,
  madid text,
  ad_tracking_enabled boolean not null default false,
  extinfo text[],
  client_user_agent text,
  client_ip text,
  updated_at timestamptz not null default now()
);

comment on table public.meta_match_data is
  'Per-user Meta advertising match keys for Conversions API app Purchase events';

alter table public.meta_match_data enable row level security;

-- No policies on purpose: PostgREST clients (anon/authenticated) cannot read or write.
revoke all on table public.meta_match_data from public, anon, authenticated;
