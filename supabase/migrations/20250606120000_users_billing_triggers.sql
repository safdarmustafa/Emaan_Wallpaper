-- Prevent clients (anon / authenticated JWT) from forging subscription state via PostgREST.
-- Edge Functions and service_role updates still apply; dashboard SQL sessions typically have NULL auth.role() and pass through.

CREATE OR REPLACE FUNCTION public.enforce_users_billing_mutation_policy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
  r text;
  old_status text;
BEGIN
  r := auth.role();

  IF r IS NULL OR r = 'service_role' THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.is_subscribed IS TRUE THEN
      RAISE EXCEPTION 'billing_guard: client cannot insert is_subscribed=true';
    END IF;
    IF NEW.subscription_status IS NOT NULL THEN
      RAISE EXCEPTION 'billing_guard: client cannot set subscription_status on insert';
    END IF;
    IF NEW.razorpay_subscription_id IS NOT NULL OR NEW.razorpay_payment_id IS NOT NULL THEN
      RAISE EXCEPTION 'billing_guard: client cannot set Razorpay ids on insert';
    END IF;
    IF NEW.trial_end IS NOT NULL OR NEW.trial_paid IS TRUE THEN
      RAISE EXCEPTION 'billing_guard: client cannot set trial fields on insert';
    END IF;
    IF NEW.current_period_end IS NOT NULL OR NEW.cancelled_at IS NOT NULL OR NEW.plan_id IS NOT NULL THEN
      RAISE EXCEPTION 'billing_guard: client cannot set billing period fields on insert';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    old_status := OLD.subscription_status;

    IF NEW.is_subscribed IS DISTINCT FROM OLD.is_subscribed
       OR NEW.razorpay_subscription_id IS DISTINCT FROM OLD.razorpay_subscription_id
       OR NEW.razorpay_payment_id IS DISTINCT FROM OLD.razorpay_payment_id
       OR NEW.trial_end IS DISTINCT FROM OLD.trial_end
       OR NEW.trial_paid IS DISTINCT FROM OLD.trial_paid
       OR NEW.current_period_end IS DISTINCT FROM OLD.current_period_end
       OR NEW.cancelled_at IS DISTINCT FROM OLD.cancelled_at
       OR NEW.plan_id IS DISTINCT FROM OLD.plan_id
    THEN
      RAISE EXCEPTION 'billing_guard: client cannot mutate subscription billing fields';
    END IF;

    IF NEW.subscription_status IS DISTINCT FROM OLD.subscription_status THEN
      IF NOT (
        NEW.subscription_status = 'cancel_requested'
        AND LOWER(COALESCE(old_status, '')) IN ('active', 'trial')
      ) THEN
        RAISE EXCEPTION 'billing_guard: invalid subscription_status transition';
      END IF;
    END IF;

    RETURN NEW;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enforce_users_billing_ins ON public.users;
DROP TRIGGER IF EXISTS enforce_users_billing_upd ON public.users;

CREATE TRIGGER enforce_users_billing_ins
  BEFORE INSERT ON public.users
  FOR EACH ROW
  EXECUTE PROCEDURE public.enforce_users_billing_mutation_policy();

CREATE TRIGGER enforce_users_billing_upd
  BEFORE UPDATE ON public.users
  FOR EACH ROW
  EXECUTE PROCEDURE public.enforce_users_billing_mutation_policy();
