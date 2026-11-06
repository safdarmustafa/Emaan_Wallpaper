/**
 * subscription-status
 *
 * Read-only Premium status for an existing application user.
 * Android must use this endpoint to determine whether Premium is active.
 *
 * Does NOT create or update subscriptions.
 * Does NOT call Cashfree.
 * Does NOT activate premium.
 *
 * Premium eligibility (read-time only, see _shared/premium.ts):
 *   - active / cancelled AND now < end_date → Premium
 *   - otherwise (incl. missing end_date) → not Premium
 */

import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

import { handleCorsPreflight } from "../_shared/cors.ts";
import { requireEnv } from "../_shared/env.ts";
import { ERROR_CODES } from "../_shared/errors.ts";
import { computeIsPremium } from "../_shared/premium.ts";
import { errorResponse, successResponse } from "../_shared/response.ts";
import { validateIndianPhone } from "../_shared/validation.ts";

interface SubscriptionStatusBody {
  phone?: unknown;
}

function createServiceClient() {
  return createClient(
    requireEnv("SUPABASE_URL"),
    requireEnv("SUPABASE_SERVICE_ROLE_KEY"),
    {
      auth: { persistSession: false, autoRefreshToken: false },
    },
  );
}

Deno.serve(async (req) => {
  const preflight = handleCorsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== "POST") {
    return errorResponse(ERROR_CODES.METHOD_NOT_ALLOWED);
  }

  let body: SubscriptionStatusBody;
  try {
    body = await req.json();
  } catch {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Invalid JSON body");
  }

  const phoneResult = validateIndianPhone(body.phone);
  if (!phoneResult.valid || !phoneResult.normalized) {
    return errorResponse(ERROR_CODES.PHONE_INVALID);
  }

  try {
    const supabase = createServiceClient();

    // Resolve the application user via the existing phone-based auth lookup.
    const { data: user, error: lookupError } = await supabase
      .from("users")
      .select("id, phone_number")
      .eq("phone_number", phoneResult.normalized)
      .maybeSingle();

    if (lookupError) {
      console.error("subscription-status user lookup failed", lookupError.message);
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!user) {
      return errorResponse(
        ERROR_CODES.BAD_REQUEST,
        "No user found for this phone number",
      );
    }

    const userId = user.id as string | number;

    const { data: subscription, error: subscriptionError } = await supabase
      .from("subscriptions")
      .select(
        "status, plan_type, start_date, end_date, next_billing_date, autopay_enabled",
      )
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (subscriptionError) {
      console.error("subscription-status subscription lookup failed", {
        userId,
        message: subscriptionError.message,
      });
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!subscription) {
      console.info("subscription-status no subscription", { userId });
      return successResponse("Subscription status retrieved.", {
        isPremium: false,
        status: null,
        plan_type: null,
        start_date: null,
        end_date: null,
        next_billing_date: null,
        autopay_enabled: false,
      });
    }

    const status = subscription.status as string;
    const isPremium = computeIsPremium(status, subscription.end_date);

    console.info("subscription-status retrieved", {
      userId,
      status,
      isPremium,
      endDate: subscription.end_date,
    });

    return successResponse("Subscription status retrieved.", {
      isPremium,
      status,
      plan_type: subscription.plan_type,
      start_date: subscription.start_date,
      end_date: subscription.end_date,
      next_billing_date: subscription.next_billing_date,
      autopay_enabled: subscription.autopay_enabled ?? false,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("subscription-status unexpected error", detail);
    return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
  }
});
