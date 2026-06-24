package com.squarenova.emaanwallpapers.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTimestampParserTest {

    @Test
    fun parsesPostgresSpaceSeparatedTimestamp() {
        val ms = SupabaseTimestampParser.parseToEpochMillis("2026-06-27 13:12:35.476")
        assertNotNull(ms)
        assertTrue(SupabaseTimestampParser.isInFuture("2026-06-27 13:12:35.476"))
    }

    @Test
    fun parsesIsoTimestamp() {
        val ms = SupabaseTimestampParser.parseToEpochMillis("2026-06-27T13:12:35.476Z")
        assertNotNull(ms)
    }

    @Test
    fun trialEntitlementWithPostgresTimestamp() {
        assertTrue(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "trial",
                trialEndIso = "2026-06-27 13:12:35.476",
            )
        )
        assertFalse(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "trial",
                trialEndIso = "2020-01-01 00:00:00",
            )
        )
    }

    @Test
    fun activeDoesNotDependOnTrialEnd() {
        assertTrue(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "active",
                trialEndIso = null,
            )
        )
    }

    @Test
    fun authenticatedWithFutureTrialEndIsNotPremiumWithoutTrialStatus() {
        assertFalse(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "authenticated",
                trialEndIso = "2026-06-27 13:12:35.476",
            )
        )
    }

    @Test
    fun cancelRequestedWithFutureTrialEndIsPremium() {
        assertTrue(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "cancel_requested",
                trialEndIso = "2026-06-27 13:12:35.476",
            )
        )
    }

    @Test
    fun cancelRequestedWithExpiredTrialEndIsNotPremium() {
        assertFalse(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "cancel_requested",
                trialEndIso = "2020-01-01 00:00:00",
            )
        )
    }

    @Test
    fun expiredStatusIsNotPremium() {
        assertFalse(
            SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = "expired",
                trialEndIso = "2026-06-27 13:12:35.476",
            )
        )
    }

    @Test
    fun needsTrialActivationWhenAuthenticatedAndTrialPaid() {
        assertTrue(
            MandateEntitlementResolver.needsTrialActivation(
                MandateEntitlementResolver.EntitlementRow(
                    subscription_status = "authenticated",
                    trial_paid = true,
                    trial_end = "2026-06-27 13:12:35.476",
                )
            )
        )
    }
}
