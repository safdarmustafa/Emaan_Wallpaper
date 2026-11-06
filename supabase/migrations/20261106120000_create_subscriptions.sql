-- Cashfree UPI Autopay subscriptions. Written only by Edge Functions (service_role);
-- cashfree-webhook is the sole writer of status / end_date.

-- user_id mirrors the type of public.users.id (created outside migrations).
DO $$
DECLARE
  users_id_type text;
BEGIN
  SELECT format_type(a.atttypid, a.atttypmod)
    INTO users_id_type
    FROM pg_attribute a
   WHERE a.attrelid = 'public.users'::regclass
     AND a.attname = 'id'
     AND NOT a.attisdropped;

  IF users_id_type IS NULL THEN
    RAISE EXCEPTION 'public.users.id not found';
  END IF;

  EXECUTE format($sql$
    CREATE TABLE IF NOT EXISTS public.subscriptions (
      id                        BIGSERIAL PRIMARY KEY,
      user_id                   %s NOT NULL REFERENCES public.users(id),
      phone                     VARCHAR(15),
      plan_type                 VARCHAR(50) NOT NULL DEFAULT 'monthly',
      status                    VARCHAR(20) NOT NULL DEFAULT 'pending',
      cashfree_subscription_id  TEXT,
      autopay_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
      start_date                TIMESTAMP,
      end_date                  TIMESTAMP,
      next_billing_date         TEXT,
      last_payment_id           TEXT,
      created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE (user_id)
    )
  $sql$, users_id_type);
END $$;

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id
  ON public.subscriptions (user_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_cashfree_id
  ON public.subscriptions (cashfree_subscription_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_status
  ON public.subscriptions (status);

-- No policies: anon / authenticated clients cannot read or forge rows via PostgREST.
-- Edge Functions use service_role, which bypasses RLS.
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
