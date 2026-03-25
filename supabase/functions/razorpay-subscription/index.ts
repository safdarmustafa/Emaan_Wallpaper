// @ts-nocheck

import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req: Request) => {

  try {

    const { phone } = await req.json();

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

    const auth = btoa(`${keyId}:${keySecret}`);

    // 🔥 CREATE SUBSCRIPTION (3 DAYS TRIAL)
    const res = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        "Authorization": `Basic ${auth}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        plan_id: "plan_SUjMMDAgQKiHOy",
        total_count: 12,
        customer_notify: 1,

        // 🔥 TRIAL → 3 DAYS
        start_at: Math.floor(Date.now() / 1000) + (3 * 24 * 60 * 60)
      })
    });

    const data = await res.json();

    if (!data.id) {
      return new Response(JSON.stringify({
        error: "Subscription creation failed",
        details: data
      }), { status: 500 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL"),
      Deno.env.get("SERVICE_ROLE_KEY")
    );

    // 🔥 IMPORTANT FIX
    await supabase
      .from("users")
      .update({
        razorpay_subscription_id: data.id,
        subscription_status: data.status,

        // ❌ DO NOT ACTIVATE HERE
        is_subscribed: false
      })
      .eq("phone_number", phone);

    return new Response(JSON.stringify({
      subscription_id: data.id
    }), { status: 200 });

  } catch (err) {
    return new Response(JSON.stringify({
      error: err.message
    }), { status: 500 });
  }
});