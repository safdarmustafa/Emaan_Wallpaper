import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { trackMixpanelPurchase } from "../_shared/mixpanel.ts";

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function extractSubscriptionEntity(event: Record<string, unknown>): {
  id: string | null;
  current_end: number | null;
} {
  const payload = event.payload as Record<string, unknown> | undefined;
  if (!payload) return { id: null, current_end: null };

  const subWrap = payload.subscription as { entity?: Record<string, unknown> } | undefined;
  const ent = subWrap?.entity;
  if (ent && typeof ent === "object") {
    const id = typeof ent.id === "string" ? ent.id : null;
    const ce = ent.current_end;
    const current_end = typeof ce === "number" ? ce : null;
    if (id) return { id, current_end };
  }

  const payWrap = payload.payment as { entity?: Record<string, unknown> } | undefined;
  const payEnt = payWrap?.entity;
  if (payEnt && typeof payEnt === "object") {
    const sid = payEnt.subscription_id;
    if (typeof sid === "string") {
      return { id: sid, current_end: null };
    }
  }

  return { id: null, current_end: null };
}

/** Payment fields from subscription.charged (analytics only; does not affect DB writes). */
function extractPaymentCharge(event: Record<string, unknown>): {
  paymentId: string | null;
  amountMinor: number | null;
  currency: string | null;
} {
  const payload = event.payload as Record<string, unknown> | undefined;
  const payWrap = payload?.payment as { entity?: Record<string, unknown> } | undefined;
  const payEnt = payWrap?.entity;
  if (!payEnt || typeof payEnt !== "object") {
    return { paymentId: null, amountMinor: null, currency: null };
  }
  const paymentId = typeof payEnt.id === "string" ? payEnt.id : null;
  const amountMinor = typeof payEnt.amount === "number" ? payEnt.amount : null;
  const currency = typeof payEnt.currency === "string" ? payEnt.currency : null;
  return { paymentId, amountMinor, currency };
}

/**
 * Analytics-only helper. Never throws to the webhook caller.
 * Identity matches Android Mixpanel identify(phone).
 */
async function emitMixpanelSubscriptionCharge(args: {
  // Keep loose typing — Edge Function client is untyped against the users schema.
  // deno-lint-ignore no-explicit-any
  supabase: any;
  event: Record<string, unknown>;
  subscriptionId: string;
  periodEndIso: string | null;
  razorpayEventId: string;
}): Promise<void> {
  try {
    const { paymentId, amountMinor, currency } = extractPaymentCharge(args.event);
    const insertId = (paymentId ?? args.razorpayEventId).trim();
    if (!insertId) {
      console.warn(
        "mixpanel: no payment_id or razorpay_event_id — skipping Purchase",
      );
      return;
    }
    if (amountMinor == null || amountMinor <= 0 || !currency?.trim()) {
      console.warn(
        "mixpanel: amount/currency missing on subscription.charged — skipping Purchase",
      );
      return;
    }
    // Razorpay amounts are in the smallest currency unit (e.g. paise for INR).
    const amountMajor = amountMinor / 100;

    const { data: userRows, error: userErr } = await args.supabase
      .from("users")
      .select("phone_number")
      .eq("razorpay_subscription_id", args.subscriptionId)
      .limit(1);
    if (userErr) {
      console.error("mixpanel: user lookup failed", userErr);
      return;
    }
    const row = userRows?.[0] as { phone_number?: unknown } | undefined;
    const phone =
      typeof row?.phone_number === "string" ? row.phone_number.trim() : "";
    if (!phone) {
      console.warn(
        "mixpanel: no phone_number for subscription — skipping Purchase",
      );
      return;
    }

    await trackMixpanelPurchase({
      distinctId: phone,
      insertId,
      subscriptionId: args.subscriptionId,
      paymentId,
      amount: amountMajor,
      currency: currency.trim().toUpperCase(),
      billingType: "subscription_charge",
      razorpayEventId: args.razorpayEventId || null,
      currentPeriodEnd: args.periodEndIso,
    });
  } catch (e) {
    console.error("mixpanel: unexpected error (ignored)", e);
  }
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const rawBody = await req.text();
    const webhookSecret = Deno.env.get("RAZORPAY_WEBHOOK_SECRET");
    const signature =
      req.headers.get("x-razorpay-signature") ??
      req.headers.get("X-Razorpay-Signature");

    if (!webhookSecret || webhookSecret.trim() === "") {
      console.error("razorpay-webhook: RAZORPAY_WEBHOOK_SECRET not set — refusing");
      return new Response("Webhook secret not configured", { status: 503 });
    }

    if (!signature) {
      console.warn("razorpay-webhook: missing X-Razorpay-Signature");
      return new Response("Missing signature", { status: 401 });
    }

    const expected = await hmacSha256Hex(webhookSecret, rawBody);
    if (expected !== signature) {
      console.warn("razorpay-webhook: invalid HMAC");
      return new Response("Invalid signature", { status: 401 });
    }

    const event = JSON.parse(rawBody) as Record<string, unknown>;
    const eventName = typeof event.event === "string" ? event.event : "";

    console.log("razorpay-webhook event:", eventName);

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceKey) {
      return new Response("Server misconfigured", { status: 503 });
    }

    const supabase = createClient(supabaseUrl, serviceKey);

    const { id: subId, current_end } = extractSubscriptionEntity(event);

    if (!subId) {
      console.log("razorpay-webhook: no subscription id in payload (ignored)");
      return new Response("OK", { status: 200 });
    }

    // Phase 2D: production-safe webhook idempotency (idempotent-consumer pattern).
    // Razorpay uses at-least-once delivery, so the same event can arrive multiple times
    // (retries / timeouts / concurrent deliveries). Razorpay delivers the unique per-event id in the
    // `x-razorpay-event-id` HEADER — the webhook JSON body has NO top-level event.id (verified
    // against Razorpay's official webhook docs), so that header is the idempotency key.
    //
    // The marker is recorded ONLY AFTER an event has been fully processed (see markProcessed below),
    // never before. This guarantees:
    //   - a true duplicate (already fully processed) is caught by the fast pre-check and skipped,
    //   - a transient failure leaves NO marker, so Razorpay's retry re-processes the event,
    //   - concurrent deliveries collapse to a single row via the event_id PRIMARY KEY.
    // Every branch below is an idempotent state assignment with no irreversible side effects, so
    // re-processing after a failure only re-applies the same final state.
    const eventId =
      req.headers.get("x-razorpay-event-id") ??
      req.headers.get("X-Razorpay-Event-Id") ??
      "";

    // Fast duplicate check: if this event id was already recorded as processed, do nothing.
    if (eventId) {
      const { data: seenRows, error: seenErr } = await supabase
        .from("razorpay_webhook_events")
        .select("event_id")
        .eq("event_id", eventId)
        .limit(1);
      if (seenErr) {
        console.error("razorpay-webhook: idempotency lookup failed", seenErr);
        return new Response(JSON.stringify({ error: seenErr.message }), { status: 500 });
      }
      if (seenRows && seenRows.length > 0) {
        console.log(
          JSON.stringify({
            fn: "razorpay-webhook",
            step: "skip_duplicate",
            event: eventName,
            event_id: eventId,
            subscription_id: subId,
          }),
        );
        return new Response("OK", { status: 200 });
      }
    } else {
      console.warn(
        "razorpay-webhook: missing x-razorpay-event-id header — processing without dedup",
      );
    }

    // Records this event as processed. Uses INSERT ... ON CONFLICT DO NOTHING (upsert with
    // ignoreDuplicates) so concurrent deliveries collapse to a single row without throwing — no
    // reliance on catching a unique-violation. Called ONLY after a branch finishes successfully,
    // never on the failure/500 paths, so a partial failure leaves no marker and stays retryable.
    const markProcessed = async () => {
      if (!eventId) return;
      const { error: markErr } = await supabase
        .from("razorpay_webhook_events")
        .upsert(
          { event_id: eventId, event_type: eventName, subscription_id: subId },
          { onConflict: "event_id", ignoreDuplicates: true },
        );
      if (markErr) {
        // Processing already succeeded and the DB state is correct; a failed marker write must not
        // downgrade a success to 500 (that would trigger a retry which only re-applies the same
        // idempotent state). Log for observability — a later duplicate simply re-processes safely.
        console.error("razorpay-webhook: mark-processed upsert failed", markErr);
      }
    };

    // Phase 2A: never let a delayed / out-of-order reactivation event revive a cancelled
    // subscription. If the row is already cancel_requested/cancelled, skip these events entirely
    // (no DB write). All other statuses continue to be processed exactly as before.
    const REACTIVATION_EVENTS = new Set([
      "subscription.authenticated",
      "subscription.activated",
      "subscription.charged",
    ]);
    const PROTECTED_STATUSES = new Set(["cancel_requested", "cancelled"]);
    if (REACTIVATION_EVENTS.has(eventName)) {
      const { data: existingRows, error: existingErr } = await supabase
        .from("users")
        .select("subscription_status")
        .eq("razorpay_subscription_id", subId)
        .limit(1);
      if (existingErr) {
        console.error("razorpay-webhook: reactivation guard lookup failed", existingErr);
        return new Response(JSON.stringify({ error: existingErr.message }), { status: 500 });
      }
      const existingStatus = (existingRows?.[0]?.subscription_status ?? "").toLowerCase();
      if (PROTECTED_STATUSES.has(existingStatus)) {
        console.log(
          JSON.stringify({
            fn: "razorpay-webhook",
            step: "skip_reactivation",
            event: eventName,
            subscription_id: subId,
            existing_status: existingStatus,
          }),
        );
        await markProcessed();
        return new Response("OK", { status: 200 });
      }
    }

    if (eventName === "subscription.authenticated") {
      // Same trial grant as activate-trial (authenticated branch). Lets Android's first
      // entitlement refresh succeed without waiting on a separate activate-trial poll.
      // Never downgrade trial/active. Never extend an existing future trial_end.
      const TRIAL_MS = 1 * 24 * 60 * 60 * 1000;
      const { data: authRows } = await supabase
        .from("users")
        .select("subscription_status, trial_end")
        .eq("razorpay_subscription_id", subId)
        .limit(1);
      const authRow = authRows?.[0] as
        | { subscription_status?: unknown; trial_end?: unknown }
        | undefined;
      const currentStatus =
        typeof authRow?.subscription_status === "string"
          ? authRow.subscription_status.trim().toLowerCase()
          : "";
      if (currentStatus === "active" || currentStatus === "trial") {
        console.log(
          JSON.stringify({
            fn: "razorpay-webhook",
            step: "authenticated_skip_already_premium",
            subscription_id: subId,
            existing_status: currentStatus,
          }),
        );
        await markProcessed();
        return new Response("OK", { status: 200 });
      }
      const existingTrialEnd =
        typeof authRow?.trial_end === "string" ? authRow.trial_end : "";
      const existingTrialMs = Date.parse(existingTrialEnd);
      const hasFutureTrial =
        existingTrialEnd !== "" &&
        !Number.isNaN(existingTrialMs) &&
        existingTrialMs > Date.now();
      const trialEnd = hasFutureTrial
        ? existingTrialEnd
        : new Date(Date.now() + TRIAL_MS).toISOString();
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "trial",
          trial_paid: true,
          trial_end: trialEnd,
        })
        .eq("razorpay_subscription_id", subId);
      console.log(
        JSON.stringify({
          fn: "razorpay-webhook",
          step: "authenticated_trial_granted",
          subscription_id: subId,
          trial_end: trialEnd,
        }),
      );
      await markProcessed();
      return new Response("OK", { status: 200 });
    }

    if (eventName === "subscription.activated") {
      const periodEnd =
        current_end != null
          ? new Date(current_end * 1000).toISOString()
          : null;
      const row: Record<string, unknown> = {
        is_subscribed: true,
        subscription_status: "active",
      };
      if (periodEnd) row.current_period_end = periodEnd;
      await supabase.from("users").update(row).eq("razorpay_subscription_id", subId);
      await markProcessed();
      return new Response("OK", { status: 200 });
    }

    if (eventName === "subscription.charged") {
      const periodEnd =
        current_end != null
          ? new Date(current_end * 1000).toISOString()
          : null;

      const row: Record<string, unknown> = {
        is_subscribed: true,
        subscription_status: "active",
      };
      if (periodEnd) row.current_period_end = periodEnd;

      await supabase.from("users").update(row).eq("razorpay_subscription_id", subId);

      // Analytics only — must never affect subscription processing or webhook status.
      await emitMixpanelSubscriptionCharge({
        supabase,
        event,
        subscriptionId: subId,
        periodEndIso: periodEnd,
        razorpayEventId: eventId,
      });
    } else if (eventName === "subscription.cancelled") {
      // Phase 2B: make webhook-initiated cancellation write the same metadata as the
      // cancel-subscription Edge Function. Preserve an existing cancelled_at (e.g. set when the
      // user cancelled in-app) and only populate it with the current server timestamp when missing.
      const { data: existingRows } = await supabase
        .from("users")
        .select("cancelled_at")
        .eq("razorpay_subscription_id", subId)
        .limit(1);
      const existingCancelledAt =
        typeof existingRows?.[0]?.cancelled_at === "string" && existingRows[0].cancelled_at !== ""
          ? existingRows[0].cancelled_at
          : null;
      const cancelledAt = existingCancelledAt ?? new Date().toISOString();
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "cancelled",
          cancelled_at: cancelledAt,
        })
        .eq("razorpay_subscription_id", subId);
      console.log(
        JSON.stringify({
          fn: "razorpay-webhook",
          step: "subscription_cancelled",
          subscription_id: subId,
          cancelled_at: cancelledAt,
        }),
      );
    } else if (
      eventName === "subscription.halted" ||
      eventName === "subscription.completed"
    ) {
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "expired",
        })
        .eq("razorpay_subscription_id", subId);
    }

    await markProcessed();
    return new Response("OK", { status: 200 });
  } catch (e) {
    console.error("razorpay-webhook", e);
    const message = e instanceof Error ? e.message : String(e);
    return new Response(JSON.stringify({ error: message }), {
      status: 500,
    });
  }
});
