// @ts-nocheck

import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const { subscription_id } = await req.json();

    if (!subscription_id || typeof subscription_id !== "string") {
      return new Response(JSON.stringify({ error: "subscription_id required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SERVICE_ROLE_KEY")!,
    );

    const { data: rows, error: lookupErr } = await supabase
      .from("users")
      .select("phone_number")
      .eq("razorpay_subscription_id", subscription_id)
      .limit(1);

    if (lookupErr) {
      return new Response(JSON.stringify({ error: lookupErr.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (!rows?.length) {
      return new Response(
        JSON.stringify({
          error:
            "No account row matches this subscription. Open the app after a successful payment.",
        }),
        { status: 404, headers: { "Content-Type": "application/json" } },
      );
    }

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

    const auth = btoa(`${keyId}:${keySecret}`);

    const res = await fetch(
      `https://api.razorpay.com/v1/subscriptions/${subscription_id}/cancel`,
      {
        method: "POST",
        headers: {
          "Authorization": `Basic ${auth}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          cancel_at_cycle_end: true,
        }),
      },
    );

    const data = await res.json();

    if (!res.ok) {
      const razorpayMsg =
        data?.error?.description ||
        data?.error?.message ||
        (typeof data?.error === "string" ? data.error : null) ||
        data?.message ||
        JSON.stringify(data);
      return new Response(
        JSON.stringify({
          error: `Razorpay: ${razorpayMsg}`,
        }),
        { status: 502, headers: { "Content-Type": "application/json" } },
      );
    }

    const { error: dbError, data: updated } = await supabase
      .from("users")
      .update({
        subscription_status: "cancel_requested",
      })
      .eq("razorpay_subscription_id", subscription_id)
      .select("phone_number");

    if (dbError) {
      return new Response(JSON.stringify({ error: dbError.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (!updated?.length) {
      return new Response(
        JSON.stringify({ error: "Database update failed (no matching row)" }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      );
    }

    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
