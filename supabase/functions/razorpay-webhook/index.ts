import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

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

    // Do NOT grant premium on mandate created — app activates trial after mandate success
    if (eventName === "subscription.activated") {
      return new Response("OK", { status: 200 });
    }

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
    } else if (eventName === "subscription.cancelled") {
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "cancelled",
        })
        .eq("razorpay_subscription_id", subId);
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

    return new Response("OK", { status: 200 });
  } catch (e) {
    console.error("razorpay-webhook", e);
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
    });
  }
});
