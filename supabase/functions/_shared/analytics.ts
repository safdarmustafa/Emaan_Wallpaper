/**
 * Server-side analytics for Cashfree money events.
 * Failures are logged only — callers must never fail the webhook because of this.
 *
 * Secrets (optional; missing = skip that destination):
 *   MIXPANEL_TOKEN
 *   META_APP_ID (or META_DATASET_ID)
 *   META_CAPI_ACCESS_TOKEN
 */

import { MONTHLY_CHARGE_AMOUNT } from "./premium.ts";

/** ₹3 UPI authorization / trial. */
export const TRIAL_AMOUNT = 3;

/** Standard event names — keep identical on Mixpanel and Meta. */
export const ANALYTICS_EVENTS = {
  START_TRIAL: "StartTrial",
  PURCHASE: "Purchase",
} as const;

export type MoneyAnalyticsKind = "AUTH" | "CHARGE";

export interface MoneyAnalyticsPayload {
  kind: MoneyAnalyticsKind;
  paymentId: string;
  phone: string | null;
  subscriptionId: string | null;
  cashfreeSubscriptionId: string | null;
}

function optionalEnv(name: string): string | null {
  const value = Deno.env.get(name)?.trim();
  return value ? value : null;
}

/** Maps Cashfree payment kind → standard event + amount. */
export function moneyEventForKind(
  kind: MoneyAnalyticsKind,
): { eventName: string; value: number; currency: string } {
  if (kind === "CHARGE") {
    return {
      eventName: ANALYTICS_EVENTS.PURCHASE,
      value: MONTHLY_CHARGE_AMOUNT,
      currency: "INR",
    };
  }
  return {
    eventName: ANALYTICS_EVENTS.START_TRIAL,
    value: TRIAL_AMOUNT,
    currency: "INR",
  };
}

async function sha256Hex(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** Normalize Indian phone to digits for hashing / distinct_id. */
export function normalizePhone(phone: string | null | undefined): string | null {
  if (!phone) return null;
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 10) return null;
  return digits.length > 10 ? digits.slice(-10) : digits;
}

async function trackMixpanel(args: {
  eventName: string;
  distinctId: string;
  insertId: string;
  value: number;
  currency: string;
  props: Record<string, unknown>;
}): Promise<void> {
  const token = optionalEnv("MIXPANEL_TOKEN");
  if (!token) {
    console.warn("analytics mixpanel skipped: MIXPANEL_TOKEN missing");
    return;
  }

  const payload = [{
    event: args.eventName,
    properties: {
      token,
      distinct_id: args.distinctId,
      time: Math.floor(Date.now() / 1000),
      $insert_id: args.insertId,
      value: args.value,
      currency: args.currency,
      ...args.props,
    },
  }];

  const response = await fetch("https://api.mixpanel.com/track?verbose=1", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/plain" },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const body = await response.text();
    console.error("analytics mixpanel failed", {
      status: response.status,
      body: body.slice(0, 300),
      eventName: args.eventName,
    });
  }
}

/**
 * Meta app server events.
 * Prefer Conversions API `/{DATASET_ID}/events` when META_DATASET_ID is set
 * (dataset must be linked to the app in Events Manager).
 * Fallback: Application `/{APP_ID}/activities` with CUSTOM_APP_EVENTS
 * (works with META_APP_ID + system-user token).
 */
async function trackMetaCapi(args: {
  eventName: string;
  eventId: string;
  value: number;
  currency: string;
  phone: string | null;
  props: Record<string, unknown>;
}): Promise<void> {
  const accessToken = optionalEnv("META_CAPI_ACCESS_TOKEN");
  const appId = optionalEnv("META_APP_ID");
  const datasetId = optionalEnv("META_DATASET_ID");
  if (!accessToken || (!datasetId && !appId)) {
    console.warn(
      "analytics meta skipped: META_CAPI_ACCESS_TOKEN and META_APP_ID/META_DATASET_ID required",
    );
    return;
  }

  const phone = args.phone;
  const hashedPhone = phone ? await sha256Hex(phone) : null;

  // 1) Conversions API dataset endpoint (correct for Ads Manager optimisation).
  if (datasetId) {
    const userData: Record<string, unknown> = {};
    if (hashedPhone) {
      userData.ph = [hashedPhone];
      userData.external_id = [hashedPhone];
    }
    const body = {
      data: [
        {
          event_name: args.eventName,
          event_time: Math.floor(Date.now() / 1000),
          event_id: args.eventId,
          action_source: "app",
          user_data: userData,
          custom_data: {
            currency: args.currency,
            value: args.value,
            ...args.props,
          },
          app_data: {
            advertiser_tracking_enabled: "1",
            application_tracking_enabled: "1",
            extinfo: [
              "a2",
              appId ?? datasetId,
              "",
              "",
              "1.1.6",
              "",
              "",
              "",
              "",
              "",
              "",
              "",
              "",
              "",
              "",
              "",
            ],
          },
        },
      ],
    };
    const url =
      `https://graph.facebook.com/v21.0/${encodeURIComponent(datasetId)}/events` +
      `?access_token=${encodeURIComponent(accessToken)}`;
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      console.error("analytics meta dataset/events failed", {
        status: response.status,
        body: text.slice(0, 400),
        eventName: args.eventName,
      });
    }
    return;
  }

  // 2) App ID alone cannot use /events (Graph error 100/33). Use /activities.
  const metaEventName = args.eventName === ANALYTICS_EVENTS.PURCHASE
    ? "fb_mobile_purchase"
    : args.eventName;
  const customEvent: Record<string, unknown> = {
    _eventName: metaEventName,
    _valueToSum: args.value,
    fb_currency: args.currency,
    event_id: args.eventId,
    ...args.props,
  };
  const form = new URLSearchParams();
  form.set("event", "CUSTOM_APP_EVENTS");
  form.set("advertiser_tracking_enabled", "1");
  form.set("application_tracking_enabled", "1");
  form.set("custom_events", JSON.stringify([customEvent]));
  if (hashedPhone) {
    form.set("ud", JSON.stringify({ ph: hashedPhone, extern_id: hashedPhone }));
  }
  form.set("access_token", accessToken);

  const activitiesUrl =
    `https://graph.facebook.com/v21.0/${encodeURIComponent(appId!)}/activities`;
  const activitiesResponse = await fetch(activitiesUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  if (!activitiesResponse.ok) {
    const text = await activitiesResponse.text();
    console.error("analytics meta activities failed", {
      status: activitiesResponse.status,
      body: text.slice(0, 400),
      eventName: args.eventName,
    });
  }
}

/**
 * Fire StartTrial (AUTH) or Purchase (CHARGE) to Mixpanel + Meta.
 * Never throws.
 */
export async function trackMoneyAnalytics(
  payload: MoneyAnalyticsPayload,
): Promise<void> {
  try {
    const { eventName, value, currency } = moneyEventForKind(payload.kind);
    const phone = normalizePhone(payload.phone);
    const eventId = payload.paymentId.trim();
    if (!eventId) {
      console.warn("analytics skipped: empty paymentId");
      return;
    }

    const distinctId = phone ??
      payload.cashfreeSubscriptionId ??
      `sub_${payload.subscriptionId ?? eventId}`;

    const props: Record<string, unknown> = {
      source: "cashfree_webhook",
      payment_kind: payload.kind,
      payment_id: eventId,
    };
    if (payload.subscriptionId) {
      props.subscription_row_id = payload.subscriptionId;
    }
    if (payload.cashfreeSubscriptionId) {
      props.cashfree_subscription_id = payload.cashfreeSubscriptionId;
    }

    await Promise.all([
      trackMixpanel({
        eventName,
        distinctId,
        insertId: eventId,
        value,
        currency,
        props,
      }),
      trackMetaCapi({
        eventName,
        eventId,
        value,
        currency,
        phone,
        props,
      }),
    ]);

    console.info("analytics money event sent", {
      eventName,
      value,
      kind: payload.kind,
      paymentId: eventId,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("analytics money event unexpected error", detail);
  }
}
