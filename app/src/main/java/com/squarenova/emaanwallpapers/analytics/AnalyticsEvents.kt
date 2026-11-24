package com.squarenova.emaanwallpapers.analytics

/**
 * Canonical analytics event names.
 * Custom names are for Mixpanel only — MetaAnalyticsProvider.track() is a no-op.
 * Meta receives only standard ActivateApp / StartTrial / Purchase (via dedicated APIs).
 */
object AnalyticsEvents {

    const val SIGN_UP = "sign_up"
    const val LOGIN_SUCCESS = "login_success"
    const val SUBSCRIPTION_CANCELLED = "subscription_cancelled"
    const val TRIAL_EXPIRED = "trial_expired"

    /** Mixpanel: user sees the subscription / paywall screen. */
    const val SUBSCRIPTION_SCREEN_VIEWED = "subscription_screen_viewed"

    /** Mixpanel: first process launch on this device. Meta uses SDK ActivateApp for install. */
    const val APP_INSTALL = "App Install"

    /** Mixpanel: every cold start. Meta ActivateApp is automatic via Facebook SDK. */
    const val APP_OPEN = "App Open"

    /** Meta / Mixpanel StartTrial (app-side if used). Webhook money event is Purchase only. */
    const val START_TRIAL = "StartTrial"

    /** Meta Purchase for ₹3 AUTH is fired from cashfree-webhook only (not the app). */
    const val PURCHASE = "Purchase"

    /** Mixpanel: ₹3 trial paid (client-side, on UPI auth success). Meta Purchase is webhook-only. */
    const val TRIAL_PAID = "trial_paid"
}
