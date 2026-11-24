# Meta (Facebook) Purchase events — Emaan Wallpapers

## App / SDK

- Meta App ID: configured in `strings.xml` → `facebook_app_id` (manifest `com.facebook.sdk.ApplicationId`)
- Meta Client Token: `facebook_client_token` (manifest `com.facebook.sdk.ClientToken`)
- `AdvertiserIDCollectionEnabled` = true; `AD_ID` permission present
- App collects match keys via `MetaDeviceInfo` → `save-meta-match-data` on login, splash (signed-in), cold start, and checkout

## What is sent

| Event | Amount | When | From |
|---|---|---|---|
| `Purchase` | ₹3.00 INR | Cashfree **AUTH** (trial / UPI mandate auth) succeeds and the webhook wins the `last_payment_id` insert | Edge Function `cashfree-webhook` → `_shared/analytics.ts` → Meta Conversions API |
| ₹499 **CHARGE** | — | Monthly AutoPay debit | **Not sent** to Meta (product choice; Mixpanel also skipped) |

Endpoint: `POST https://graph.facebook.com/v21.0/{META_DATASET_ID}/events`  
`action_source`: `app`  
Secrets: `META_CAPI_ACCESS_TOKEN`, `META_DATASET_ID` (no `test_event_code` on production).

### `user_data` (optional keys only when present)

- `anon_id` — Facebook SDK anonymous app device GUID (`users.meta_anon_id`)
- `madid` — Advertising ID only when tracking allowed and id is a non-zero UUID
- `client_ip_address` / `client_user_agent` — from the user’s last saved device row (never the server’s)
- `ph` — `[SHA-256("91" + last 10 phone digits)]`

### `app_data`

- `advertiser_tracking_enabled`: `1` only when a usable `madid` is sent, else `0`
- `application_tracking_enabled`: `1`
- `extinfo`: 16 Android slots (`a2`…); OS version (slot 4) never blank. Malformed → placeholder with package `com.squarenova.emaanwallpapers` and OS `"13"`.

### Send rules

- **Device row present:** send only if `anon_id` **or** usable `madid`.
- **No device row (older app):** legacy send with tracking `0`, placeholder `extinfo`, hashed phone — so the server can deploy before the app release.
- 3 s timeout; never throws into the payment / webhook flow.
- Success = HTTP 200 and `events_received ≥ 1`.
- Log per send: `eventId`, `sent`, `hasDevice`, `adTracking`.

## Where device data is stored

1. Android collects via `MetaDeviceInfo` (off main thread).
2. App POSTs to Edge Function `save-meta-match-data` (phone + match keys), unawaited, at:
   - after OTP sign-in
   - app cold start (if a phone is in DataStore)
   - when checkout / create-subscription is started
3. Rows live in `public.meta_match_data` (+ `users.meta_anon_id`). RLS on, **no policies** — only service role.

## How to verify

1. Events Manager → Test events (App): install build, open once, send one CAPI event with that device’s saved row + test code.
2. After release (daily):
   - device rows by app build (`extinfo[3]` = versionName)
   - share with `ad_tracking_enabled = true` / usable madid
   - payers since release vs Ads Manager attributed purchases
3. Expect most new payers to carry an advertising id; Ads Manager should start crediting purchases (sister app: 0/39 → 8/9 within two days).

## Privacy

We link advertising id to a phone-backed account and share advertising id, IP, and user agent with Meta for advertising measurement. Update the in-app privacy policy and Play Data safety form before release (Device or other IDs; Approximate location from IP; App info; shared with Meta).
