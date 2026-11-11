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
                eventName = AnalyticsEvents.START_TRIAL,
                value = SubscriptionPricing.AUTHORIZATION_AMOUNT.toDouble(),
                currency = SubscriptionPricing.CURRENCY,
            )
            "CHARGE" -> MoneyAnalyticsEvent(
                eventName = AnalyticsEvents.PURCHASE,
                value = SubscriptionPricing.MONTHLY_AMOUNT,
                currency = SubscriptionPricing.CURRENCY,
            )
            else -> null
        }
    }
}
