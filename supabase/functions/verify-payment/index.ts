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
      console.log(JSON.stringify({ fn: "verify-payment", step, ...meta }));
    const body = await req.json();
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    const paymentId = typeof body.payment_id === "string" ? body.payment_id.trim() : "";
    const orderId = typeof body.order_id === "string" ? body.order_id.trim() : "";

    if (!phone || !paymentId || !orderId) {
      return new Response(
        JSON.stringify({ error: "phone, payment_id and order_id are required" }),
        { status: 400, headers: jsonHeaders },
      );
    }

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");

    if (!keyId || !keySecret || !supabaseUrl || !serviceKey) {
      return new Response(JSON.stringify({ error: "Server configuration missing" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const auth = btoa(`${keyId}:${keySecret}`);
    const paymentRes = await fetch(`https://api.razorpay.com/v1/payments/${paymentId}`, {
      headers: { Authorization: `Basic ${auth}` },
    });
    const paymentJson = await paymentRes.json();

    if (!paymentRes.ok) {
      const msg =
        paymentJson?.error?.description ||
        paymentJson?.error?.message ||
        JSON.stringify(paymentJson);
      return new Response(JSON.stringify({ error: `Razorpay: ${msg}` }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    const paymentOrderId = typeof paymentJson?.order_id === "string" ? paymentJson.order_id : "";
    const paymentStatus = typeof paymentJson?.status === "string" ? paymentJson.status : "";
    const amount = typeof paymentJson?.amount === "number" ? paymentJson.amount : 0;
    const currency = typeof paymentJson?.currency === "string" ? paymentJson.currency : "";

    if (paymentOrderId !== orderId) {
      log("verify_failed_order_mismatch", { phone, payment_id: paymentId, order_id: orderId });
      return new Response(JSON.stringify({ error: "Payment order_id mismatch" }), {
        status: 403,
        headers: jsonHeaders,
      });
    }
    if (paymentStatus !== "captured") {
      log("verify_failed_not_captured", { phone, payment_id: paymentId, status: paymentStatus });
      return new Response(
        JSON.stringify({ error: `Payment not captured (status: ${paymentStatus || "unknown"})` }),
        { status: 403, headers: jsonHeaders },
      );
    }
    if (amount !== 300 || currency !== "INR") {
      log("verify_failed_amount", { phone, payment_id: paymentId, amount, currency });
      return new Response(JSON.stringify({ error: "Invalid payment amount or currency" }), {
        status: 403,
        headers: jsonHeaders,
      });
    }

    const supabase = createClient(supabaseUrl, serviceKey);
    const { data: replayRow, error: replayErr } = await supabase
      .from("users")
      .select("phone_number")
      .eq("razorpay_payment_id", paymentId)
      .maybeSingle();
    if (replayErr) {
      return new Response(JSON.stringify({ error: replayErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    if (replayRow) {
      log("verify_replay_attempt", { phone, payment_id: paymentId });
      return new Response(JSON.stringify({ error: "Payment replay detected" }), {
        status: 409,
        headers: jsonHeaders,
      });
    }

    const { data: selfRow, error: selfErr } = await supabase
      .from("users")
      .select("razorpay_payment_id")
      .eq("phone_number", phone)
      .maybeSingle();
    if (selfErr) {
      return new Response(JSON.stringify({ error: selfErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    if (
      typeof selfRow?.razorpay_payment_id === "string" &&
      selfRow.razorpay_payment_id.trim() !== ""
    ) {
      log("verify_replay_self_existing_payment", {
        phone,
        existing_payment_id: selfRow.razorpay_payment_id,
        attempted_payment_id: paymentId,
      });
      return new Response(JSON.stringify({ error: "Entry fee already verified for this account" }), {
        status: 409,
        headers: jsonHeaders,
      });
    }

    const { error: upErr } = await supabase
      .from("users")
      .update({
        trial_paid: true,
        razorpay_payment_id: paymentId,
      })
      .eq("phone_number", phone);

    if (upErr) {
      return new Response(JSON.stringify({ error: upErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
    log("verify_success", { phone, payment_id: paymentId, order_id: orderId });

    return new Response(JSON.stringify({ ok: true }), {
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
