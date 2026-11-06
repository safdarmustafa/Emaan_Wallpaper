/**
 * cashfree-webhook
 *
 * Single source of truth for subscription lifecycle updates from Cashfree.
 * Updates existing public.subscriptions rows only.
 *
 * Does NOT create subscriptions or users.
 * Does NOT activate premium from the client.
 *
 * Premium time lives in end_date (see _shared/premium.ts):
 *   ₹3 AUTH success    → trial until first ₹499 charge + grace
 *   ₹499 CHARGE success → until next billing date + grace
 *   failures            → logged only; end_date simply runs out
 */

import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

import { handleCorsPreflight } from "../_shared/cors.ts";
import { requireEnv } from "../_shared/env.ts";
import { ERROR_CODES } from "../_shared/errors.ts";
import {
  addOneCalendarMonth,
  chargeDayEndWithGrace,
  FALLBACK_TRIAL_MS,
  laterOf,
  MONTHLY_CHARGE_AMOUNT,
  parseDate,
  PREMIUM_GRACE_MS,
} from "../_shared/premium.ts";
import { errorResponse, successResponse } from "../_shared/response.ts";

type LocalSubscriptionStatus = "pending" | "active" | "cancelled" | "expired";

interface WebhookEnvelope {
  type?: unknown;
  event_time?: unknown;
  data?: unknown;
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

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function asString(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/** Maps Cashfree subscription_status values onto local subscription_status. */
function mapCashfreeStatus(status: string): LocalSubscriptionStatus | null {
  switch (status.trim().toUpperCase()) {
    case "ACTIVE":
      return "active";
    case "CANCELLED":
    case "CUSTOMER_CANCELLED":
      return "cancelled";
    case "EXPIRED":
    case "CARD_EXPIRED":
    case "LINK_EXPIRED":
    case "COMPLETED":
      return "expired";
    case "INITIALIZED":
    case "BANK_APPROVAL_PENDING":
      return "pending";
    default:
      return null;
  }
}

/**
 * Merchant subscription_id is stored in subscriptions.cashfree_subscription_id
 * by create-subscription. Prefer that field from nested or flat webhook data.
 */
function extractMerchantSubscriptionId(
  data: Record<string, unknown>,
): string | null {
  const direct = asString(data.subscription_id);
  if (direct) return direct;

  const details = asRecord(data.subscription_details);
  if (details) {
    const nested = asString(details.subscription_id);
    if (nested) return nested;
  }

  return null;
}

function extractCashfreeSubscriptionStatus(
  data: Record<string, unknown>,
): string | null {
  const details = asRecord(data.subscription_details);
  if (details) {
    const nested = asString(details.subscription_status);
    if (nested) return nested;
  }
  return asString(data.subscription_status);
}

function extractNextBillingDate(
  data: Record<string, unknown>,
): string | null {
  const details = asRecord(data.subscription_details);
  const candidates = [
    details ? details.next_schedule_date : null,
    data.next_schedule_date,
    data.payment_schedule_date,
  ];

  for (const candidate of candidates) {
    const value = asString(candidate);
    if (value) return value;
  }
  return null;
}

function extractPaymentId(data: Record<string, unknown>): string | null {
  const paymentId = asString(data.payment_id);
  if (paymentId) return paymentId;

  if (typeof data.cf_payment_id === "string" || typeof data.cf_payment_id === "number") {
    return String(data.cf_payment_id);
  }

  const auth = asRecord(data.authorization_details);
  if (auth) {
    const authPaymentId = asString(auth.payment_id);
    if (authPaymentId) return authPaymentId;
  }

  return null;
}

function nowIso(): string {
  return new Date().toISOString();
}

type PaymentKind = "AUTH" | "CHARGE";

/**
 * ₹3 authorization vs ₹499 recurring charge. Prefers Cashfree payment_type;
 * falls back to the amount only when payment_type is missing / unknown.
 */
function classifyPayment(data: Record<string, unknown>): PaymentKind | null {
  const type = asString(data.payment_type)?.toUpperCase();
  if (type === "AUTH") return "AUTH";
  if (type === "CHARGE") return "CHARGE";
  const amount = Number(data.payment_amount);
  if (!Number.isFinite(amount)) return null;
  return amount >= MONTHLY_CHARGE_AMOUNT ? "CHARGE" : "AUTH";
}

function extractPaymentDate(data: Record<string, unknown>): Date | null {
  return parseDate(data.payment_initiated_date) ??
    parseDate(data.payment_schedule_date);
}

/** A next_schedule_date this close to the charge has not advanced yet. */
const MIN_NEXT_CYCLE_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Trial paid-through for a ₹3 authorization: end of the first ₹499 charge day
 * (IST) + grace. Without a usable first-charge date, a 1-day trial + grace.
 */
function computeTrialEndDate(firstCharge: Date | null, now: Date): Date {
  if (firstCharge) {
    const trialEnd = chargeDayEndWithGrace(firstCharge);
    if (trialEnd.getTime() > now.getTime()) return trialEnd;
  }
  return new Date(now.getTime() + FALLBACK_TRIAL_MS + PREMIUM_GRACE_MS);
}

/**
 * Paid-through for a successful ₹499 charge: end of Cashfree's next scheduled
 * debit day + grace. If next_schedule_date has not moved to the next cycle yet,
 * one calendar month after the charge instead — never "tomorrow".
 */
function computeChargeEndDate(nextBilling: Date | null, paidAt: Date): Date {
  if (
    nextBilling &&
    nextBilling.getTime() - paidAt.getTime() >= MIN_NEXT_CYCLE_MS
  ) {
    return chargeDayEndWithGrace(nextBilling);
  }
  return chargeDayEndWithGrace(addOneCalendarMonth(paidAt));
}

/** Terminal local states a late / out-of-order event must not reopen. */
function isTerminalStatus(status: unknown): boolean {
  return status === "cancelled" || status === "expired";
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * Cashfree Subscriptions webhook signature verification.
 * signatureData = x-webhook-timestamp + rawBody
 * HMAC-SHA256 with CASHFREE_CLIENT_SECRET, Base64-encoded.
 */
async function verifyCashfreeWebhookSignature(
  rawBody: string,
  signatureHeader: string | null,
  timestampHeader: string | null,
): Promise<boolean> {
  if (!signatureHeader || !timestampHeader) {
    return false;
  }

  const secretKey = requireEnv("CASHFREE_CLIENT_SECRET");
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secretKey),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const digest = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(timestampHeader + rawBody),
  );
  const computedSignature = bytesToBase64(new Uint8Array(digest));
  return computedSignature === signatureHeader;
}

Deno.serve(async (req) => {
  const preflight = handleCorsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== "POST") {
    return errorResponse(ERROR_CODES.METHOD_NOT_ALLOWED);
  }

  // Raw body must be used for signature verification before JSON parsing.
  let rawBody: string;
  try {
    rawBody = await req.text();
  } catch {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Invalid request body");
  }

  const signatureValid = await verifyCashfreeWebhookSignature(
    rawBody,
    req.headers.get("x-webhook-signature"),
    req.headers.get("x-webhook-timestamp"),
  );
  if (!signatureValid) {
    console.error("cashfree-webhook invalid signature");
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Invalid webhook signature");
  }

  let envelope: WebhookEnvelope;
  try {
    envelope = JSON.parse(rawBody) as WebhookEnvelope;
  } catch {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Invalid JSON body");
  }

  const eventType = asString(envelope.type);
  if (!eventType) {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Missing webhook type");
  }

  const data = asRecord(envelope.data);
  if (!data) {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Missing webhook data");
  }

  const merchantSubscriptionId = extractMerchantSubscriptionId(data);
  if (!merchantSubscriptionId) {
    return errorResponse(
      ERROR_CODES.BAD_REQUEST,
      "Missing subscription_id",
    );
  }

  try {
    const supabase = createServiceClient();

    const { data: subscription, error: lookupError } = await supabase
      .from("subscriptions")
      .select(
        "id, status, cashfree_subscription_id, plan_type, end_date, next_billing_date, last_payment_id",
      )
      .eq("cashfree_subscription_id", merchantSubscriptionId)
      .maybeSingle();

    if (lookupError) {
      console.error("cashfree-webhook subscription lookup failed", {
        message: lookupError.message,
      });
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (!subscription) {
      console.info("cashfree-webhook subscription not found", {
        eventType,
        cashfreeSubscriptionId: merchantSubscriptionId,
      });
      // Acknowledge so Cashfree does not retry forever for unknown ids.
      return successResponse("Webhook acknowledged.", {
        processed: false,
        reason: "subscription_not_found",
      });
    }

    const subscriptionRowId = subscription.id as number;
    let updates: Record<string, unknown> | null = null;

    const existingEnd = parseDate(subscription.end_date);

    if (eventType === "SUBSCRIPTION_PAYMENT_SUCCESS") {
      const processedAt = new Date();
      const paymentId = extractPaymentId(data);

      // Cashfree retries webhooks: the same payment must not extend end_date twice.
      if (paymentId && paymentId === subscription.last_payment_id) {
        console.info("cashfree-webhook duplicate payment ignored", {
          subscriptionRowId,
          paymentId,
        });
        return successResponse("Webhook acknowledged.", {
          processed: false,
          reason: "duplicate_payment",
        });
      }

      const kind = classifyPayment(data);
      if (!kind) {
        console.warn("cashfree-webhook payment kind unknown; not extending", {
          subscriptionRowId,
          paymentId,
          paymentType: data.payment_type,
          paymentAmount: data.payment_amount,
        });
        return successResponse("Webhook acknowledged.", {
          processed: false,
          reason: "unknown_payment_kind",
        });
      }

      const nextBilling = extractNextBillingDate(data);
      updates = {
        autopay_enabled: true,
        updated_at: processedAt.toISOString(),
      };
      if (paymentId) {
        updates.last_payment_id = paymentId;
      }
      if (nextBilling) {
        updates.next_billing_date = nextBilling;
      }

      let computedEnd: Date;
      if (kind === "CHARGE") {
        // ₹499 collected: always (re)activate and extend to the next billing date.
        updates.status = "active" satisfies LocalSubscriptionStatus;
        computedEnd = computeChargeEndDate(
          parseDate(nextBilling),
          extractPaymentDate(data) ?? processedAt,
        );
      } else {
        // ₹3 authorization: trial only, until the first ₹499 charge (+ grace).
        if (!isTerminalStatus(subscription.status)) {
          updates.status = "active" satisfies LocalSubscriptionStatus;
        }
        computedEnd = computeTrialEndDate(
          parseDate(nextBilling) ?? parseDate(subscription.next_billing_date),
          processedAt,
        );
      }
      // Never shorten an already-earned paid-through date.
      updates.end_date = (laterOf(existingEnd, computedEnd) ?? computedEnd)
        .toISOString();

      console.info("cashfree-webhook payment success", {
        subscriptionRowId,
        kind,
        paymentId,
        amount: data.payment_amount,
        endDate: updates.end_date,
      });
    } else if (eventType === "SUBSCRIPTION_STATUS_CHANGED") {
      const cashfreeStatus = extractCashfreeSubscriptionStatus(data);
      if (!cashfreeStatus) {
        return errorResponse(
          ERROR_CODES.BAD_REQUEST,
          "Missing subscription_status",
        );
      }

      const localStatus = mapCashfreeStatus(cashfreeStatus);
      if (!localStatus) {
        console.info("cashfree-webhook ignoring unmapped status", {
          eventType,
          cashfreeStatus,
          subscriptionRowId,
        });
        return successResponse("Webhook acknowledged.", {
          processed: false,
          reason: "unmapped_status",
          cashfreeStatus,
        });
      }

      // Cancelled / expired mandates cannot resume (Cashfree docs); a late or
      // replayed ACTIVE must not reopen them.
      if (localStatus === "active" && isTerminalStatus(subscription.status)) {
        console.info("cashfree-webhook ignoring ACTIVE for terminal row", {
          subscriptionRowId,
          currentStatus: subscription.status,
        });
        return successResponse("Webhook acknowledged.", {
          processed: false,
          reason: "terminal_status",
        });
      }

      updates = {
        status: localStatus,
        updated_at: nowIso(),
      };

      if (localStatus === "active") {
        updates.autopay_enabled = true;
        const nextBilling = extractNextBillingDate(data);
        if (nextBilling) {
          updates.next_billing_date = nextBilling;
        }
        // Mandate approved but no payment webhook yet: start the trial once.
        // Never extends an existing end_date — only payments do that.
        if (!existingEnd) {
          updates.end_date = computeTrialEndDate(
            parseDate(nextBilling) ?? parseDate(subscription.next_billing_date),
            new Date(),
          ).toISOString();
        }
      } else if (localStatus === "cancelled" || localStatus === "expired") {
        updates.autopay_enabled = false;
      }
    } else if (
      eventType === "SUBSCRIPTION_PAYMENT_FAILED" ||
      eventType === "SUBSCRIPTION_PAYMENT_CANCELLED"
    ) {
      // No write: UPI retries may still succeed, and Premium lapses on its own
      // when end_date passes without a successful charge.
      const failure = asRecord(data.failure_details);
      console.warn("cashfree-webhook payment not collected", {
        eventType,
        subscriptionRowId,
        paymentId: extractPaymentId(data),
        paymentType: data.payment_type,
        amount: data.payment_amount,
        reason: failure ? failure.failure_reason : null,
      });
      return successResponse("Webhook acknowledged.", {
        processed: false,
        reason: "payment_not_collected",
        eventType,
      });
    } else {
      console.info("cashfree-webhook ignoring event type", {
        eventType,
        subscriptionRowId,
      });
      return successResponse("Webhook acknowledged.", {
        processed: false,
        reason: "ignored_event_type",
        eventType,
      });
    }

    const { error: updateError } = await supabase
      .from("subscriptions")
      .update(updates)
      .eq("id", subscriptionRowId);

    if (updateError) {
      console.error("cashfree-webhook update failed", {
        subscriptionRowId,
        eventType,
        message: updateError.message,
      });
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    console.info("cashfree-webhook processed", {
      subscriptionRowId,
      eventType,
      status: updates.status,
    });

    return successResponse("Webhook processed successfully.", {
      processed: true,
      eventType,
      status: updates.status,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("cashfree-webhook unexpected error", detail);
    return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
  }
});
