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

    /** Meta + Mixpanel standard StartTrial. */
    const val START_TRIAL = "StartTrial"

    /** Mixpanel Purchase; Meta uses logPurchase(). */
    const val PURCHASE = "Purchase"
}
