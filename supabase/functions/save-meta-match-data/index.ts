/**
 * Persists Meta advertising match keys for the signed-in phone user.
 * Called from the Android app (fire-and-forget) so Cashfree webhooks can
 * attribute Purchase events when the app is closed.
 *
 * Auth model matches other Emaan functions: phone in body + anon JWT gateway.
 * Writes use the service role (meta_match_data has RLS and no policies).
 */

import { createClient } from "npm:@supabase/supabase-js@2";
import { handleCorsPreflight } from "../_shared/cors.ts";
import { ERROR_CODES } from "../_shared/errors.ts";
import { requireEnv } from "../_shared/env.ts";
import { errorResponse, successResponse } from "../_shared/response.ts";
import { validateIndianPhone } from "../_shared/validation.ts";
import {
  clientIpFromForwardedFor,
  sanitizeAnonId,
  sanitizeExtinfo,
  sanitizeMadidForStorage,
  sanitizeUserAgent,
} from "../_shared/metaMatch.ts";

interface Body {
  phone?: unknown;
  anon_id?: unknown;
  madid?: unknown;
  ad_tracking_enabled?: unknown;
  extinfo?: unknown;
  user_agent?: unknown;
}

function createServiceClient() {
  return createClient(
    requireEnv("SUPABASE_URL"),
    requireEnv("SUPABASE_SERVICE_ROLE_KEY"),
  );
}

Deno.serve(async (req) => {
  const preflight = handleCorsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== "POST") {
    return errorResponse(ERROR_CODES.METHOD_NOT_ALLOWED);
  }

  let body: Body;
  try {
    body = await req.json();
  } catch {
    return errorResponse(ERROR_CODES.BAD_REQUEST, "Invalid JSON body");
  }

  const phoneResult = validateIndianPhone(body.phone);
  if (!phoneResult.valid || !phoneResult.normalized) {
    return errorResponse(ERROR_CODES.PHONE_INVALID);
  }

  const tracking = body.ad_tracking_enabled === true;
  const madid = sanitizeMadidForStorage(body.madid, tracking);
  const extinfo = sanitizeExtinfo(body.extinfo);
  const anonId = sanitizeAnonId(body.anon_id);
  const userAgent = sanitizeUserAgent(body.user_agent);
  const clientIp = clientIpFromForwardedFor(
    req.headers.get("x-forwarded-for"),
  );

  try {
    const supabase = createServiceClient();
    const { data: user, error: lookupError } = await supabase
      .from("users")
      .select("id")
      .eq("phone_number", phoneResult.normalized)
      .maybeSingle();

    if (lookupError) {
      console.error("save-meta-match-data user lookup failed", lookupError.message);
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }
    if (!user?.id) {
      return errorResponse(ERROR_CODES.BAD_REQUEST, "User not found");
    }

    const { error: upsertError } = await supabase.from("meta_match_data").upsert(
      {
        user_id: user.id,
        madid,
        // Persist the client's flag even when madid is null so a later
        // limited-tracking save clears a previously stored advertising id.
        ad_tracking_enabled: tracking,
        extinfo,
        client_user_agent: userAgent,
        client_ip: clientIp,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id" },
    );

    if (upsertError) {
      console.error("save-meta-match-data upsert failed", upsertError.message);
      return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
    }

    if (anonId) {
      const { error: anonError } = await supabase
        .from("users")
        .update({ meta_anon_id: anonId })
        .eq("id", user.id);
      if (anonError) {
        console.error("save-meta-match-data anon update failed", anonError.message);
        // Match row already saved; do not fail the client for anon-only issues.
      }
    }

    console.info("save-meta-match-data ok", {
      hasMadid: madid != null,
      hasAnon: anonId != null,
      hasExtinfo: extinfo != null,
      tracking,
    });

    return successResponse("Meta match data saved.", {
      saved: true,
      has_madid: madid != null,
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("save-meta-match-data unexpected", detail);
    return errorResponse(ERROR_CODES.INTERNAL_SERVER_ERROR);
  }
});
