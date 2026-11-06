#!/usr/bin/env bash
set -euo pipefail

echo "==> Deploying subscription-related Supabase Edge Functions..."

FUNCTIONS=(
  "send-otp"
  "verify-otp"
  "delete-user-data"
  "create-subscription"
  "cancel-subscription"
  "subscription-status"
  "cashfree-webhook"
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
echo "  supabase secrets set CASHFREE_CLIENT_ID=..."
echo "  supabase secrets set CASHFREE_CLIENT_SECRET=..."
echo "  supabase secrets set CASHFREE_PLAN_ID=..."
echo "  supabase secrets set CASHFREE_ENV=..."
echo "  supabase secrets set SERVICE_ROLE_KEY=..."
echo "  supabase secrets set FAST2SMS_API_KEY=..."
echo ""
echo "Optional quick checks:"
echo "  supabase functions list"
echo "  supabase secrets list"
