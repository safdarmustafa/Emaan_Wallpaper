import { serve } from "https://deno.land/std@0.192.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json" };

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
    if (phone.length !== 10) {
      return new Response(JSON.stringify({ error: "Valid 10-digit phone required" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }
    if (otp.length !== 6) {
      return new Response(JSON.stringify({ error: "OTP required to confirm deletion" }), {
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

    const { data: challenge, error: otpErr } = await supabase
      .from("otp_challenges")
      .select("otp_hash, expires_at, attempts")
      .eq("phone_number", phone)
      .maybeSingle();

    if (otpErr || !challenge) {
      return new Response(JSON.stringify({ error: "Request a new OTP to confirm deletion" }), {
        status: 400,
        headers: jsonHeaders,
      });
    }

    const expectedHash = await sha256Hex(`${phone}:${otp}`);
    if (expectedHash !== challenge.otp_hash || new Date(challenge.expires_at).getTime() < Date.now()) {
      return new Response(JSON.stringify({ error: "Invalid or expired OTP" }), {
        status: 401,
        headers: jsonHeaders,
      });
    }

    await supabase.from("otp_challenges").delete().eq("phone_number", phone);

    const { data: user, error: fetchErr } = await supabase
      .from("users")
      .select("id, avatar_url, razorpay_subscription_id, subscription_status")
      .eq("phone_number", phone)
      .maybeSingle();

    if (fetchErr) {
      return new Response(JSON.stringify({ error: fetchErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    if (!user) {
      return new Response(JSON.stringify({ ok: true, deleted: false, message: "No account found" }), {
        status: 200,
        headers: jsonHeaders,
      });
    }

    // Best-effort: cancel active Razorpay subscription before deletion
  if (user.razorpay_subscription_id &&
      ["trial", "active", "authenticated", "created", "cancel_requested"].includes(
        (user.subscription_status ?? "").toLowerCase(),
      )) {
      try {
        const keyId = Deno.env.get("RAZORPAY_KEY_ID");
        const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
        if (keyId && keySecret) {
          const auth = btoa(`${keyId}:${keySecret}`);
          await fetch(
            `https://api.razorpay.com/v1/subscriptions/${user.razorpay_subscription_id}/cancel`,
            {
              method: "POST",
              headers: {
                Authorization: `Basic ${auth}`,
                "Content-Type": "application/json",
              },
              body: JSON.stringify({ cancel_at_cycle_end: 0 }),
            },
          );
        }
      } catch (e) {
        console.warn("Razorpay cancel during delete failed", e);
      }
    }

    if (user.avatar_url) {
      try {
        const path = user.avatar_url.split("/storage/v1/object/public/avatars/").pop();
        if (path) {
          await supabase.storage.from("avatars").remove([path]);
        }
      } catch (e) {
        console.warn("avatar delete failed", e);
      }
    }

    await supabase.from("otp_challenges").delete().eq("phone_number", phone);

    const { error: deleteErr } = await supabase
      .from("users")
      .delete()
      .eq("phone_number", phone);

    if (deleteErr) {
      return new Response(JSON.stringify({ error: deleteErr.message }), {
        status: 500,
        headers: jsonHeaders,
      });
    }

    return new Response(JSON.stringify({ ok: true, deleted: true }), {
      status: 200,
      headers: jsonHeaders,
    });
  } catch (e) {
    console.error("delete-user-data", e);
    return new Response(JSON.stringify({ error: "Internal error" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }
});
