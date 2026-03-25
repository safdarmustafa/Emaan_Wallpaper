// @ts-nocheck

import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req: Request) => {

  try {

    const { subscription_id } = await req.json();

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

    const auth = btoa(`${keyId}:${keySecret}`);

    const res = await fetch(
      `https://api.razorpay.com/v1/subscriptions/${subscription_id}/cancel`,
      {
        method: "POST",
        headers: {
          "Authorization": `Basic ${auth}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          // SaaS behavior: cancel effective at end of current billing period.
          cancel_at_cycle_end: 1
        })
      }
    );

    const data = await res.json();

    if (!res.ok) {
      return new Response(JSON.stringify({
        error: "Razorpay cancel failed",
        details: data
      }), { status: 500 });
    }

    // ✅ Update DB immediately (so app sees changes right after clicking cancel).
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL"),
      Deno.env.get("SERVICE_ROLE_KEY")
    );

    await supabase
      .from("users")
      .update({
        // Keep `is_subscribed` unchanged until the expiry webhook fires.
        subscription_status: "cancelled"
      })
      .eq("razorpay_subscription_id", subscription_id);

    return new Response(JSON.stringify(data), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({
      error: err.message
    }), { status: 500 });
  }
});