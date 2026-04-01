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
    const subscription_id =
      typeof body.subscription_id === "string" ? body.subscription_id.trim() : "";

    if (!subscription_id) {
      return new Response(JSON.stringify({ error: "subscription_id required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, serviceKey);

    const { data: rows, error: lookupErr } = await supabase
      .from("users")
      .select("phone_number, subscription_status")
      .eq("razorpay_subscription_id", subscription_id)
      .limit(1);

    if (lookupErr) {
      console.error("cancel-subscription lookup", lookupErr);
      return new Response(JSON.stringify({ error: lookupErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    if (!rows?.length) {
      return new Response(
        JSON.stringify({
          error:
            "No account matches this subscription. Complete signup and payment in the app first.",
        }),
        { status: 404, headers: jsonHeaders },
      );
    }

    const subStatus = (rows[0].subscription_status ?? "").toLowerCase();

    if (subStatus === "trial") {
      const cancelledAt = new Date().toISOString();
      const { error: dbErr } = await supabase
        .from("users")
        .update({
          subscription_status: "cancel_requested",
          cancelled_at: cancelledAt,
        })
        .eq("razorpay_subscription_id", subscription_id);

      if (dbErr) {
        return new Response(JSON.stringify({ error: dbErr.message }), {
          status: 500,
          headers: jsonHeaders,
        });
      }
      return new Response(JSON.stringify({ success: true, mode: "trial_local_cancel" }), {
        status: 200,
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

    const auth = btoa(`${keyId}:${keySecret}`);
    const rz = await fetch(
      `https://api.razorpay.com/v1/subscriptions/${subscription_id}/cancel`,
      {
        method: "POST",
        headers: {
          Authorization: `Basic ${auth}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ cancel_at_cycle_end: 1 }),
      },
    );

    const rzData = await rz.json();

    if (!rz.ok) {
      const razorpayMsg =
        rzData?.error?.description ||
        rzData?.error?.message ||
        (typeof rzData?.error === "string" ? rzData.error : null) ||
        rzData?.message ||
        JSON.stringify(rzData);
      console.error("Razorpay cancel failed", razorpayMsg);
      return new Response(JSON.stringify({ error: `Razorpay: ${razorpayMsg}` }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    const { error: dbError, data: updated } = await supabase
      .from("users")
      .update({ subscription_status: "cancel_requested" })
      .eq("razorpay_subscription_id", subscription_id)
      .select("phone_number");

    if (dbError) {
      return new Response(JSON.stringify({ error: dbError.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    if (!updated?.length) {
      return new Response(JSON.stringify({ error: "Database update failed" }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    return new Response(JSON.stringify({ success: true, mode: "razorpay_cancel" }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("cancel-subscription", e);
    return new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
