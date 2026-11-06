drop trigger if exists enforce_users_billing_ins on public.users;
drop trigger if exists enforce_users_billing_upd on public.users;
drop function if exists public.enforce_users_billing_mutation_policy();

create or replace function public.get_admin_dashboard_stats()
 returns json
 language sql
 stable security definer
 set search_path to 'public'
as $function$
  with premium as (
    select distinct on (user_id) user_id, plan_type, lower(trim(status)) as status
    from public.subscriptions
    where lower(trim(status)) in ('active', 'cancelled')
      and end_date > now()
    order by user_id, end_date desc
  )
  select json_build_object(
    'registered_users', (select count(*)::bigint from auth.users),
    'wallpapers_total', (select count(*)::bigint from public.wallpapers),
    'reels_total', (select count(*)::bigint from public.reels),
    'wallpapers_last_7_days', (
      select count(*)::bigint from public.wallpapers
      where created_at >= (now() at time zone 'utc') - interval '7 days'
    ),
    'reels_last_7_days', (
      select count(*)::bigint from public.reels
      where created_at >= (now() at time zone 'utc') - interval '7 days'
    ),
    'analytics_events_last_30_days', (
      select count(*)::bigint from public.analytics_events
      where created_at >= (now() at time zone 'utc') - interval '30 days'
    ),
    'subscription_profiles_total', (select count(*)::bigint from public.users),
    'users_subscribed', (select count(*)::bigint from premium),
    'users_not_subscribed', (
      select count(*)::bigint from public.users u
      where not exists (select 1 from premium p where p.user_id = u.id)
    ),
    'subscriptions_by_plan', (
      select coalesce(
        (
          select json_object_agg(plan_key, ct)
          from (
            select coalesce(nullif(trim(plan_type), ''), 'no_plan') as plan_key, count(*)::bigint as ct
            from premium
            group by plan_key
          ) s
        ),
        '{}'::json
      )
    ),
    'subscriptions_by_status', (
      select coalesce(
        (
          select json_object_agg(st, ct)
          from (
            select coalesce(nullif(status, ''), '(no status)') as st, count(*)::bigint as ct
            from premium
            group by st
          ) q
        ),
        '{}'::json
      )
    )
  );
$function$;

alter table public.users
  drop column if exists is_subscribed,
  drop column if exists razorpay_payment_id,
  drop column if exists razorpay_subscription_id,
  drop column if exists subscription_status,
  drop column if exists current_period_end,
  drop column if exists trial_end,
  drop column if exists trial_paid,
  drop column if exists cancelled_at,
  drop column if exists plan_id;

drop table if exists public.razorpay_webhook_events;
