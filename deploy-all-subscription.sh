#!/usr/bin/env bash
set -euo pipefail

echo "==> Deploying subscription-related Supabase Edge Functions..."

FUNCTIONS=(
  "send-otp"
  "verify-otp"
  "delete-user-data"
  "create-order"
  "verify-payment"
  "start-trial"
  "activate-trial"
  "create-subscription"
  "validate-subscription-status"
  "cancel-subscription"
  "razorpay-webhook"
  "expire-trials"
)

for fn in "${FUNCTIONS[@]}"; do
  echo ""
  echo "-> Deploying: ${fn}"
  supabase functions deploy "${fn}"
done

echo ""
echo "==> Deploy complete."
echo ""
echo "Required secrets (set if missing):"
echo "  supabase secrets set RAZORPAY_KEY_ID=..."
echo "  supabase secrets set RAZORPAY_KEY_SECRET=..."
echo "  supabase secrets set RAZORPAY_PLAN_ID=..."
echo "  supabase secrets set RAZORPAY_WEBHOOK_SECRET=..."
echo "  supabase secrets set SERVICE_ROLE_KEY=..."
echo "  supabase secrets set FAST2SMS_API_KEY=..."
echo ""
echo "Optional quick checks:"
echo "  supabase functions list"
echo "  supabase secrets list"
