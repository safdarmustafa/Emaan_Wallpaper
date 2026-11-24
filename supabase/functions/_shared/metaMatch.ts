/**
 * Meta CAPI device match helpers (madid / extinfo / phone hash).
 * Pure functions — safe to unit-test without network or DB.
 */

export const META_ANDROID_PACKAGE = "com.squarenova.emaanwallpapers";

/** Placeholder extinfo when the device row is missing or malformed. Slot 4 = OS (never blank). */
export const PLACEHOLDER_EXTINFO: string[] = [
  "a2",
  META_ANDROID_PACKAGE,
  "",
  "",
  "13",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
  "",
];

const MADID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export interface MetaDeviceMatchRow {
  madid: string | null;
  adTrackingEnabled: boolean;
  extinfo: string[] | null;
  clientUserAgent: string | null;
  clientIp: string | null;
  anonId: string | null;
}

/** Returns madid only when tracking is allowed and the id is a non-zero UUID. */
export function usableAdId(
  madid: string | null | undefined,
  adTrackingEnabled: boolean,
): string | null {
  if (!adTrackingEnabled) return null;
  const id = (madid ?? "").trim().toLowerCase();
  if (!id) return null;
  if (!MADID_REGEX.test(id)) return null;
  if (/^[0-]+$/.test(id.replace(/-/g, ""))) return null;
  return id;
}

/** Keep a 16-slot Android extinfo only when slot 0 is a2 and OS (slot 4) is non-blank. */
export function sanitizeExtinfo(raw: unknown): string[] | null {
  if (!Array.isArray(raw) || raw.length !== 16) return null;
  const slots = raw.map((v) => {
    if (typeof v === "string") return v.slice(0, 100);
    if (typeof v === "number" || typeof v === "boolean") return String(v).slice(0, 100);
    return "";
  });
  if (slots[0] !== "a2") return null;
  if (!slots[4] || !slots[4].trim()) return null;
  return slots;
}

export function resolveExtinfo(device: MetaDeviceMatchRow | null): string[] {
  const fromDevice = device ? sanitizeExtinfo(device.extinfo) : null;
  return fromDevice ?? PLACEHOLDER_EXTINFO;
}

/** Last 10 digits; null if fewer than 10. */
export function last10PhoneDigits(phone: string | null | undefined): string | null {
  if (!phone) return null;
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 10) return null;
  return digits.slice(-10);
}

/** Meta expects E.164 without +: India = "91" + 10 digits, then SHA-256 hex. */
export async function hashPhoneForMeta(
  phone: string | null | undefined,
): Promise<string | null> {
  const last10 = last10PhoneDigits(phone);
  if (!last10) return null;
  const data = new TextEncoder().encode(`91${last10}`);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function sanitizeMadidForStorage(
  madid: unknown,
  adTrackingEnabled: boolean,
): string | null {
  if (!adTrackingEnabled) return null;
  if (typeof madid !== "string") return null;
  return usableAdId(madid, true);
}

export function sanitizeAnonId(anonId: unknown): string | null {
  if (typeof anonId !== "string") return null;
  const trimmed = anonId.trim();
  if (!trimmed) return null;
  return trimmed.slice(0, 100);
}

export function sanitizeUserAgent(ua: unknown): string | null {
  if (typeof ua !== "string") return null;
  const trimmed = ua.trim();
  if (!trimmed) return null;
  return trimmed.slice(0, 300);
}

/** First hop of X-Forwarded-For, validated as IPv4/IPv6 text. */
export function clientIpFromForwardedFor(header: string | null): string | null {
  if (!header) return null;
  const first = header.split(",")[0]?.trim() ?? "";
  if (!first) return null;
  // Loose validation: reject empty / spaces; prefer dotted or colon forms.
  if (/\s/.test(first)) return null;
  if (!/^[\d.:a-fA-F]+$/.test(first)) return null;
  return first.slice(0, 64);
}

export function clampEventTimeSeconds(unixSeconds: number, now = Date.now()): number {
  const nowSec = Math.floor(now / 1000);
  const min = nowSec - 7 * 24 * 60 * 60;
  if (!Number.isFinite(unixSeconds)) return nowSec;
  if (unixSeconds > nowSec) return nowSec;
  if (unixSeconds < min) return min;
  return Math.floor(unixSeconds);
}
