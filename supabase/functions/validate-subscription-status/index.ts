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
      console.log(JSON.stringify({ fn: "validate-subscription-status", step, ...meta }));
    const body = await req.json();
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    const inputSubId =
      typeof body.subscription_id === "string" ? body.subscription_id.trim() : "";

    if (!phone && !inputSubId) {
      return new Response(JSON.stringify({ error: "phone or subscription_id is required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
    if (!supabaseUrl || !serviceKey || !keyId || !keySecret) {
      return new Response(JSON.stringify({ error: "Server configuration missing" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);
    let subId = inputSubId;
    let whereColumn = "razorpay_subscription_id";
    let whereValue = inputSubId;
    if (!subId && phone) {
      const { data: userRow, error: userErr } = await supabase
        .from("users")
        .select("razorpay_subscription_id, subscription_status, trial_end")
        .eq("phone_number", phone)
        .maybeSingle();
      if (userErr) {
        return new Response(JSON.stringify({ error: userErr.message }), {
          status: 500,
          headers: jsonHeaders,
        });
      }
      subId =
        typeof userRow?.razorpay_subscription_id === "string"
          ? userRow.razorpay_subscription_id.trim()
          : "";
      whereColumn = "phone_number";
      whereValue = phone;

      const isTrial = (userRow?.subscription_status ?? "") === "trial";
      const trialEndIso = typeof userRow?.trial_end === "string" ? userRow.trial_end : "";
      const trialEndMs = Date.parse(trialEndIso);
      if (isTrial && trialEndIso && !Number.isNaN(trialEndMs) && Date.now() > trialEndMs) {
        await supabase
          .from("users")
          .update({
            is_subscribed: false,
            subscription_status: "expired",
          })
          .eq("phone_number", phone);
        log("trial_expired_transition", { phone, trial_end: trialEndIso });
      }
    }

    if (!subId) {
      return new Response(JSON.stringify({ ok: true, has_subscription: false }), {
        status: 200,
        headers: jsonHeaders,
      });
    }

    const auth = btoa(`${keyId}:${keySecret}`);
    const rz = await fetch(`https://api.razorpay.com/v1/subscriptions/${subId}`, {
      headers: { Authorization: `Basic ${auth}` },
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

    const rzStatus = typeof rzData?.status === "string" ? rzData.status : "unknown";
    const currentEnd =
      typeof rzData?.current_end === "number"
        ? new Date(rzData.current_end * 1000).toISOString()
        : null;

    let patch: Record<string, unknown> = {};
    if (rzStatus === "active") {
      patch = { is_subscribed: true, subscription_status: "active" };
      if (currentEnd) patch.current_period_end = currentEnd;
    } else if (rzStatus === "cancelled") {
      patch = { is_subscribed: false, subscription_status: "cancelled" };
    } else if (rzStatus === "halted" || rzStatus === "completed" || rzStatus === "expired") {
      patch = { is_subscribed: false, subscription_status: "expired" };
    } else if (rzStatus === "authenticated") {
      patch = { subscription_status: "authenticated", is_subscribed: false };
    } else if (rzStatus === "created") {
      patch = { subscription_status: "created", is_subscribed: false };
    }

    if (Object.keys(patch).length > 0) {
      await supabase
        .from("users")
        .update(patch)
        .eq(whereColumn, whereValue);
    }

    log("validated", { phone, subscription_id: subId, razorpay_status: rzStatus });

    return new Response(
      JSON.stringify({
        ok: true,
        has_subscription: true,
        subscription_id: subId,
        razorpay_status: rzStatus,
        is_mandate_approved: rzStatus === "authenticated" || rzStatus === "active",
      }),
      { status: 200, headers: jsonHeaders },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
