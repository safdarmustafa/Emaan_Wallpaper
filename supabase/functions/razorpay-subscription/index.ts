// @ts-nocheck
import { serve } from "https://deno.land/std@0.192.0/http/server.ts";

serve(async (req: Request) => {

  if (req.method === "OPTIONS") {
    return new Response("OK", {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Content-Type, Authorization",
        "Access-Control-Allow-Methods": "POST, OPTIONS"
      }
    });
  }

  try {

    const { plan_id } = await req.json();

    const keyId = Deno.env.get("RAZORPAY_KEY_ID");
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");

    const encodedAuth = btoa(`${keyId}:${keySecret}`);

    const response = await fetch("https://api.razorpay.com/v1/subscriptions", {
      method: "POST",
      headers: {
        "Authorization": `Basic ${encodedAuth}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        plan_id: plan_id,
        total_count: 12,
        customer_notify: 1
      })
    });

    const result = await response.json();

    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500
    });
  }
});