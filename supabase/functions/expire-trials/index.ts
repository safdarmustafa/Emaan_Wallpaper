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
      console.log(JSON.stringify({ fn: "expire-trials", step, ...meta }));
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceKey) {
      return new Response(JSON.stringify({ error: "Server configuration missing" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);
    const nowIso = new Date().toISOString();
    const { data, error } = await supabase
      .from("users")
      .update({
        is_subscribed: false,
        subscription_status: "expired",
      })
      .eq("subscription_status", "trial")
      .lt("trial_end", nowIso)
      .select("phone_number, trial_end");

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    const transitioned = data?.length ?? 0;
    if (transitioned > 0) {
      log("trial_expiry_batch_transition", { transitioned });
    }

    return new Response(JSON.stringify({ ok: true, transitioned }), {
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
