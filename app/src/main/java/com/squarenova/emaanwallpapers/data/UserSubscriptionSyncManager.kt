package com.squarenova.emaanwallpapers.data

import android.util.Log
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

class UserSubscriptionSyncManager(
    private val dataStoreManager: DataStoreManager
) {
    companion object {
        private const val TAG = "USER_SUB_SYNC"
    }

    @Serializable
    private data class SubscriptionSyncRow(
        val is_subscribed: Boolean? = false
    )

    /**
     * Syncs subscription flag from Supabase (source of truth) to DataStore cache.
     * Returns the final value that app should use for current session.
     */
    suspend fun syncUserSubscription(phone: String): Boolean {
        val cleanPhone = phone.trim()
        if (cleanPhone.isBlank()) {
            val cached = dataStoreManager.isSubscribed.first()
            Log.d(TAG, "syncUserSubscription: empty phone, using cached=$cached")
            return cached
        }

        return try {
            Log.d(TAG, "syncUserSubscription: fetching users.is_subscribed for phone=$cleanPhone")
            val row = SupabaseClient.client
                .postgrest["users"]
                .select {
                    filter { eq("phone_number", cleanPhone) }
                }
                .decodeList<SubscriptionSyncRow>()
                .firstOrNull()

            val subscribed = row?.is_subscribed == true
            if (subscribed) {
                dataStoreManager.setSubscribed()
                Log.d(TAG, "syncUserSubscription: server=true, DataStore updated to subscribed")
            } else {
                dataStoreManager.setUnsubscribed()
                Log.d(TAG, "syncUserSubscription: server=false, DataStore updated to unsubscribed")
            }
            subscribed
        } catch (e: Exception) {
            val cached = dataStoreManager.isSubscribed.first()
            Log.d(
                TAG,
                "syncUserSubscription: network/error fallback to cached=$cached, error=${e.message}"
            )
            cached
        }
    }
}
