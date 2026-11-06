/**
 * Single Premium rule shared by subscription-status, create-subscription and
 * cashfree-webhook.
 *
 * subscriptions.end_date is the ONLY paid-through source:
 *   - ₹3 authorization  → end_date = end of first ₹499 charge day + grace (trial)
 *   - ₹499 charge       → end_date = end of next billing day + grace
 *   - nothing collected → end_date passes → not Premium
 *
 * status only says what the mandate is doing; it never grants Premium alone.
 */

/** Covers UPI Autopay retries (1 + 3 hourly) and webhook delivery delay. */
export const PREMIUM_GRACE_MS = 6 * 60 * 60 * 1000;

/** Recurring amount of the Emaan Monthly plan (INR). */
export const MONTHLY_CHARGE_AMOUNT = 499;

/** Used only when no first-charge date is known for a ₹3 authorization. */
export const FALLBACK_TRIAL_MS = 24 * 60 * 60 * 1000;

/** Asia/Kolkata has no DST. */
const IST_OFFSET_MS = (5 * 60 + 30) * 60 * 1000;

/** 23:59:59.999 IST of the calendar day (IST) that contains [date]. */
export function endOfIstDay(date: Date): Date {
  const ist = new Date(date.getTime() + IST_OFFSET_MS);
  const endUtcWall = Date.UTC(
    ist.getUTCFullYear(),
    ist.getUTCMonth(),
    ist.getUTCDate(),
    23,
    59,
    59,
    999,
  );
  return new Date(endUtcWall - IST_OFFSET_MS);
}

/**
 * Paid-through for a debit scheduled at [chargeTime]. Cashfree schedules at
 * ~00:10 IST but debits later that day (and UPI retries hourly), so Premium
 * runs through the whole IST charge day plus grace.
 */
export function chargeDayEndWithGrace(chargeTime: Date): Date {
  return new Date(endOfIstDay(chargeTime).getTime() + PREMIUM_GRACE_MS);
}

/** Parses an ISO / date-like string; null when blank or invalid. */
export function parseDate(value: unknown): Date | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const ms = Date.parse(trimmed);
  return Number.isNaN(ms) ? null : new Date(ms);
}

/** True when [endDate] parses to a time strictly after [now]. */
export function isBeforeEndDate(endDate: unknown, now: Date = new Date()): boolean {
  const end = parseDate(endDate);
  return end !== null && now.getTime() < end.getTime();
}

/**
 * Read-time Premium gate. Only active / cancelled mandates can be Premium, and
 * only while end_date is still in the future.
 */
export function computeIsPremium(
  status: unknown,
  endDate: unknown,
  now: Date = new Date(),
): boolean {
  if (status !== "active" && status !== "cancelled") return false;
  return isBeforeEndDate(endDate, now);
}

/** Same day-of-month one calendar month later (clamped, e.g. 31 Jan → 28 Feb). */
export function addOneCalendarMonth(from: Date): Date {
  const result = new Date(from.getTime());
  const day = result.getUTCDate();
  result.setUTCDate(1);
  result.setUTCMonth(result.getUTCMonth() + 1);
  const lastDay = new Date(
    Date.UTC(result.getUTCFullYear(), result.getUTCMonth() + 1, 0),
  ).getUTCDate();
  result.setUTCDate(Math.min(day, lastDay));
  return result;
}

/** Later of two dates; a null side loses. */
export function laterOf(a: Date | null, b: Date | null): Date | null {
  if (!a) return b;
  if (!b) return a;
  return a.getTime() >= b.getTime() ? a : b;
}
