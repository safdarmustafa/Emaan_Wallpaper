/**
 * Server-side analytics for Cashfree money events.
 * Failures are logged only — callers must never fail the webhook because of this.
 *
 * Secrets (optional; missing = skip that destination):
 *   MIXPANEL_TOKEN
 *   META_APP_ID (or META_DATASET_ID)
 *   META_CAPI_ACCESS_TOKEN
 */

import type { SupabaseClient } from "npm:@supabase/supabase-js@2";
import {
  clampEventTimeSeconds,
  hashPhoneForMeta,
  type MetaDeviceMatchRow,
  resolveExtinfo,
  usableAdId,
} from "./metaMatch.ts";

/** ₹3 UPI authorization / trial. */
export const TRIAL_AMOUNT = 3;

/** Standard event names. */
export const ANALYTICS_EVENTS = {
  START_TRIAL: "StartTrial",
  /** Meta Purchase for ₹3 AUTH trial only. ₹499 CHARGE sends nothing. */
  PURCHASE: "Purchase",
} as const;

export type MoneyAnalyticsKind = "AUTH" | "CHARGE";

export interface MoneyAnalyticsPayload {
  kind: MoneyAnalyticsKind;
  paymentId: string;
  phone: string | null;
  /** Application user id (users.id) for loading meta_match_data. */
  userId: string | null;
  subscriptionId: string | null;
  cashfreeSubscriptionId: string | null;
}

function optionalEnv(name: string): string | null {
  const value = Deno.env.get(name)?.trim();
  return value ? value : null;
}

/**
 * Maps Cashfree payment kind → Meta event + amount.
 * Only ₹3 AUTH → Purchase. ₹499 CHARGE is not tracked.
 */
export function moneyEventForKind(
  kind: MoneyAnalyticsKind,
): { metaEventName: string; mixpanelEventName: string | null; value: number; currency: string } | null {
  if (kind === "CHARGE") {
    return null;
  }
  return {
    metaEventName: ANALYTICS_EVENTS.PURCHASE,
    mixpanelEventName: null,
    value: TRIAL_AMOUNT,
    currency: "INR",
  };
}

/** Normalize Indian phone to last-10 digits for Mixpanel distinct_id. */
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
 * Loads meta_match_data + users.meta_anon_id with the service role.
 * Any problem (no row, error, throw) → null (legacy path).
 */
export async function loadMetaDeviceMatch(
  serviceClient: SupabaseClient,
  userId: string | null | undefined,
): Promise<MetaDeviceMatchRow | null> {
  if (!userId) return null;
  try {
    const { data: match, error: matchError } = await serviceClient
      .from("meta_match_data")
      .select("madid, ad_tracking_enabled, extinfo, client_user_agent, client_ip")
      .eq("user_id", userId)
      .maybeSingle();

    if (matchError) {
      console.warn("analytics meta_match_data load failed", {
        message: matchError.message,
      });
      return null;
    }
    if (!match) return null;

    const { data: user, error: userError } = await serviceClient
      .from("users")
      .select("meta_anon_id")
      .eq("id", userId)
      .maybeSingle();

    if (userError) {
      console.warn("analytics users.meta_anon_id load failed", {
        message: userError.message,
      });
    }

    return {
      madid: typeof match.madid === "string" ? match.madid : null,
      adTrackingEnabled: match.ad_tracking_enabled === true,
      extinfo: Array.isArray(match.extinfo)
        ? match.extinfo.map((s: unknown) => (typeof s === "string" ? s : String(s ?? "")))
        : null,
      clientUserAgent: typeof match.client_user_agent === "string"
        ? match.client_user_agent
        : null,
      clientIp: typeof match.client_ip === "string" ? match.client_ip : null,
      anonId: typeof user?.meta_anon_id === "string" ? user.meta_anon_id : null,
    };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.warn("analytics loadMetaDeviceMatch unexpected", detail);
    return null;
  }
}

export interface MetaPurchaseBuildArgs {
  eventName: string;
  eventId: string;
  value: number;
  currency: string;
  phone: string | null;
  device: MetaDeviceMatchRow | null;
  eventTime?: number;
  props?: Record<string, unknown>;
}

export interface MetaPurchaseBuildResult {
  shouldSend: boolean;
  reason?: string;
  hasDevice: boolean;
  adTracking: 0 | 1;
  body: {
    data: Array<Record<string, unknown>>;
  } | null;
}

/**
 * Builds the CAPI Purchase body.
 * - No device row (older app): legacy send with tracking 0 + placeholder extinfo + hashed phone.
 * - Device row: send only when anon_id OR usable madid is present.
 */
export async function buildMetaPurchasePayload(
  args: MetaPurchaseBuildArgs,
): Promise<MetaPurchaseBuildResult> {
  const hasDevice = args.device != null;
  const madid = args.device
    ? usableAdId(args.device.madid, args.device.adTrackingEnabled)
    : null;
  const anonId = args.device?.anonId?.trim() || null;
  const adTracking: 0 | 1 = madid ? 1 : 0;

  if (hasDevice && !anonId && !madid) {
    return {
      shouldSend: false,
      reason: "no_anon_or_madid",
      hasDevice: true,
      adTracking: 0,
      body: null,
    };
  }

  const hashedPhone = await hashPhoneForMeta(args.phone);
  const userData: Record<string, unknown> = {};
  if (anonId) userData.anon_id = anonId;
  if (madid) userData.madid = madid;
  if (args.device?.clientIp) userData.client_ip_address = args.device.clientIp;
  if (args.device?.clientUserAgent) {
    userData.client_user_agent = args.device.clientUserAgent;
  }
  if (hashedPhone) userData.ph = [hashedPhone];

  const eventTime = clampEventTimeSeconds(
    args.eventTime ?? Math.floor(Date.now() / 1000),
  );

  const body = {
    data: [
      {
        event_name: args.eventName,
        event_time: eventTime,
        event_id: args.eventId,
        action_source: "app",
        user_data: userData,
        custom_data: {
          currency: args.currency,
          value: args.value.toFixed(2),
          ...(args.props ?? {}),
        },
        app_data: {
          advertiser_tracking_enabled: adTracking,
          application_tracking_enabled: 1,
          extinfo: resolveExtinfo(args.device),
        },
      },
    ],
  };

  return {
    shouldSend: true,
    hasDevice,
    adTracking,
    body,
  };
}

type FetchLike = typeof fetch;

/**
 * Meta Conversions API dataset events. Returns true on HTTP 200 and events_received ≥ 1.
 * Never throws.
 */
export async function sendMetaCapiPurchase(args: {
  eventName: string;
  eventId: string;
  value: number;
  currency: string;
  phone: string | null;
  device: MetaDeviceMatchRow | null;
  fetchImpl?: FetchLike;
  timeoutMs?: number;
}): Promise<boolean> {
  const accessToken = optionalEnv("META_CAPI_ACCESS_TOKEN");
  const datasetId = optionalEnv("META_DATASET_ID");
  const appId = optionalEnv("META_APP_ID");

  if (!accessToken || !datasetId) {
    if (!accessToken || (!datasetId && !appId)) {
      console.warn(
        "analytics meta skipped: META_CAPI_ACCESS_TOKEN and META_DATASET_ID required",
      );
    } else if (!datasetId) {
      console.warn(
        "analytics meta skipped: META_DATASET_ID required for app Purchase attribution",
      );
    }
    return false;
  }

  try {
    const built = await buildMetaPurchasePayload({
      eventName: args.eventName,
      eventId: args.eventId,
      value: args.value,
      currency: args.currency,
      phone: args.phone,
      device: args.device,
    });

    if (!built.shouldSend || !built.body) {
      console.info("analytics meta skipped", {
        eventId: args.eventId,
        reason: built.reason ?? "not_sending",
        hasDevice: built.hasDevice,
        adTracking: built.adTracking,
      });
      return false;
    }

    const fetchFn = args.fetchImpl ?? fetch;
    const timeoutMs = args.timeoutMs ?? 3_000;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const url =
        `https://graph.facebook.com/v21.0/${encodeURIComponent(datasetId)}/events` +
        `?access_token=${encodeURIComponent(accessToken)}`;
      const response = await fetchFn(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(built.body),
        signal: controller.signal,
      });

      const text = await response.text();
      let eventsReceived = 0;
      try {
        const json = JSON.parse(text) as { events_received?: number };
        eventsReceived = Number(json.events_received ?? 0);
      } catch {
        eventsReceived = 0;
      }

      const sent = response.ok && eventsReceived >= 1;
      console.info("analytics meta send", {
        eventId: args.eventId,
        sent,
        hasDevice: built.hasDevice,
        adTracking: built.adTracking,
        status: response.status,
        eventsReceived,
      });

      if (!response.ok) {
        console.error("analytics meta dataset/events failed", {
          status: response.status,
          body: text.slice(0, 400),
          eventName: args.eventName,
        });
      }
      return sent;
    } finally {
      clearTimeout(timer);
    }
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("analytics meta send error", {
      eventId: args.eventId,
      detail,
    });
    return false;
  }
}

/**
 * Fire Meta Purchase for ₹3 AUTH trial only. ₹499 CHARGE is ignored.
 * Never throws.
 */
export async function trackMoneyAnalytics(
  payload: MoneyAnalyticsPayload,
  deps?: {
    serviceClient?: SupabaseClient;
    fetchImpl?: FetchLike;
  },
): Promise<void> {
  try {
    const mapped = moneyEventForKind(payload.kind);
    if (!mapped) {
      console.info("analytics money event skipped", {
        kind: payload.kind,
        reason: "CHARGE_not_tracked",
        paymentId: payload.paymentId,
      });
      return;
    }
    const { metaEventName, mixpanelEventName, value, currency } = mapped;
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

    let device: MetaDeviceMatchRow | null = null;
    if (deps?.serviceClient && payload.userId) {
      device = await loadMetaDeviceMatch(deps.serviceClient, payload.userId);
    }

    const tasks: Promise<unknown>[] = [
      sendMetaCapiPurchase({
        eventName: metaEventName,
        eventId,
        value,
        currency,
        phone: payload.phone,
        device,
        fetchImpl: deps?.fetchImpl,
      }),
    ];

    if (mixpanelEventName) {
      tasks.push(
        trackMixpanel({
          eventName: mixpanelEventName,
          distinctId,
          insertId: eventId,
          value,
          currency,
          props,
        }),
      );
    }

    await Promise.all(tasks);

    console.info("analytics money event done", {
      metaEventName,
      mixpanelEventName,
      value,
      kind: payload.kind,
      paymentId: eventId,
      hasDevice: device != null,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("analytics money event unexpected error", detail);
  }
}
