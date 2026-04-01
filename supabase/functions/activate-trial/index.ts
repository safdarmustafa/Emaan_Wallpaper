import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };
const TRIAL_MS = 3 * 24 * 60 * 60 * 1000;

/** Razorpay may lag updating status after mandate; we allow these states for trial activation. */
const ALLOWED_TRIAL_STATUSES = new Set(["created", "authenticated", "active"]);

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

    if (!phone) {
      return new Response(JSON.stringify({ error: "phone is required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY")!;
    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

    if (!keyId || !keySecret) {
      return new Response(JSON.stringify({ error: "Razorpay not configured" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);

    const { data: row, error: fetchErr } = await supabase
      .from("users")
      .select("razorpay_subscription_id, subscription_status")
      .eq("phone_number", phone)
      .maybeSingle();

    if (fetchErr) {
      console.error("[activate-trial] user fetch error", fetchErr);
      return new Response(JSON.stringify({ error: fetchErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    if (!row) {
      console.warn("[activate-trial] user not found phone=", phone);
      return new Response(JSON.stringify({ error: "User not found" }), {
        status: 404,
        headers: jsonHeaders,
      });
    }

    const subId = row.razorpay_subscription_id;
    if (!subId || String(subId).trim() === "") {
      console.warn("[activate-trial] razorpay_subscription_id is null phone=", phone);
      return new Response(
        JSON.stringify({
          error: "razorpay_subscription_id is null — create subscription first",
        }),
        { status: 400, headers: jsonHeaders },
      );
    }

    const auth = btoa(`${keyId}:${keySecret}`);
    const rz = await fetch(`https://api.razorpay.com/v1/subscriptions/${subId}`, {
      headers: { Authorization: `Basic ${auth}` },
    });
    const subJson = await rz.json();

    if (!rz.ok) {
      const msg =
        subJson?.error?.description ||
        subJson?.error?.message ||
        JSON.stringify(subJson);
      console.error("[activate-trial] Razorpay GET subscription failed", msg);
      return new Response(JSON.stringify({ error: `Razorpay: ${msg}` }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    const status = subJson?.status as string | undefined;
    console.log(
      "[activate-trial] Razorpay subscription status=",
      status,
      "subId=",
      subId,
    );

    if (!status || !ALLOWED_TRIAL_STATUSES.has(status)) {
      console.warn(
        "[activate-trial] subscription status not eligible for trial:",
        status,
      );
      return new Response(
        JSON.stringify({
          error: `Subscription not eligible for trial (status: ${status ?? "unknown"})`,
          razorpay_status: status,
        }),
        { status: 403, headers: jsonHeaders },
      );
    }

    const trialEnd = new Date(Date.now() + TRIAL_MS).toISOString();

    const { data: updated, error: upErr } = await supabase
      .from("users")
      .update({
        is_subscribed: true,
        subscription_status: "trial",
        trial_paid: true,
        trial_end: trialEnd,
      })
      .eq("phone_number", phone)
      .select("phone_number");

    if (upErr) {
      console.error("[activate-trial] DB update error", upErr);
      return new Response(JSON.stringify({ error: upErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    if (!updated?.length) {
      console.error("[activate-trial] DB update returned no rows phone=", phone);
      return new Response(
        JSON.stringify({ error: "Update failed — no matching user row" }),
        { status: 400, headers: jsonHeaders },
      );
    }

    console.log(
      "[activate-trial] trial activated OK phone=",
      phone,
      "trial_end=",
      trialEnd,
    );

    return new Response(JSON.stringify({ ok: true, trial_end: trialEnd }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("[activate-trial] exception", e);
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
