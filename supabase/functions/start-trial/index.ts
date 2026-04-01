import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };
const TRIAL_MS = 3 * 24 * 60 * 60 * 1000;

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: jsonHeaders,
    });
  }

  try {
    const log = (step: string, meta: Record<string, unknown> = {}) =>
      console.log(JSON.stringify({ fn: "start-trial", step, ...meta }));
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
    if (!supabaseUrl || !serviceKey) {
      return new Response(JSON.stringify({ error: "Server configuration missing" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const trialEnd = new Date(Date.now() + TRIAL_MS).toISOString();
    const supabase = createClient(supabaseUrl, serviceKey);

    const { data: row, error: fetchErr } = await supabase
      .from("users")
      .select("trial_paid, trial_end")
      .eq("phone_number", phone)
      .maybeSingle();

    if (fetchErr) {
      return new Response(JSON.stringify({ error: fetchErr.message }), {
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
        JSON.stringify({ error: "Eligible user not found (verify ₹5 payment first)" }),
        { status: 400, headers: jsonHeaders },
      );
    }
    if (row.trial_end) {
      log("trial_already_exists_skip", { phone });
      return new Response(JSON.stringify({ ok: true, skipped: true, trial_end: row.trial_end }), {
        status: 200,
        headers: jsonHeaders,
      });
    }

    const { data, error } = await supabase
      .from("users")
      .update({
        is_subscribed: true,
        subscription_status: "trial",
        trial_end: trialEnd,
      })
      .eq("phone_number", phone)
      .select("phone_number")
      .limit(1);

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    if (!data?.length) {
      return new Response(
        JSON.stringify({ error: "Trial activation failed" }),
        { status: 400, headers: jsonHeaders },
      );
    }

    log("trial_activated", { phone, trial_end: trialEnd });
    return new Response(JSON.stringify({ ok: true, trial_end: trialEnd }), {
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
