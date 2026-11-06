/**
 * Reusable Cashfree Subscriptions HTTP client for Emaan Edge Functions.
 *
 * Responsibility: authenticate and call Cashfree Subscription APIs only.
 * Does NOT create customers, plans, or subscriptions.
 * Does NOT implement payment or subscription business logic.
 *
 * Configure via:
 *   supabase secrets set CASHFREE_CLIENT_ID=... CASHFREE_CLIENT_SECRET=... \
 *     CASHFREE_ENV=TEST
 */

import { requireEnv } from "./env.ts";

type CashfreeEnv = "TEST" | "PRODUCTION";

/** Cashfree Subscriptions API version sent on every request. */
const CASHFREE_API_VERSION = "2026-01-01";

const BASE_URLS: Record<CashfreeEnv, string> = {
  TEST: "https://sandbox.cashfree.com/pg",
  PRODUCTION: "https://api.cashfree.com/pg",
};

/** Reads and validates CASHFREE_ENV (TEST | PRODUCTION). */
function getCashfreeEnv(): CashfreeEnv {
  const value = requireEnv("CASHFREE_ENV").trim().toUpperCase();
  if (value !== "TEST" && value !== "PRODUCTION") {
    throw new Error(
      `Invalid CASHFREE_ENV: "${value}". Expected TEST or PRODUCTION.`,
    );
  }
  return value;
}

/** Returns the Cashfree Subscriptions API base URL for the configured environment. */
export function getCashfreeBaseUrl(): string {
  return BASE_URLS[getCashfreeEnv()];
}

/**
 * Builds authentication and content headers for Cashfree Subscription API calls.
 * Secrets are read from environment variables — never hardcoded.
 */
export function getCashfreeHeaders(): Record<string, string> {
  return {
    Accept: "application/json",
    "Content-Type": "application/json",
    "x-api-version": CASHFREE_API_VERSION,
    "x-client-id": requireEnv("CASHFREE_CLIENT_ID"),
    "x-client-secret": requireEnv("CASHFREE_CLIENT_SECRET"),
  };
}

/**
 * Performs an authenticated Cashfree Subscription HTTP request.
 *
 * [endpoint] may be a path (e.g. "/subscriptions") or a full URL.
 * [body] is optional and JSON-serialized when provided.
 * [headers] is optional and merged with the default authentication headers
 * (e.g. "x-request-id", "x-idempotency-key").
 * Returns the parsed JSON response body.
 * Throws a descriptive Error on non-2xx responses.
 */
export async function cashfreeFetch<T = unknown>(
  endpoint: string,
  method: string,
  body?: Record<string, unknown>,
  headers?: Record<string, string>,
): Promise<T> {
  const url = endpoint.startsWith("http")
    ? endpoint
    : `${getCashfreeBaseUrl()}${
      endpoint.startsWith("/") ? endpoint : `/${endpoint}`
    }`;

  const response = await fetch(url, {
    method,
    headers: {
      ...getCashfreeHeaders(),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const responseText = await response.text();
  let payload: unknown = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      payload = responseText;
    }
  }

  if (!response.ok) {
    const detail = typeof payload === "string"
      ? payload
      : JSON.stringify(payload);
    throw new Error(`Cashfree HTTP ${response.status}: ${detail}`);
  }

  return payload as T;
}

/** Result of a best-effort Cashfree subscription cancel. Never throws to callers. */
export type CancelCashfreeSubscriptionResult = {
  success: boolean;
  /** True when cancel was not attempted (e.g. blank id). */
  skipped?: boolean;
  detail?: string;
};

/**
 * Cancels a Cashfree subscription via Manage Subscription API.
 *
 * POST /subscriptions/{subscription_id}/manage
 * body: { subscription_id, action: "CANCEL" }
 *
 * Reusable for pending recovery cleanup and future Cancel Auto Renewal.
 * Does not throw — callers decide whether failure is fatal.
 *
 * @see https://www.cashfree.com/docs/api-reference/payments/latest/subscription/manage-subscription
 */
export async function cancelCashfreeSubscription(
  subscriptionId: string,
): Promise<CancelCashfreeSubscriptionResult> {
  const id = subscriptionId.trim();
  if (!id) {
    return {
      success: false,
      skipped: true,
      detail: "empty cashfree_subscription_id",
    };
  }

  try {
    const idempotencyKey = crypto.randomUUID();
    await cashfreeFetch(
      `/subscriptions/${encodeURIComponent(id)}/manage`,
      "POST",
      {
        subscription_id: id,
        action: "CANCEL",
      },
      {
        "x-request-id": idempotencyKey,
        "x-idempotency-key": idempotencyKey,
      },
    );
    return { success: true };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    return { success: false, detail };
  }
}

/**
 * True when Cashfree REJECTS CANCEL because the subscription is already in a
 * terminal / in-flight cancel state — a safe no-op for the caller.
 */
export function isNonActionableCancelError(detail: string | undefined): boolean {
  if (!detail) return false;
  const text = detail.toLowerCase();
  return (
    text.includes("already cancelled") ||
    text.includes("already canceled") ||
    text.includes("already cancelling") ||
    text.includes("already canceling") ||
    text.includes("already processed") ||
    text.includes("already in cancelled") ||
    text.includes("already in canceled") ||
    text.includes("subscription is cancelled") ||
    text.includes("subscription is canceled") ||
    text.includes("subscription cancelled") ||
    text.includes("subscription canceled") ||
    text.includes("status is cancelled") ||
    text.includes("status is canceled") ||
    text.includes("customer_cancelled") ||
    (text.includes("already") && /\bcancell?ed\b/.test(text)) ||
    (text.includes("already") && /\bcancell?ing\b/.test(text))
  );
}
