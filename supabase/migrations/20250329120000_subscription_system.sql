-- Subscription + Razorpay fields (idempotent)
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_subscribed boolean NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS subscription_status text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS razorpay_subscription_id text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS razorpay_payment_id text;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS trial_end timestamptz;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS trial_paid boolean NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS current_period_end timestamptz;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS cancelled_at timestamptz;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS plan_id text;

COMMENT ON COLUMN public.users.subscription_status IS 'created | trial | active | cancel_requested | cancelled | expired';
COMMENT ON COLUMN public.users.razorpay_payment_id IS 'Last ₹5 entry-fee payment id from Razorpay';
