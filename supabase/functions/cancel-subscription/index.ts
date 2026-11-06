/**
 * cancel-subscription
 *
 * Submits a Cashfree CANCEL (Manage Subscription) for the user's latest
 * ACTIVE subscription. Does NOT write subscription state — the webhook is
 * the only writer. Android refreshes via subscription-status afterward.
 *
 * Idempotent: no ACTIVE subscription → success with alreadyCancelled.
 * Also treats Cashfree "already cancelled / cancelling" errors as success
 * (alreadyCancelling) so retries during the webhook delay window are safe.
 */

import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

import {
  cancelCashfreeSubscription,
  isNonActionableCancelError,
} from "../_shared/cashfree.ts";
import { handleCorsPreflight } from "../_shared/cors.ts";
import { requireEnv } from "../_shared/env.ts";
import { ERROR_CODES } from "../_shared/errors.ts";
import { errorResponse, successResponse } from "../_shared/response.ts";
import { validateIndianPhone } from "../_shared/validation.ts";

interface CancelSubscriptionBody {
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

  let body: CancelSubscriptionBody;
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
    requireEnv("CASHFREE_CLIENT_ID");
    requireEnv("CASHFREE_CLIENT_SECRET");

    const supabase = createServiceClient();

    // Resolve the application user via the existing phone-based auth lookup.
    const { data: user, error: lookupError } = await supabase
      .from("users")
      .select("id, phone_number")
      .eq("phone_number", phoneResult.normalized)
      .maybeSingle();

    if (lookupError) {
      console.error("cancel-subscription user lookup failed", lookupError.message);
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!user) {
      return errorResponse(
        ERROR_CODES.BAD_REQUEST,
        "No user found for this phone number",
      );
    }

    const userId = user.id as string | number;

    // Latest ACTIVE only — never pending or expired.
    const { data: subscription, error: subscriptionError } = await supabase
      .from("subscriptions")
      .select("id, status, cashfree_subscription_id")
      .eq("user_id", userId)
      .eq("status", "active")
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (subscriptionError) {
      console.error("cancel-subscription active subscription lookup failed", {
        userId,
        message: subscriptionError.message,
      });
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!subscription) {
      console.info("cancel-subscription already cancelled (no active)", {
        userId,
      });
      return successResponse("Subscription is already cancelled.", {
        alreadyCancelled: true,
      });
    }

    const cashfreeSubscriptionId =
      typeof subscription.cashfree_subscription_id === "string"
        ? subscription.cashfree_subscription_id.trim()
        : "";

    if (!cashfreeSubscriptionId) {
      // Nothing to cancel at Cashfree — treat as idempotent success.
      console.info("cancel-subscription already cancelled (missing cashfree id)", {
        userId,
        subscriptionRowId: subscription.id,
      });
      return successResponse("Subscription is already cancelled.", {
        alreadyCancelled: true,
      });
    }

    console.info("cancel-subscription calling Cashfree CANCEL", {
      userId,
      subscriptionRowId: subscription.id,
      cashfreeSubscriptionId,
    });

    const cancelResult = await cancelCashfreeSubscription(cashfreeSubscriptionId);

    if (cancelResult.skipped) {
      console.info("cancel-subscription skipped", {
        userId,
        subscriptionRowId: subscription.id,
        cashfreeSubscriptionId,
        detail: cancelResult.detail,
      });
      return successResponse("Subscription is already cancelled.", {
        alreadyCancelled: true,
      });
    }

    if (!cancelResult.success) {
      // Webhook delay window: Cashfree may already be cancelled / cancelling.
      if (isNonActionableCancelError(cancelResult.detail)) {
        console.info("cancel-subscription Cashfree already cancelling", {
          userId,
          subscriptionRowId: subscription.id,
          cashfreeSubscriptionId,
          detail: cancelResult.detail,
        });
        return successResponse("Cancellation request already submitted.", {
          alreadyCancelling: true,
        });
      }

      console.error("cancel-subscription Cashfree CANCEL failed", {
        userId,
        subscriptionRowId: subscription.id,
        cashfreeSubscriptionId,
        detail: cancelResult.detail,
      });
      return errorResponse(
        ERROR_CODES.INTERNAL_SERVER_ERROR,
        cancelResult.detail ?? "Cashfree CANCEL failed",
      );
    }

    // Do NOT update subscriptions — webhook owns status / autopay / dates.
    console.info("cancel-subscription Cashfree CANCEL submitted", {
      userId,
      subscriptionRowId: subscription.id,
      cashfreeSubscriptionId,
    });

    return successResponse("Cancellation request submitted.");
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("cancel-subscription unexpected error", detail);
    return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
  }
});
