// @ts-nocheck

import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

serve(async () => {

  const keyId = Deno.env.get("RAZORPAY_KEY_ID");
  const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

  const auth = btoa(`${keyId}:${keySecret}`);

  const res = await fetch("https://api.razorpay.com/v1/orders", {
    method: "POST",
    headers: {
      "Authorization": `Basic ${auth}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      amount: 500, // ₹5
      currency: "INR",
      receipt: "trial_" + Date.now()
    })
  });

  const data = await res.json();

  return new Response(JSON.stringify({
    order_id: data.id
  }), { status: 200 });
});