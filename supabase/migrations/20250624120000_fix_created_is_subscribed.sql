-- Users with Razorpay subscription in "created" (mandate not complete) must not retain premium.
UPDATE public.users
SET is_subscribed = false
WHERE is_subscribed = true
  AND lower(coalesce(subscription_status, '')) IN ('created', 'authenticated', 'pending');
