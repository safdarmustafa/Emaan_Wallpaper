package com.squarenova.emaanwallpapers.subscription

/**
 * Product pricing for the Cashfree subscription plan (₹499/month).
 * Single source for analytics Purchase amount/currency and billing UI.
 */
object SubscriptionPricing {

    /** Monthly plan charge after the authorization (rupees). */
    const val MONTHLY_AMOUNT = 499.0

    /** UPI Autopay authorization charge; must match the backend CASHFREE_AUTHORIZATION_AMOUNT secret. */
    const val AUTHORIZATION_AMOUNT = 3

    const val CURRENCY = "INR"
}
