// @ts-nocheck

import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req: Request) => {

  try {

    const body = await req.text();
    const event = JSON.parse(body);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL"),
      Deno.env.get("SERVICE_ROLE_KEY")
    );

    const sub = event.payload?.subscription?.entity;

    if (!sub) {
      return new Response("No subscription data", { status: 400 });
    }

    // ✅ PAYMENT SUCCESS (AFTER TRIAL)
    if (event.event === "subscription.charged") {

      await supabase
        .from("users")
        .update({
          is_subscribed: true,
          subscription_status: "active",
          current_period_end: new Date(sub.current_end * 1000).toISOString()
        })
        .eq("razorpay_subscription_id", sub.id);
    }

    // ✅ ACTIVATED (OPTIONAL)
    if (event.event === "subscription.activated") {

      await supabase
        .from("users")
        .update({
          subscription_status: "active"
        })
        .eq("razorpay_subscription_id", sub.id);
    }

    // ✅ CANCELLED
    if (event.event === "subscription.cancelled") {

      await supabase
        .from("users")
        .update({
          // Cancellation requested effective at end of period.
          // Premium access remains until the expiry webhook.
          subscription_status: "cancelled"
        })
        .eq("razorpay_subscription_id", sub.id);
    }

    // ✅ EXPIRED (effective end of billing period after cancellation)
    if (event.event === "subscription.completed" || event.event === "subscription.halted") {
      await supabase
        .from("users")
        .update({
          is_subscribed: false,
          subscription_status: "expired"
        })
        .eq("razorpay_subscription_id", sub.id);
    }

    return new Response("OK", { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({
      error: err.message
    }), { status: 500 });
  }
});