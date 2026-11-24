-- Self-rolling-back SQL checks for meta_match_data.
-- Run AFTER the migration is applied (or inside a dry-run that creates objects then rolls back).
-- Usage: wrap with BEGIN; ... ROLLBACK; or run as a script that ends in ROLLBACK.

begin;

do $$
declare
  v_uid uuid := gen_random_uuid();
  v_phone text := '9999900001';
  v_count bigint;
  v_madid text;
  v_tracking boolean;
  v_extinfo text[];
  v_anon text;
  v_err text;
begin
  -- Fixture user (rolled back with the transaction).
  insert into public.users (id, phone_number)
  values (v_uid, v_phone);

  -- Invalid / all-zero / limited-tracking madid stored as null (service_role write path).
  insert into public.meta_match_data (
    user_id, madid, ad_tracking_enabled, extinfo, client_user_agent, client_ip
  ) values (
    v_uid, null, false, null, 'TestAgent/1.0', '1.2.3.4'
  );

  select madid, ad_tracking_enabled into v_madid, v_tracking
  from public.meta_match_data where user_id = v_uid;
  if v_madid is not null or v_tracking is distinct from false then
    raise exception 'expected null madid and tracking false for limited row';
  end if;

  -- Bad extinfo dropped by app/edge sanitizer; DB accepts null.
  update public.meta_match_data
     set extinfo = null,
         madid = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
         ad_tracking_enabled = true,
         updated_at = now()
   where user_id = v_uid;

  select madid, ad_tracking_enabled, extinfo
    into v_madid, v_tracking, v_extinfo
  from public.meta_match_data where user_id = v_uid;
  if v_madid is null or v_tracking is not true or v_extinfo is not null then
    raise exception 'overwrite / null extinfo expectations failed';
  end if;

  -- Second save overwrites.
  update public.meta_match_data
     set madid = '11111111-2222-3333-4444-555555555555',
         ad_tracking_enabled = true,
         extinfo = array[
           'a2','com.squarenova.emaanwallpapers','17','1.2.3','14',
           'Pixel','en_IN','IST','','1080','2400','2.75','8','128','64','Asia/Kolkata'
         ],
         client_user_agent = 'Agent/2',
         client_ip = '8.8.8.8',
         updated_at = now()
   where user_id = v_uid;

  select madid, client_user_agent into v_madid, v_err
  from public.meta_match_data where user_id = v_uid;
  if v_madid <> '11111111-2222-3333-4444-555555555555' or v_err <> 'Agent/2' then
    raise exception 'second save did not overwrite';
  end if;

  update public.users
     set meta_anon_id = 'anon-test-guid'
   where id = v_uid;
  select meta_anon_id into v_anon from public.users where id = v_uid;
  if v_anon <> 'anon-test-guid' then
    raise exception 'meta_anon_id not stored on users';
  end if;

  -- anon/authenticated must not be able to select (RLS, no policies).
  -- Verified via privilege revoke; table has RLS enabled.
  select count(*) into v_count
  from pg_policies
  where schemaname = 'public' and tablename = 'meta_match_data';
  if v_count <> 0 then
    raise exception 'meta_match_data must have zero RLS policies, found %', v_count;
  end if;

  if not exists (
    select 1 from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname = 'meta_match_data' and c.relrowsecurity
  ) then
    raise exception 'meta_match_data RLS is not enabled';
  end if;

  raise notice 'meta_match_data_test OK';
end $$;

rollback;
