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
    const log = (step: string, meta: Record<string, unknown> = {}) =>
      console.log(JSON.stringify({ fn: "create-subscription", step, ...meta }));
    const body = await req.json();
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    if (!phone) {
      return new Response(JSON.stringify({ error: "phone is required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
    const planId = Deno.env.get("RAZORPAY_PLAN_ID");

    const missing: string[] = [];
    if (!supabaseUrl) missing.push("SUPABASE_URL");
    if (!serviceKey) missing.push("SERVICE_ROLE_KEY");
    if (!keyId) missing.push("RAZORPAY_KEY_ID");
    if (!keySecret) missing.push("RAZORPAY_KEY_SECRET");
    if (!planId) missing.push("RAZORPAY_PLAN_ID");

    if (missing.length > 0) {
      log("missing_env", { missing });
      return new Response(JSON.stringify({ error: "Server configuration missing", missing }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);

    const { data: row, error: userErr } = await supabase
      .from("users")
      .select("trial_paid, trial_end, razorpay_subscription_id")
      .eq("phone_number", phone)
      .maybeSingle();

    if (userErr) {
      return new Response(JSON.stringify({ error: userErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    if (!row) {
      return new Response(JSON.stringify({ error: "User not found" }), {
        status: 404,
        headers: jsonHeaders,
      });
    }
    if (!row.trial_paid) {
      return new Response(
        JSON.stringify({ error: "₹5 payment not verified. Verify payment first." }),
        { status: 403, headers: jsonHeaders },
      );
    }

    const existingSubId =
      typeof row.razorpay_subscription_id === "string"
        ? row.razorpay_subscription_id.trim()
        : "";
    if (existingSubId) {
      log("subscription_reused", { phone, subscription_id: existingSubId });
      return new Response(JSON.stringify({ subscription_id: existingSubId, reused: true }), {
        status: 200,
        headers: jsonHeaders,
      });
    }

    const trialEndIso = typeof row.trial_end === "string" ? row.trial_end : "";
    const trialEndMs = Date.parse(trialEndIso);
    if (!trialEndIso || Number.isNaN(trialEndMs)) {
      return new Response(
        JSON.stringify({ error: "trial_end missing. Start trial before creating subscription." }),
        { status: 400, headers: jsonHeaders },
      );
    }

    const startAtUnix = Math.max(Math.floor(trialEndMs / 1000), Math.floor(Date.now() / 1000) + 60);
    const auth = btoa(`${keyId}:${keySecret}`);
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

    const rzData = await rz.json();
    if (!rz.ok) {
      const msg =
        rzData?.error?.description ||
        rzData?.error?.message ||
        JSON.stringify(rzData);
      return new Response(JSON.stringify({ error: `Razorpay: ${msg}` }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    const subId = typeof rzData?.id === "string" ? rzData.id : "";
    if (!subId) {
      return new Response(JSON.stringify({ error: "No subscription id from Razorpay" }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    const { error: upErr } = await supabase
      .from("users")
      .update({
        razorpay_subscription_id: subId,
        subscription_status: "created",
        plan_id: planId,
      })
      .eq("phone_number", phone);

    if (upErr) {
      return new Response(JSON.stringify({ error: upErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    log("subscription_created", { phone, subscription_id: subId, start_at: startAtUnix });

    return new Response(JSON.stringify({ subscription_id: subId }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
