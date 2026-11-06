package com.squarenova.emaanwallpapers.subscription

import android.content.Context
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.MandateEntitlementResolver
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

/**
 * Subscription System V2 — the ONE source of truth for premium entitlement.
 *
 * Every previous "sync + is-premium" path funnels here (including [com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager],
 * which now delegates to [refresh]). The rules themselves stay in [SubscriptionEntitlement] (the
 * single pure calculation); this repository owns fetching, trial activation, caching and the
 * observable [premium] flow that UI can collect.
 *
 * Guarantees:
 *  - [refresh] is single-flight (a [Mutex]) so concurrent callers never double-activate or race.
 *  - Never throws; on network failure it returns the cached value.
 */
object EntitlementRepository {

    /** Result of a premium refresh; used by confirmation to avoid duplicate activate-trial calls. */
    data class RefreshResult(
        val hasPremium: Boolean,
        /** True when activate-trial succeeded during this refresh (server idempotent). */
        val activateTrialSucceeded: Boolean = false,
        /** True when users table returned no row (account deleted). Not a network failure. */
        val accountMissing: Boolean = false,
    )

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
     * @param skipActivateTrial unused with Cashfree; kept for the legacy Razorpay call sites.
     * @return premium flag. Never throws.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun refreshDetailed(
        phone: String,
        skipActivateTrial: Boolean = false,
    ): RefreshResult = refreshLock.withLock {
        val cleanPhone = phone.trim()
        val cached = readCache()
        if (cleanPhone.isBlank()) return RefreshResult(hasPremium = cached)

        // Unreachable (offline) keeps cache. Missing (empty users row) is a deleted account.
        when (val lookup = MandateEntitlementResolver.lookupRow(cleanPhone)) {
            MandateEntitlementResolver.RowLookup.Unreachable -> {
                SecureLog.w(TAG, "refresh inconclusive (offline) — keeping cached=$cached")
                return RefreshResult(hasPremium = cached)
            }
            MandateEntitlementResolver.RowLookup.Missing -> {
                SecureLog.w(TAG, "refresh account missing — clearing cached premium")
                lastStatus = null
                _subscription.value = null
                persist(false)
                _premium.value = false
                return RefreshResult(hasPremium = false, accountMissing = true)
            }
            is MandateEntitlementResolver.RowLookup.Found -> {
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

    /**
     * Convenience wrapper — callers outside confirmation only need the premium boolean.
     */
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
