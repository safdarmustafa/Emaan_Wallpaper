-- Phase 2D: atomic idempotency store for Razorpay webhook deliveries.
-- Records each processed Razorpay event id exactly once so duplicate / retried
-- deliveries cannot re-apply subscription state. Service-role (edge function) only.
CREATE TABLE IF NOT EXISTS public.razorpay_webhook_events (
    event_id         TEXT PRIMARY KEY,
    event_type       TEXT,
    subscription_id  TEXT,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast lookups when debugging all events for one subscription.
CREATE INDEX IF NOT EXISTS idx_razorpay_webhook_events_subscription_id
    ON public.razorpay_webhook_events (subscription_id);

ALTER TABLE public.razorpay_webhook_events ENABLE ROW LEVEL SECURITY;

-- No client policies: only the service role (webhook edge function) may read/write,
-- matching public.otp_challenges. anon / authenticated JWTs get zero access.
