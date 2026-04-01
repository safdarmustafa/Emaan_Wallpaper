import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: jsonHeaders,
    });
  }

  try {
    const body = await req.json();
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    const razorpay_payment_id =
      typeof body.razorpay_payment_id === "string"
        ? body.razorpay_payment_id.trim()
        : "";

    if (!phone) {
      return new Response(JSON.stringify({ error: "phone is required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }
    if (!razorpay_payment_id) {
      return new Response(
        JSON.stringify({
          error: "razorpay_payment_id is required (₹5 payment must complete first)",
        }),
        { status: 400, headers: jsonHeaders },
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceKey) {
      console.error("razorpay-subscription: SUPABASE_URL or SERVICE_ROLE_KEY missing");
      return new Response(JSON.stringify({ error: "Server configuration error" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);

    const { data: userRow, error: userErr } = await supabase
      .from("users")
      .select("phone_number")
      .eq("phone_number", phone)
      .maybeSingle();

    if (userErr) {
      console.error("user lookup", userErr);
      return new Response(JSON.stringify({ error: userErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    if (!userRow) {
      return new Response(JSON.stringify({ error: "User not found" }), {
        status: 404,
        headers: jsonHeaders,
      });
    }

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
    if (!keyId || !keySecret) {
      return new Response(JSON.stringify({ error: "Razorpay not configured" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const planId = Deno.env.get("RAZORPAY_PLAN_ID") ?? "plan_SUjMMDAgQKiHOy";
    const auth = btoa(`${keyId}:${keySecret}`);

    // First renewal charge: 3 days after signup (aligns with in-app trial)
    const startAtUnix = Math.floor(Date.now() / 1000) + 3 * 24 * 60 * 60;

    const rz = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        plan_id: planId,
        total_count: 120,
        customer_notify: 1,
        start_at: startAtUnix,
        notes: { phone, source: "emaan_wallpapers_app" },
      }),
    });

    const data = await rz.json();

    if (!rz.ok) {
      const msg =
        data?.error?.description ||
        data?.error?.message ||
        JSON.stringify(data);
      console.error("Razorpay subscriptions.create failed:", msg);
      return new Response(JSON.stringify({ error: msg, details: data }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    if (!data?.id) {
      return new Response(
        JSON.stringify({ error: "No subscription id from Razorpay" }),
        { status: 502, headers: jsonHeaders },
      );
    }

    const { error: upErr } = await supabase
      .from("users")
      .update({
        razorpay_subscription_id: data.id,
        subscription_status: "created",
        is_subscribed: false,
        razorpay_payment_id,
        plan_id: planId,
      })
      .eq("phone_number", phone);

    if (upErr) {
      console.error("users update failed", upErr);
      return new Response(JSON.stringify({ error: upErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    return new Response(JSON.stringify({ subscription_id: data.id }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("razorpay-subscription", e);
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
