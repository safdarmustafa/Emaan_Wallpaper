/**
 * Minimal Mixpanel HTTP track helper for Edge Functions.
 * Token must come from Deno.env MIXPANEL_TOKEN — never hardcode.
 * Failures are logged and swallowed by callers so payment processing is never blocked.
 */

export type MixpanelPurchaseInput = {
  distinctId: string;
  insertId: string;
  subscriptionId: string;
  paymentId: string | null;
  amount: number;
  currency: string;
  billingType: string;
  razorpayEventId: string | null;
  currentPeriodEnd: string | null;
};

/**
 * Sends a Mixpanel "Purchase" event via the public track API.
 * Uses $insert_id for native Mixpanel deduplication.
 */
export async function trackMixpanelPurchase(
  input: MixpanelPurchaseInput,
): Promise<void> {
  const token = Deno.env.get("MIXPANEL_TOKEN")?.trim() ?? "";
  if (!token) {
    console.warn(
      "mixpanel: MIXPANEL_TOKEN not set — skipping Purchase (subscription processing unaffected)",
    );
    return;
  }

  if (!input.distinctId.trim()) {
    console.warn("mixpanel: missing distinct_id — skipping Purchase");
    return;
  }
  if (!(input.amount > 0) || !input.currency.trim()) {
    console.warn(
      "mixpanel: missing amount/currency from Razorpay payload — skipping Purchase",
    );
    return;
  }

  const properties: Record<string, unknown> = {
    token,
    distinct_id: input.distinctId.trim(),
    time: Math.floor(Date.now() / 1000),
    $insert_id: input.insertId,
    amount: input.amount,
    currency: input.currency.trim().toUpperCase(),
    subscription_id: input.subscriptionId,
    billing_type: input.billingType,
    platform: "server",
    source: "razorpay_webhook",
  };

  if (input.paymentId) properties.payment_id = input.paymentId;
  if (input.razorpayEventId) properties.razorpay_event_id = input.razorpayEventId;
  if (input.currentPeriodEnd) {
    properties.current_period_end = input.currentPeriodEnd;
  }

  const body = JSON.stringify([
    {
      event: "Purchase",
      properties,
    },
  ]);

  const response = await fetch("https://api.mixpanel.com/track", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/plain",
    },
    body,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    console.error(
      `mixpanel: track failed status=${response.status} body=${text.slice(0, 200)}`,
    );
  }
}
