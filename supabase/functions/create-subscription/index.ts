/**
 * create-subscription
 *
 * Creates a Cashfree subscription for an existing user and stores/updates a
 * pending row in public.subscriptions. Returns session fields for the Android SDK.
 *
 * Pending recovery: best-effort cancel of the previous Cashfree subscription,
 * then creates a NEW Cashfree subscription and updates that same DB row
 * (no duplicate rows).
 *
 * Does NOT activate premium or handle webhooks.
 * Active subscriptions that are still Premium return alreadyExists (no new
 * Cashfree create). An "active" row whose end_date has passed (₹249 never
 * collected) is renewed like pending recovery, after its old mandate is
 * cancelled — so the user can pay again.
 */

import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

import {
  cancelCashfreeSubscription,
  cashfreeFetch,
  isNonActionableCancelError,
} from "../_shared/cashfree.ts";
import { handleCorsPreflight } from "../_shared/cors.ts";
import { requireEnv } from "../_shared/env.ts";
import { ERROR_CODES } from "../_shared/errors.ts";
import { computeIsPremium } from "../_shared/premium.ts";
import { errorResponse, successResponse } from "../_shared/response.ts";
import { validateIndianPhone } from "../_shared/validation.ts";

/** Only local plan currently offered; Cashfree plan comes from CASHFREE_PLAN_ID. */
const LOCAL_PLAN_TYPE = "monthly";

/** Cashfree UPI Autopay FCD cutoff in IST (hour, 24h). Before → T+1; at/after → T+2. */
const UPI_FIRST_CHARGE_CUTOFF_HOUR_IST = 21;

/** Extra minutes added to mirrored local time so FCD is safely after auth. */
const FIRST_CHARGE_SAFETY_BUFFER_MINUTES = 2;

/** Fixed IST offset (Asia/Kolkata has no DST). */
const IST_OFFSET_MS = (5 * 60 + 30) * 60 * 1000;

interface CreateSubscriptionBody {
  phone?: unknown;
}

/**
 * PERIODIC subscription_first_charge_time (FCD) in Asia/Kolkata.
 * Mirrors current IST wall-clock onto T+1 or T+2 per Cashfree UPI rules,
 * then adds a small safety buffer.
 * @see https://www.cashfree.com/docs/payments/subscription/create
 */
function calculateSubscriptionFirstChargeTime(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());

  const value = (type: Intl.DateTimeFormatPartTypes): number => {
    const part = parts.find((p) => p.type === type)?.value;
    if (!part) {
      throw new Error(`Unable to resolve IST ${type}`);
    }
    return Number(part);
  };

  const year = value("year");
  const month = value("month");
  const day = value("day");
  const hour = value("hour");
  const minute = value("minute");
  const second = value("second");

  const daysToAdd = hour < UPI_FIRST_CHARGE_CUTOFF_HOUR_IST ? 1 : 2;

  // Interpret IST wall time as UTC components, then shift by IST offset.
  let utcMs =
    Date.UTC(year, month - 1, day, hour, minute, second) - IST_OFFSET_MS;
  utcMs += daysToAdd * 24 * 60 * 60 * 1000;
  utcMs += FIRST_CHARGE_SAFETY_BUFFER_MINUTES * 60 * 1000;

  // Convert back to IST wall components for +05:30 ISO8601.
  const ist = new Date(utcMs + IST_OFFSET_MS);
  const y = ist.getUTCFullYear();
  const m = String(ist.getUTCMonth() + 1).padStart(2, "0");
  const d = String(ist.getUTCDate()).padStart(2, "0");
  const h = String(ist.getUTCHours()).padStart(2, "0");
  const mi = String(ist.getUTCMinutes()).padStart(2, "0");
  const s = String(ist.getUTCSeconds()).padStart(2, "0");
  return `${y}-${m}-${d}T${h}:${mi}:${s}+05:30`;
}

function nowIso(): string {
  return new Date().toISOString();
}

/** Reads CASHFREE_AUTHORIZATION_AMOUNT; rejects missing/non-numeric/negative values. */
function requireAuthorizationAmount(): number {
  const authorizationAmount = Number(
    requireEnv("CASHFREE_AUTHORIZATION_AMOUNT"),
  );
  if (!Number.isFinite(authorizationAmount) || authorizationAmount < 0) {
    throw new Error(
      "Invalid CASHFREE_AUTHORIZATION_AMOUNT: expected a non-negative number",
    );
  }
  return authorizationAmount;
}

interface CashfreeSubscriptionResponse {
  subscription_id?: string;
  cf_subscription_id?: string;
  subscription_session_id?: string;
  subscription_status?: string;
}

interface CreatedCashfreeSubscription {
  subscriptionId: string;
  sessionId: string;
  firstChargeTime: string;
  cfSubscriptionId?: string;
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

async function createCashfreeSubscription(params: {
  userId: string | number;
  userPhone: string;
  userName: string | null;
  planId: string;
  authorizationAmount: number;
}): Promise<CreatedCashfreeSubscription> {
  const subscriptionId = `emaan_${params.userId}_${crypto.randomUUID()}`;
  const idempotencyKey = crypto.randomUUID();
  const firstChargeTime = calculateSubscriptionFirstChargeTime();

  const customerDetails: Record<string, string> = {
    customer_phone: params.userPhone,
    customer_email: `${params.userPhone}@emaan.app`,
  };
  if (params.userName && params.userName.trim().length > 0) {
    customerDetails.customer_name = params.userName.trim();
  }

  console.info("create-subscription calling Cashfree", {
    userId: params.userId,
    subscriptionId,
  });

  const cashfreeResponse = await cashfreeFetch<CashfreeSubscriptionResponse>(
    "/subscriptions",
    "POST",
    {
      subscription_id: subscriptionId,
      customer_details: customerDetails,
      plan_details: {
        plan_id: params.planId,
      },
      authorization_details: {
        authorization_amount: params.authorizationAmount,
        authorization_amount_refund: false,
      },
      subscription_meta: {
        return_url: "emaan://subscription/success",
      },
      subscription_first_charge_time: firstChargeTime,
    },
    {
      "x-request-id": idempotencyKey,
      "x-idempotency-key": idempotencyKey,
    },
  );

  const returnedSubscriptionId =
    cashfreeResponse.subscription_id ?? subscriptionId;
  const sessionId = cashfreeResponse.subscription_session_id;

  if (!sessionId) {
    console.error("create-subscription missing subscription_session_id", {
      userId: params.userId,
      subscriptionId: returnedSubscriptionId,
      cfSubscriptionId: cashfreeResponse.cf_subscription_id,
      subscriptionStatus: cashfreeResponse.subscription_status,
    });
    throw new Error("Cashfree response missing subscription_session_id");
  }

  return {
    subscriptionId: returnedSubscriptionId,
    sessionId,
    firstChargeTime,
    cfSubscriptionId: cashfreeResponse.cf_subscription_id,
  };
}

Deno.serve(async (req) => {
  const preflight = handleCorsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== "POST") {
    return errorResponse(ERROR_CODES.METHOD_NOT_ALLOWED);
  }

  let body: CreateSubscriptionBody;
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
    const planId = requireEnv("CASHFREE_PLAN_ID");
    requireEnv("CASHFREE_CLIENT_ID");
    requireEnv("CASHFREE_CLIENT_SECRET");
    const authorizationAmount = requireAuthorizationAmount();

    const supabase = createServiceClient();

    // Resolve the application user via the existing phone-based auth lookup.
    const { data: user, error: lookupError } = await supabase
      .from("users")
      .select("id, phone_number, first_name, last_name")
      .eq("phone_number", phoneResult.normalized)
      .maybeSingle();

    if (lookupError) {
      console.error("create-subscription user lookup failed", lookupError.message);
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!user) {
      return errorResponse(
        ERROR_CODES.BAD_REQUEST,
        "No user found for this phone number",
      );
    }

    // After lookup, user.id is the canonical application identity.
    const userId = user.id as string | number;
    const userPhone = user.phone_number as string;
    const fullName = [user.first_name, user.last_name]
      .filter((part): part is string => typeof part === "string" && part.trim().length > 0)
      .join(" ");
    const userName = fullName.length > 0 ? fullName : null;

    const { data: existing, error: existingError } = await supabase
      .from("subscriptions")
      .select("id, status, cashfree_subscription_id, end_date")
      .eq("user_id", userId)
      .in("status", ["active", "pending", "cancelled"])
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (existingError) {
      console.error(
        "create-subscription existing subscription lookup failed",
        { userId, message: existingError.message },
      );
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    // Active: check whether premium is still valid, then verify with Cashfree.
    if (existing && existing.status === "active") {
      const stillPremium = computeIsPremium(existing.status, existing.end_date);

      if (stillPremium) {
        // Within paid period — Option A: check Cashfree in case the user cancelled
        // their UPI mandate in PhonePe/Paytm. Cashfree only fires
        // SUBSCRIPTION_STATUS_CHANGED on the next failed debit, so our DB lags.
        let confirmedActive = true;

        const cashfreeSubId =
          typeof existing.cashfree_subscription_id === "string"
            ? existing.cashfree_subscription_id.trim()
            : "";

        if (cashfreeSubId) {
          try {
            const cfSub = await cashfreeFetch<{ subscription_status?: string }>(
              `/subscriptions/${cashfreeSubId}`,
              "GET",
            );
            const cfStatus =
              typeof cfSub.subscription_status === "string"
                ? cfSub.subscription_status.trim().toUpperCase()
                : "";

            if (cfStatus === "CUSTOMER_CANCELLED" || cfStatus === "CANCELLED") {
              console.info(
                "create-subscription active→cancelled sync: Cashfree mandate cancelled",
                { userId, cashfreeSubId, cfStatus },
              );

              const { error: syncError } = await supabase
                .from("subscriptions")
                .update({
                  status: "cancelled",
                  autopay_enabled: false,
                  updated_at: nowIso(),
                })
                .eq("id", existing.id);

              if (syncError) {
                console.warn(
                  "create-subscription active→cancelled sync DB write failed, proceeding",
                  { userId, message: syncError.message },
                );
              }

              existing.status = "cancelled";
              confirmedActive = false;
            } else {
              console.info("create-subscription Cashfree status confirmed active", {
                userId,
                cashfreeSubId,
                cfStatus,
              });
            }
          } catch (fetchError) {
            const detail =
              fetchError instanceof Error
                ? fetchError.message
                : String(fetchError);
            console.warn(
              "create-subscription Cashfree status check failed, treating as active",
              { userId, cashfreeSubId, detail },
            );
          }
        }

        if (confirmedActive) {
          console.info("create-subscription blocked: existing active subscription", {
            userId,
          });
          return successResponse("An active subscription already exists.", {
            alreadyExists: true,
            status: "active",
            subscription_id: existing.cashfree_subscription_id,
          });
        }
        // confirmedActive = false → Cashfree mandate cancelled, fall through to recovery.
      }
      // !stillPremium → lapsed active: end_date has passed, treat like pending recovery.
    }

    // Lapsed active (end_date passed): cancel the old mandate before creating a new one
    // so the user is never double-charged.
    const isLapsedActive = existing !== null && existing.status === "active";
    if (isLapsedActive) {
      const previousCashfreeSubscriptionId =
        typeof existing.cashfree_subscription_id === "string"
          ? existing.cashfree_subscription_id.trim()
          : "";
      if (previousCashfreeSubscriptionId) {
        const cleanup = await cancelCashfreeSubscription(
          previousCashfreeSubscriptionId,
        );
        if (!cleanup.success && !isNonActionableCancelError(cleanup.detail)) {
          console.error("create-subscription lapsed cancel failed; not renewing", {
            userId,
            subscriptionRowId: existing.id,
            previousCashfreeSubscriptionId,
            detail: cleanup.detail,
          });
          return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
        }
        console.info("create-subscription lapsed mandate cancelled", {
          userId,
          subscriptionRowId: existing.id,
          previousCashfreeSubscriptionId,
        });
      }
    }

    // Pending / cancelled recovery: best-effort cancel of the OLD Cashfree subscription first.
    if (existing && (existing.status === "pending" || existing.status === "cancelled")) {
      const previousCashfreeSubscriptionId =
        typeof existing.cashfree_subscription_id === "string"
          ? existing.cashfree_subscription_id.trim()
          : "";

      if (!previousCashfreeSubscriptionId) {
        console.info("create-subscription pending cleanup skipped", {
          userId,
          subscriptionRowId: existing.id,
          reason: "missing cashfree_subscription_id",
        });
      } else {
        console.info("create-subscription pending cleanup attempted", {
          userId,
          subscriptionRowId: existing.id,
          previousCashfreeSubscriptionId,
        });

        const cleanup = await cancelCashfreeSubscription(
          previousCashfreeSubscriptionId,
        );

        if (cleanup.skipped) {
          console.info("create-subscription pending cleanup skipped", {
            userId,
            subscriptionRowId: existing.id,
            previousCashfreeSubscriptionId,
            detail: cleanup.detail,
          });
        } else if (cleanup.success) {
          console.info("create-subscription pending cleanup success", {
            userId,
            subscriptionRowId: existing.id,
            previousCashfreeSubscriptionId,
          });
        } else {
          // Best effort — never block checkout recovery on cleanup failure.
          console.warn("create-subscription pending cleanup failed", {
            userId,
            subscriptionRowId: existing.id,
            previousCashfreeSubscriptionId,
            detail: cleanup.detail,
          });
        }
      }
    }

    let created: CreatedCashfreeSubscription;
    try {
      created = await createCashfreeSubscription({
        userId,
        userPhone,
        userName,
        planId,
        authorizationAmount,
      });
    } catch (cashfreeError) {
      const detail = cashfreeError instanceof Error
        ? cashfreeError.message
        : String(cashfreeError);
      console.error("create-subscription Cashfree create failed", {
        userId,
        detail,
      });
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    // Pending / cancelled / lapsed recovery: update the SAME row with the new
    // Cashfree subscription. end_date is left as-is for lapsed rows.
    if (existing && (existing.status === "pending" || existing.status === "cancelled" || isLapsedActive)) {
      const { error: updateError } = await supabase
        .from("subscriptions")
        .update({
          cashfree_subscription_id: created.subscriptionId,
          status: "pending",
          updated_at: nowIso(),
          next_billing_date: created.firstChargeTime,
        })
        .eq("id", existing.id);

      if (updateError) {
        console.error(
          "create-subscription pending row update failed after Cashfree create",
          {
            userId,
            subscriptionRowId: existing.id,
            previousCashfreeSubscriptionId: existing.cashfree_subscription_id,
            cashfreeSubscriptionId: created.subscriptionId,
            cfSubscriptionId: created.cfSubscriptionId,
            message: updateError.message,
          },
        );
        return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
      }

      console.info("create-subscription pending/cancelled recovered", {
        userId,
        subscriptionRowId: existing.id,
        previousCashfreeSubscriptionId: existing.cashfree_subscription_id,
        subscriptionId: created.subscriptionId,
      });

      return successResponse("Subscription checkout session refreshed.", {
        subscription_id: created.subscriptionId,
        subscription_session_id: created.sessionId,
      });
    }

    // Fresh user: insert a new pending row.
    const { error: insertError } = await supabase.from("subscriptions").insert({
      user_id: userId,
      phone: userPhone,
      plan_type: LOCAL_PLAN_TYPE,
      status: "pending",
      cashfree_subscription_id: created.subscriptionId,
      // First ₹249 debit time; the trial end_date is derived from it.
      next_billing_date: created.firstChargeTime,
    });

    if (insertError) {
      // Cashfree already created the subscription; log ids for manual reconciliation.
      console.error(
        "create-subscription insert failed after Cashfree create",
        {
          userId,
          cashfreeSubscriptionId: created.subscriptionId,
          cfSubscriptionId: created.cfSubscriptionId,
          message: insertError.message,
        },
      );
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    console.info("create-subscription created", {
      userId,
      subscriptionId: created.subscriptionId,
    });

    return successResponse("Subscription created successfully.", {
      subscription_id: created.subscriptionId,
      subscription_session_id: created.sessionId,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("create-subscription unexpected error", detail);
    return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
  }
});
