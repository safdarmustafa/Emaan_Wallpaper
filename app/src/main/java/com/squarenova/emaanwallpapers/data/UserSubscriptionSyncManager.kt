package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.subscription.EntitlementRepository

/**
 * Thin compatibility shim. Historically this class owned its own fetch + activate + entitlement
 * logic; that logic is now consolidated in [EntitlementRepository] (Subscription System V2's single
 * source of truth). Existing call sites keep working by delegating here.
 *
 * @param dataStoreManager retained for source compatibility; the repository owns caching now.
 */
class UserSubscriptionSyncManager(
    @Suppress("UNUSED_PARAMETER") private val dataStoreManager: DataStoreManager,
) {
    /**
     * Syncs premium entitlement from Supabase and returns whether the user has premium access.
     * Never throws — on failure returns the cached value. See [EntitlementRepository.refresh].
     */
    suspend fun syncUserSubscription(phone: String): Boolean =
        EntitlementRepository.refresh(phone)
}
