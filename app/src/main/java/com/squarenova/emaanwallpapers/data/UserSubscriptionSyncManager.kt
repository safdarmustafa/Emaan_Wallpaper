package com.squarenova.emaanwallpapers.data

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import kotlinx.coroutines.flow.first

class UserSubscriptionSyncManager(
    private val dataStoreManager: DataStoreManager
) {
    companion object {
        private const val TAG = "USER_SUB_SYNC"
    }

    /**
     * Syncs premium entitlement from Supabase to DataStore cache.
     * Returns whether the user has premium access for the current session.
     * Never throws — on failure returns the cached DataStore value.
     */
    suspend fun syncUserSubscription(phone: String): Boolean {
        val cached = try {
            dataStoreManager.isSubscribed.first()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "syncUserSubscription: could not read DataStore cache", e)
            }
            false
        }

        val cleanPhone = phone.trim()
        if (cleanPhone.isBlank()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "syncUserSubscription: empty phone, using cached=$cached")
            }
            return cached
        }

        return try {
            var row = MandateEntitlementResolver.fetchRow(cleanPhone)

            val initialPremium = SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = row?.subscription_status,
                trialEndIso = row?.trial_end,
            )
            MandateDebugLog.entitlementCheck(
                source = "syncUserSubscription:initial",
                subscriptionStatus = row?.subscription_status,
                trialPaid = row?.trial_paid,
                trialEnd = row?.trial_end,
                hasPremiumAccess = initialPremium,
            )

            row = MandateEntitlementResolver.ensureTrialActivatedIfNeeded(cleanPhone, row)

            val hasPremium = MandateEntitlementResolver.hasPremium(row)

            MandateDebugLog.syncResult(
                phone = cleanPhone,
                hasPremium = hasPremium,
                subscriptionStatus = row?.subscription_status,
                trialEnd = row?.trial_end,
            )

            if (hasPremium) {
                dataStoreManager.setSubscribed()
            } else {
                dataStoreManager.setUnsubscribed()
            }
            hasPremium
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "syncUserSubscription: network/error fallback to cached=$cached, error=${e.message}"
                )
            }
            MandateDebugLog.note("syncUserSubscription error=${e.message} fallback cached=$cached")
            cached
        }
    }
}
