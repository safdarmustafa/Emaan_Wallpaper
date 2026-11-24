package com.squarenova.emaanwallpapers.analytics

import com.squarenova.emaanwallpapers.subscription.SubscriptionPricing

/**
 * Mirrors server [supabase/functions/_shared/analytics.ts] moneyEventForKind.
 * Client does not fire these for Cashfree; tests lock the contract.
 */
data class MoneyAnalyticsEvent(
    val eventName: String,
    val value: Double,
    val currency: String,
)

object MoneyAnalytics {
    fun forPaymentKind(kind: String): MoneyAnalyticsEvent? {
        return when (kind.uppercase()) {
            "AUTH" -> MoneyAnalyticsEvent(
                eventName = AnalyticsEvents.PURCHASE,
                value = SubscriptionPricing.AUTHORIZATION_AMOUNT.toDouble(),
                currency = SubscriptionPricing.CURRENCY,
            )
            // ₹499 CHARGE: no Meta / Mixpanel money event from webhook.
            "CHARGE" -> null
            else -> null
        }
    }
}
