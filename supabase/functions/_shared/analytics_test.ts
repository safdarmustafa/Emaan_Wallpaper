import {
  assertEquals,
  assertExists,
} from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  buildMetaPurchasePayload,
  moneyEventForKind,
  sendMetaCapiPurchase,
} from "./analytics.ts";
import {
  PLACEHOLDER_EXTINFO,
  type MetaDeviceMatchRow,
} from "./metaMatch.ts";

const GOOD_MADID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

function device(overrides: Partial<MetaDeviceMatchRow> = {}): MetaDeviceMatchRow {
  return {
    madid: GOOD_MADID,
    adTrackingEnabled: true,
    extinfo: [
      "a2",
      "com.squarenova.emaanwallpapers",
      "17",
      "1.2.3",
      "14",
      "Pixel",
      "en_IN",
      "IST",
      "",
      "1080",
      "2400",
      "2.75",
      "8",
      "128",
      "64",
      "Asia/Kolkata",
    ],
    clientUserAgent: "Dalvik/2.1.0",
    clientIp: "203.0.113.10",
    anonId: "anon-guid-1",
    ...overrides,
  };
}

Deno.test("moneyEventForKind: AUTH purchase, CHARGE skipped", () => {
  assertEquals(moneyEventForKind("AUTH")?.metaEventName, "Purchase");
  assertEquals(moneyEventForKind("AUTH")?.value, 3);
  assertEquals(moneyEventForKind("CHARGE"), null);
});

Deno.test("payload with device data includes madid anon ip ua tracking 1", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_1",
    value: 3,
    currency: "INR",
    phone: "9876543210",
    device: device(),
  });
  assertEquals(built.shouldSend, true);
  assertEquals(built.adTracking, 1);
  assertEquals(built.hasDevice, true);
  const event = built.body!.data[0];
  const userData = event.user_data as Record<string, unknown>;
  assertEquals(userData.madid, GOOD_MADID);
  assertEquals(userData.anon_id, "anon-guid-1");
  assertEquals(userData.client_ip_address, "203.0.113.10");
  assertEquals(userData.client_user_agent, "Dalvik/2.1.0");
  assertExists(userData.ph);
  const appData = event.app_data as Record<string, unknown>;
  assertEquals(appData.advertiser_tracking_enabled, 1);
  assertEquals(appData.application_tracking_enabled, 1);
  assertEquals((appData.extinfo as string[])[4], "14");
  assertEquals((event.custom_data as Record<string, unknown>).value, "3.00");
});

Deno.test("payload without device uses tracking 0 and placeholder extinfo", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_legacy",
    value: 3,
    currency: "INR",
    phone: "9876543210",
    device: null,
  });
  assertEquals(built.shouldSend, true);
  assertEquals(built.adTracking, 0);
  assertEquals(built.hasDevice, false);
  const userData = built.body!.data[0].user_data as Record<string, unknown>;
  assertEquals(userData.madid, undefined);
  assertEquals(userData.anon_id, undefined);
  assertExists(userData.ph);
  const appData = built.body!.data[0].app_data as Record<string, unknown>;
  assertEquals(appData.advertiser_tracking_enabled, 0);
  assertEquals(appData.extinfo, PLACEHOLDER_EXTINFO);
});

Deno.test("limited tracking → no madid and tracking 0", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_2",
    value: 3,
    currency: "INR",
    phone: null,
    device: device({ adTrackingEnabled: false, madid: GOOD_MADID }),
  });
  assertEquals(built.shouldSend, true); // still has anon_id
  assertEquals(built.adTracking, 0);
  const userData = built.body!.data[0].user_data as Record<string, unknown>;
  assertEquals(userData.madid, undefined);
  assertEquals(userData.anon_id, "anon-guid-1");
});

Deno.test("all-zero advertising id is dropped", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_3",
    value: 3,
    currency: "INR",
    phone: null,
    device: device({
      madid: "00000000-0000-0000-0000-000000000000",
      adTrackingEnabled: true,
    }),
  });
  assertEquals(built.adTracking, 0);
  const userData = built.body!.data[0].user_data as Record<string, unknown>;
  assertEquals(userData.madid, undefined);
});

Deno.test("malformed extinfo → placeholder", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_4",
    value: 3,
    currency: "INR",
    phone: null,
    device: device({ extinfo: ["a2", "only", "three"] }),
  });
  const appData = built.body!.data[0].app_data as Record<string, unknown>;
  assertEquals(appData.extinfo, PLACEHOLDER_EXTINFO);
});

Deno.test("device row with neither anon nor madid → no send", async () => {
  const built = await buildMetaPurchasePayload({
    eventName: "Purchase",
    eventId: "pay_5",
    value: 3,
    currency: "INR",
    phone: "9876543210",
    device: device({
      anonId: null,
      madid: null,
      adTrackingEnabled: false,
    }),
  });
  assertEquals(built.shouldSend, false);
  assertEquals(built.reason, "no_anon_or_madid");
  assertEquals(built.body, null);
});

Deno.test("sendMetaCapiPurchase: missing token/dataset → false no throw", async () => {
  const prevToken = Deno.env.get("META_CAPI_ACCESS_TOKEN");
  const prevDataset = Deno.env.get("META_DATASET_ID");
  Deno.env.delete("META_CAPI_ACCESS_TOKEN");
  Deno.env.delete("META_DATASET_ID");
  try {
    const ok = await sendMetaCapiPurchase({
      eventName: "Purchase",
      eventId: "pay_x",
      value: 3,
      currency: "INR",
      phone: null,
      device: device(),
      fetchImpl: () => {
        throw new Error("should not fetch");
      },
    });
    assertEquals(ok, false);
  } finally {
    if (prevToken) Deno.env.set("META_CAPI_ACCESS_TOKEN", prevToken);
    else Deno.env.delete("META_CAPI_ACCESS_TOKEN");
    if (prevDataset) Deno.env.set("META_DATASET_ID", prevDataset);
    else Deno.env.delete("META_DATASET_ID");
  }
});

Deno.test("sendMetaCapiPurchase: network / 401 / events_received 0 → false", async () => {
  Deno.env.set("META_CAPI_ACCESS_TOKEN", "test-token");
  Deno.env.set("META_DATASET_ID", "test-dataset");
  try {
    const network = await sendMetaCapiPurchase({
      eventName: "Purchase",
      eventId: "pay_net",
      value: 3,
      currency: "INR",
      phone: null,
      device: device(),
      fetchImpl: () => Promise.reject(new Error("network down")),
    });
    assertEquals(network, false);

    const unauthorized = await sendMetaCapiPurchase({
      eventName: "Purchase",
      eventId: "pay_401",
      value: 3,
      currency: "INR",
      phone: null,
      device: device(),
      fetchImpl: () =>
        Promise.resolve(
          new Response(JSON.stringify({ error: { message: "unauthorized" } }), {
            status: 401,
          }),
        ),
    });
    assertEquals(unauthorized, false);

    const zero = await sendMetaCapiPurchase({
      eventName: "Purchase",
      eventId: "pay_0",
      value: 3,
      currency: "INR",
      phone: null,
      device: device(),
      fetchImpl: () =>
        Promise.resolve(
          new Response(JSON.stringify({ events_received: 0 }), { status: 200 }),
        ),
    });
    assertEquals(zero, false);

    const ok = await sendMetaCapiPurchase({
      eventName: "Purchase",
      eventId: "pay_ok",
      value: 3,
      currency: "INR",
      phone: null,
      device: device(),
      fetchImpl: () =>
        Promise.resolve(
          new Response(JSON.stringify({ events_received: 1 }), { status: 200 }),
        ),
    });
    assertEquals(ok, true);
  } finally {
    Deno.env.delete("META_CAPI_ACCESS_TOKEN");
    Deno.env.delete("META_DATASET_ID");
  }
});
