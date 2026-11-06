package com.squarenova.emaanwallpapers.subscription

import android.content.Context
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.UserAccountLookup
import com.squarenova.emaanwallpapers.data.UserAccountQueries
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionRepository
import com.squarenova.emaanwallpapers.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * The ONE source of truth for premium entitlement: the Cashfree `subscription-status` Edge
 * Function. Every "is premium" path funnels here (including
 * [com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager], which delegates to [refresh]).
 * This repository owns fetching, caching and the observable [premium] flow that UI can collect.
 *
 * Guarantees:
 *  - [refresh] is single-flight (a [Mutex]) so concurrent callers never race.
 *  - Never throws; on network failure it returns the cached value.
 */
object EntitlementRepository {

    data class RefreshResult(
        val hasPremium: Boolean,
        /** True when users table returned no row (account deleted). Not a network failure. */
        val accountMissing: Boolean = false,
    )

    @Serializable
    private data class UserIdRow(val id: String? = null)

    private const val TAG = "EntitlementRepository"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile
    private var dataStore: DataStoreManager? = null

    private val _premium = MutableStateFlow(false)
    /** Observable, app-wide premium flag. UI should collect this rather than re-computing. */
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    @Volatile
    var lastStatus: String? = null
        private set

    private val _subscription = MutableStateFlow<CashfreeSubscriptionRepository.SubscriptionStatus?>(null)
    /** Last Cashfree subscription-status snapshot (status, end_date, autopay…); null until fetched. */
    val subscription: StateFlow<CashfreeSubscriptionRepository.SubscriptionStatus?> = _subscription.asStateFlow()

    fun init(context: Context) {
        if (dataStore == null) {
            dataStore = DataStoreManager(context.applicationContext)
            scope.launch {
                _premium.value = readCache()
            }
        }
    }

    private suspend fun readCache(): Boolean = try {
        dataStore?.isSubscribed?.first() ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * The single premium refresh. Confirms the user row still exists, then reads premium from the
     * Cashfree `subscription-status` Edge Function, caches it and updates [premium].
     *
     * @return premium flag. Never throws.
     */
    suspend fun refreshDetailed(phone: String): RefreshResult = refreshLock.withLock {
        val cleanPhone = phone.trim()
        val cached = readCache()
        if (cleanPhone.isBlank()) return RefreshResult(hasPremium = cached)

        // Unreachable (offline) keeps cache. Missing (empty users row) is a deleted account.
        when (UserAccountQueries.lookupByPhone<UserIdRow>(cleanPhone)) {
            UserAccountLookup.Unreachable -> {
                SecureLog.w(TAG, "refresh inconclusive (offline) — keeping cached=$cached")
                return RefreshResult(hasPremium = cached)
            }
            UserAccountLookup.Missing -> {
                SecureLog.w(TAG, "refresh account missing — clearing cached premium")
                lastStatus = null
                _subscription.value = null
                persist(false)
                _premium.value = false
                return RefreshResult(hasPremium = false, accountMissing = true)
            }
            is UserAccountLookup.Found -> {
                // Cashfree only: premium comes from subscription-status, never from the users row.
                return CashfreeSubscriptionRepository.getSubscriptionStatus(cleanPhone).fold(
                    onSuccess = { status ->
                        lastStatus = status.status
                        _subscription.value = status
                        persist(status.isPremium)
                        _premium.value = status.isPremium
                        SecureLog.d(
                            TAG,
                            "refresh phone=${SecureLog.redactPhone(cleanPhone)} premium=${status.isPremium} status=${status.status}",
                        )
                        RefreshResult(hasPremium = status.isPremium)
                    },
                    onFailure = { e ->
                        SecureLog.w(TAG, "subscription-status failed, keeping cached=$cached: ${e.message}")
                        RefreshResult(hasPremium = cached)
                    },
                )
            }
        }
    }

    /** Convenience wrapper for callers that only need the premium boolean. */
    suspend fun refresh(phone: String): Boolean = refreshDetailed(phone).hasPremium

    /** Resets the in-memory premium flag on logout (the DataStore cache is cleared by logout()). */
    fun clearForLogout() {
        lastStatus = null
        _subscription.value = null
        _premium.value = false
    }

    private suspend fun persist(hasPremium: Boolean) {
        try {
            if (hasPremium) dataStore?.setSubscribed() else dataStore?.setUnsubscribed()
        } catch (e: Exception) {
            SecureLog.w(TAG, "persist failed: ${e.message}")
        }
    }
}
