import {
  assertEquals,
  assertExists,
} from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  clientIpFromForwardedFor,
  hashPhoneForMeta,
  PLACEHOLDER_EXTINFO,
  resolveExtinfo,
  sanitizeExtinfo,
  sanitizeMadidForStorage,
  usableAdId,
} from "./metaMatch.ts";

Deno.test("usableAdId rejects limited tracking and zero ids", () => {
  assertEquals(usableAdId("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false), null);
  assertEquals(usableAdId("00000000-0000-0000-0000-000000000000", true), null);
  assertEquals(usableAdId("not-a-uuid", true), null);
  assertEquals(usableAdId("", true), null);
  assertEquals(
    usableAdId("A1B2C3D4-E5F6-7890-ABCD-EF1234567890", true),
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  );
});

Deno.test("sanitizeExtinfo requires a2 and non-blank OS", () => {
  const good = Array.from({ length: 16 }, (_, i) => (i === 0 ? "a2" : i === 4 ? "14" : ""));
  assertEquals(sanitizeExtinfo(good)?.[4], "14");
  assertEquals(sanitizeExtinfo(["a2", "", "", "", ""]), null);
  assertEquals(sanitizeExtinfo(["i2", "", "", "", "14"]), null);
  assertEquals(sanitizeExtinfo(["a2", "pkg"]), null);
  assertEquals(resolveExtinfo(null), PLACEHOLDER_EXTINFO);
  assertEquals(PLACEHOLDER_EXTINFO.length, 16);
  assertEquals(PLACEHOLDER_EXTINFO[0], "a2");
  assertEquals(PLACEHOLDER_EXTINFO[4], "13");
});

Deno.test("sanitizeMadidForStorage clears when tracking off", () => {
  assertEquals(
    sanitizeMadidForStorage("a1b2c3d4-e5f6-7890-abcd-ef1234567890", false),
    null,
  );
  assertEquals(
    sanitizeMadidForStorage("a1b2c3d4-e5f6-7890-abcd-ef1234567890", true),
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  );
});

Deno.test("phone hash uses 91 + last 10 digits", async () => {
  const a = await hashPhoneForMeta("+91 98765-43210");
  const b = await hashPhoneForMeta("9876543210");
  const c = await hashPhoneForMeta("919876543210");
  assertExists(a);
  assertEquals(a, b);
  assertEquals(a, c);
  assertEquals(a!.length, 64);
  assertEquals(await hashPhoneForMeta("12345"), null);
  assertEquals(await hashPhoneForMeta(null), null);
});

Deno.test("clientIpFromForwardedFor takes first hop", () => {
  assertEquals(clientIpFromForwardedFor("1.2.3.4, 5.6.7.8"), "1.2.3.4");
  assertEquals(clientIpFromForwardedFor("  10.0.0.1  "), "10.0.0.1");
  assertEquals(clientIpFromForwardedFor(null), null);
  assertEquals(clientIpFromForwardedFor("not an ip"), null);
});
