import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };

/**
 * Statuses that already carry a LIVE mandate and must NEVER be reopened in Razorpay Checkout —
 * Checkout rejects them with "The id provided does not exist". These ids are still returned so the
 * client can confirm entitlement, but with checkout_required=false.
 *   authenticated → mandate approved, trial not yet activated (client calls activate-trial)
 *   active        → subscription live (user already premium)
 *   pending       → live subscription whose latest charge is retrying (do NOT re-mandate)
 */
const NON_CHECKOUT_LIVE_STATUSES = new Set(["authenticated", "active", "pending"]);

/** Terminal Razorpay statuses — always mint a fresh subscription instead of reusing. */
const TERMINAL_STATUSES = new Set(["cancelled", "completed", "expired", "halted"]);

const clog = (step: string, meta: Record<string, unknown> = {}) =>
  console.log(JSON.stringify({ fn: "create-subscription", step, ...meta }));

/** Never logs the secret. Key IDs are public (shipped in the APK), so masking is belt-and-suspenders. */
const maskKeyId = (k?: string | null) =>
  !k ? "null" : k.length <= 12 ? "****" : `${k.slice(0, 12)}…${k.slice(-4)}`;

/** Derives test/live purely from the key-id prefix — the crux of "id does not exist" diagnosis. */
const keyMode = (k?: string | null) =>
  !k
    ? "unknown"
    : k.startsWith("rzp_live_")
      ? "live"
      : k.startsWith("rzp_test_")
        ? "test"
        : "unknown";

async function fetchRazorpaySubscriptionStatus(
  subId: string,
  auth: string,
): Promise<
  | { ok: true; status: string; httpStatus: number }
  | { ok: false; error: string; httpStatus: number }
> {
  const rz = await fetch(`https://api.razorpay.com/v1/subscriptions/${subId}`, {
    headers: { Authorization: `Basic ${auth}` },
  });
  const rzData = await rz.json();
  // TEMP DIAGNOSTIC: full GET response (no secrets — rzData is the subscription/error object).
  clog("razorpay_lookup_response", {
    subscription_id: subId,
    http_status: rz.status,
    ok: rz.ok,
    response: rzData,
  });
  if (!rz.ok) {
    const msg =
      rzData?.error?.description ||
      rzData?.error?.message ||
      JSON.stringify(rzData);
    return { ok: false, error: msg, httpStatus: rz.status };
  }
  const status = typeof rzData?.status === "string" ? rzData.status.trim().toLowerCase() : "";
  if (!status) {
    return { ok: false, error: "No status in Razorpay subscription response", httpStatus: rz.status };
  }
  return { ok: true, status, httpStatus: rz.status };
}

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
    const auth = btoa(`${keyId}:${keySecret}`);

    // TEMP DIAGNOSTIC: which Razorpay account/mode is the BACKEND operating in? A subscription is
    // only visible to the same key (account + test/live) that created it. If this mode differs from
    // the Android client's BuildConfig RAZORPAY_KEY_ID, Checkout raises "The id provided does not exist".
    clog("razorpay_env", {
      key_mode: keyMode(keyId),
      key_id_masked: maskKeyId(keyId),
      plan_id: planId,
    });

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
      const rzLookup = await fetchRazorpaySubscriptionStatus(existingSubId, auth);

      // TEMP DIAGNOSTIC: is the returned id coming from DB reuse, and does it still exist on THIS key?
      clog("reuse_lookup", {
        phone,
        existing_subscription_id: existingSubId,
        key_mode: keyMode(keyId),
        http_status: rzLookup.httpStatus,
        result: rzLookup.ok
          ? { status: rzLookup.status, checkoutable: rzLookup.status === "created" }
          : { error: rzLookup.error },
      });

      if (rzLookup.ok) {
        const rzStatus = rzLookup.status;

        // Already has a live mandate (authenticated/active/pending) — reuse the id but NEVER reopen
        // Checkout (Razorpay would reject: "The id provided does not exist"). The client confirms
        // entitlement instead of launching mandate checkout again.
        if (NON_CHECKOUT_LIVE_STATUSES.has(rzStatus)) {
          log("subscription_live_no_checkout", {
            phone,
            subscription_id: existingSubId,
            razorpay_status: rzStatus,
          });
          return new Response(
            JSON.stringify({
              subscription_id: existingSubId,
              reused: true,
              checkout_required: false,
              already_active: rzStatus === "active",
              razorpay_status: rzStatus,
            }),
            { status: 200, headers: jsonHeaders },
          );
        }

        // Only a freshly created subscription (mandate not yet approved) may be reopened in Checkout.
        if (rzStatus === "created") {
          log("subscription_reused", {
            phone,
            subscription_id: existingSubId,
            razorpay_status: rzStatus,
          });
          return new Response(
            JSON.stringify({
              subscription_id: existingSubId,
              reused: true,
              checkout_required: true,
              razorpay_status: rzStatus,
            }),
            { status: 200, headers: jsonHeaders },
          );
        }

        if (TERMINAL_STATUSES.has(rzStatus)) {
          log("subscription_terminal_create_new", {
            phone,
            old_subscription_id: existingSubId,
            razorpay_status: rzStatus,
          });
        } else {
          // Unknown / transitional status — do not reuse; mint a fresh subscription.
          log("subscription_unknown_status_create_new", {
            phone,
            old_subscription_id: existingSubId,
            razorpay_status: rzStatus,
          });
        }
      } else {
        // Subscription missing or unreadable at Razorpay — mint a fresh one.
        log("subscription_lookup_failed_create_new", {
          phone,
          old_subscription_id: existingSubId,
          error: rzLookup.error,
        });
      }
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
    const createRequestBody = {
      plan_id: planId,
      total_count: 120,
      customer_notify: 1,
      start_at: startAtUnix,
      notes: { phone, source: "emaan_wallpapers_app" },
    };

    // TEMP DIAGNOSTIC: full create request (no secrets) + which mode it is being created under.
    clog("razorpay_create_request", {
      key_mode: keyMode(keyId),
      key_id_masked: maskKeyId(keyId),
      request: createRequestBody,
    });

    const rz = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(createRequestBody),
    });

    const rzData = await rz.json();
    // TEMP DIAGNOSTIC: full create response. rzData holds the new subscription (id/status/plan_id) or
    // the Razorpay error — no secrets. Compare rzData.id's mode against the client's checkout key.
    clog("razorpay_create_response", {
      http_status: rz.status,
      ok: rz.ok,
      response: rzData,
    });
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
        is_subscribed: false,
        plan_id: planId,
      })
      .eq("phone_number", phone);

    if (upErr) {
      return new Response(JSON.stringify({ error: upErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    log("subscription_created", {
      phone,
      subscription_id: subId,
      start_at: startAtUnix,
      replaced_subscription_id: existingSubId || null,
    });

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
