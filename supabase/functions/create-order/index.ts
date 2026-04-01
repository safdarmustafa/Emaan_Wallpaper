import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

const jsonHeaders = { "Content-Type": "application/json" };

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204 });
  }

  try {
    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
    if (!keyId || !keySecret) {
      console.error("create-order: RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET missing");
      return new Response(
        JSON.stringify({ error: "Server payment configuration missing" }),
        { status: 503, headers: jsonHeaders },
      );
    }

    const auth = btoa(`${keyId}:${keySecret}`);
    const res = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        Authorization: `Basic ${auth}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount: 500, // ₹5 — paise
        currency: "INR",
        receipt: `entry_${Date.now()}`,
      }),
    });

    const data = await res.json();

    if (!res.ok) {
      const msg =
        data?.error?.description ||
        data?.error?.message ||
        (typeof data?.error === "string" ? data.error : null) ||
        JSON.stringify(data);
      console.error("create-order Razorpay HTTP error:", res.status, msg);
      return new Response(JSON.stringify({ error: msg }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    if (!data?.id) {
      console.error("create-order: no id in response", data);
      return new Response(
        JSON.stringify({ error: "No order_id from Razorpay" }),
        { status: 502, headers: jsonHeaders },
      );
    }

    return new Response(JSON.stringify({ order_id: data.id }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("create-order exception", e);
    return new Response(
      JSON.stringify({ error: String(e?.message ?? e) }),
      { status: 500, headers: jsonHeaders },
    );
  }
});
