// @ts-nocheck

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

serve(async (req: Request) => {
  try {
    const body = await req.text();

    const webhookSecret = Deno.env.get("RAZORPAY_WEBHOOK_SECRET");
    const signature =
      req.headers.get("x-razorpay-signature") ||
      req.headers.get("X-Razorpay-Signature");

    if (webhookSecret) {
      if (!signature) {
        return new Response("Missing signature", { status: 401 });
      }
      const expected = await hmacSha256Hex(webhookSecret, body);
      if (expected !== signature) {
        return new Response("Invalid signature", { status: 401 });
      }
    }

    const event = JSON.parse(body);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL"),
      Deno.env.get("SERVICE_ROLE_KEY"),
    );

    const sub = event.payload?.subscription?.entity;

    if (!sub) {
      return new Response("No subscription data", { status: 400 });
    }

    const subId = sub.id;

    // subscription.charged — recurring charge (including first charge after trial)
    if (event.event === "subscription.charged") {
      await supabase
        .from("users")
        .update({
          is_subscribed: true,
          subscription_status: "active",
          current_period_end: new Date(sub.current_end * 1000).toISOString(),
        })
        .eq("razorpay_subscription_id", subId);
    }

    // subscription.cancelled — cycle ended / subscription fully cancelled
    if (event.event === "subscription.cancelled") {
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "cancelled",
        })
        .eq("razorpay_subscription_id", subId);
    }

    // subscription.halted | subscription.completed — no longer entitled
    if (
      event.event === "subscription.halted" ||
      event.event === "subscription.completed"
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
  } catch (err) {
    return new Response(JSON.stringify({
      error: err.message,
    }), { status: 500 });
  }
});
