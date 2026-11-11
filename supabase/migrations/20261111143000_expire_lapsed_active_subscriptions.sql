-- Mark lapsed "active" rows as expired.
-- Premium is end_date-based; status=active after end_date confused renew/reporting.
-- Safe: computeIsPremium already requires end_date in the future for active/cancelled.

update public.subscriptions
set
  status = 'expired',
  autopay_enabled = false,
  updated_at = now()
where status = 'active'
  and (
    end_date is null
    or end_date::timestamptz <= now()
  );
