package com.squarenova.emaanwallpapers.data

/**
 * Single source of truth for in-app premium access.
 *
 * Premium is granted for:
 * - [subscription_status] == "active"
 * - [subscription_status] == "trial" with a future [trialEndIso]
 * - [subscription_status] == "cancel_requested" with a future [trialEndIso] (access until trial ends)
 * - [subscription_status] == "cancelled" with a future [trialEndIso] (access until trial ends)
 *
 * Does not depend on is_subscribed or trial_paid.
 */
object SubscriptionEntitlement {

    fun hasPremiumAccess(
        subscriptionStatus: String?,
        trialEndIso: String? = null,
        @Suppress("UNUSED_PARAMETER") isSubscribedLegacy: Boolean? = null,
    ): Boolean {
        val status = subscriptionStatus?.trim()?.lowercase().orEmpty()
        val trialStillActive = SupabaseTimestampParser.isInFuture(trialEndIso)
        val result = when (status) {
            "active" -> true
            "trial", "cancel_requested", "cancelled" -> trialStillActive
            else -> false
        }
        EntitlementDebugLog.check(
            subscriptionStatus = subscriptionStatus,
            trialEnd = trialEndIso,
            hasPremiumAccess = result,
        )
        return result
    }
}
