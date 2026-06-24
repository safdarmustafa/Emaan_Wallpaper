import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };
const MAX_ATTEMPTS = 5;

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
    const otp = typeof body.otp === "string" ? body.otp.replace(/\D/g, "") : "";
    if (phone.length !== 10 || otp.length !== 6) {
      return new Response(JSON.stringify({ error: "Invalid phone or OTP" }), {
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

    const supabase = createClient(supabaseUrl, serviceKey);
    const { data: row, error: fetchErr } = await supabase
      .from("otp_challenges")
      .select("otp_hash, expires_at, attempts")
      .eq("phone_number", phone)
      .maybeSingle();

    if (fetchErr || !row) {
      return new Response(JSON.stringify({ error: "OTP expired or not found" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    if (row.attempts >= MAX_ATTEMPTS) {
      return new Response(JSON.stringify({ error: "Too many attempts. Request a new OTP." }), {
        status: 429,
        headers: jsonHeaders,
      });
    }

    if (new Date(row.expires_at).getTime() < Date.now()) {
      await supabase.from("otp_challenges").delete().eq("phone_number", phone);
      return new Response(JSON.stringify({ error: "OTP expired" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const expectedHash = await sha256Hex(`${phone}:${otp}`);
    if (expectedHash !== row.otp_hash) {
      await supabase
        .from("otp_challenges")
        .update({ attempts: (row.attempts ?? 0) + 1 })
        .eq("phone_number", phone);
      return new Response(JSON.stringify({ error: "Invalid OTP" }), {
        status: 401,
        headers: jsonHeaders,
      });
    }

    await supabase.from("otp_challenges").delete().eq("phone_number", phone);

    return new Response(JSON.stringify({ verified: true }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("verify-otp", e);
    return new Response(JSON.stringify({ error: "Internal error" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
