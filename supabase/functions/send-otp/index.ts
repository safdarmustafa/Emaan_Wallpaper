import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };
const OTP_TTL_MS = 5 * 60 * 1000;

async function sha256Hex(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: jsonHeaders,
    });
  }

  try {
    const body = await req.json();
    const phone = typeof body.phone === "string" ? body.phone.replace(/\D/g, "") : "";
    if (phone.length !== 10) {
      return new Response(JSON.stringify({ error: "Valid 10-digit phone required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SERVICE_ROLE_KEY");
    const fast2smsKey = Deno.env.get("FAST2SMS_API_KEY");
    if (!supabaseUrl || !serviceKey || !fast2smsKey) {
      return new Response(JSON.stringify({ error: "Server configuration missing" }), {
        status: 503,
        headers: jsonHeaders,
      });
    }

    const otp = String(Math.floor(100000 + Math.random() * 900000));
    const otpHash = await sha256Hex(`${phone}:${otp}`);
    const expiresAt = new Date(Date.now() + OTP_TTL_MS).toISOString();

    const supabase = createClient(supabaseUrl, serviceKey);
    const { error: upsertErr } = await supabase.from("otp_challenges").upsert({
      phone_number: phone,
      otp_hash: otpHash,
      expires_at: expiresAt,
      attempts: 0,
      created_at: new Date().toISOString(),
    });
    if (upsertErr) {
      return new Response(JSON.stringify({ error: upsertErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    const message = `Your OTP for Emaan Wallpapers is ${otp}`;
    const form = new URLSearchParams();
    form.set("message", message);
    form.set("numbers", phone);
    form.set("sender_id", Deno.env.get("FAST2SMS_SENDER_ID") ?? "SPCTEK");
    form.set("pe_id", Deno.env.get("FAST2SMS_PE_ID") ?? "1201176779722977287");
    form.set("template_id", Deno.env.get("FAST2SMS_TEMPLATE_ID") ?? "1207176874999607244");
    form.set("route", "q");
    form.set("language", "english");

    const smsRes = await fetch("https://www.fast2sms.com/dev/bulkV2", {
      method: "POST",
      headers: {
        authorization: fast2smsKey,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: form.toString(),
    });
    if (!smsRes.ok) {
      const smsBody = await smsRes.text();
      console.error("Fast2SMS error", smsRes.status, smsBody.slice(0, 200));
      return new Response(JSON.stringify({ error: "Failed to send OTP" }), {
        status: 502,
        headers: jsonHeaders,
      });
    }

    return new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("send-otp", e);
    return new Response(JSON.stringify({ error: "Internal error" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
