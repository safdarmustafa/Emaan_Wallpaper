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

    /** Mixpanel: first process launch on this device. Meta uses SDK ActivateApp for install. */
    const val APP_INSTALL = "App Install"

    /** Mixpanel: every cold start. Meta ActivateApp is automatic via Facebook SDK. */
    const val APP_OPEN = "App Open"

    /** Meta + Mixpanel standard StartTrial (₹3 AUTH). Fired from cashfree-webhook. */
    const val START_TRIAL = "StartTrial"

    /** Mixpanel Purchase / Meta logPurchase (₹499 CHARGE). Fired from cashfree-webhook. */
    const val PURCHASE = "Purchase"
}
