package com.squarenova.emaanwallpapers.subscription

import android.content.Context
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.MandateDebugLog
import com.squarenova.emaanwallpapers.data.MandateEntitlementResolver
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
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
     * The single premium refresh. Fetches the user row, activates the trial if the mandate is
     * authenticated-but-not-yet-trial, computes entitlement, caches it and updates [premium].
     *
     * @return true if the user currently has premium access. Never throws.
     */
    suspend fun refresh(phone: String): Boolean = refreshLock.withLock {
        val cleanPhone = phone.trim()
        val cached = readCache()
        if (cleanPhone.isBlank()) return cached

        // Fetch is INCONCLUSIVE when it returns null. A logged-in user always has a row, so a null
        // here means offline / transient / RLS failure — NOT "user is not premium". We must never
        // downgrade a previously-granted entitlement on an inconclusive read, otherwise a paid user
        // who opens the app offline would be wrongly paywalled and their cache clobbered to false.
        val row = MandateEntitlementResolver.fetchRow(cleanPhone)
        if (row == null) {
            SecureLog.w(TAG, "refresh inconclusive (no row / offline) — keeping cached=$cached")
            return cached
        }

        return try {
            val finalRow = MandateEntitlementResolver.ensureTrialActivatedIfNeeded(cleanPhone, row) ?: row

            val hasPremium = SubscriptionEntitlement.hasPremiumAccess(
                subscriptionStatus = finalRow.subscription_status,
                trialEndIso = finalRow.trial_end,
            )
            lastStatus = finalRow.subscription_status

            MandateDebugLog.syncResult(
                phone = cleanPhone,
                hasPremium = hasPremium,
                subscriptionStatus = finalRow.subscription_status,
                trialEnd = finalRow.trial_end,
            )

            persist(hasPremium)
            _premium.value = hasPremium
            SecureLog.d(TAG, "refresh phone=${SecureLog.redactPhone(cleanPhone)} premium=$hasPremium status=${finalRow.subscription_status}")
            hasPremium
        } catch (e: Exception) {
            SecureLog.w(TAG, "refresh compute/activate failed, keeping cached=$cached: ${e.message}")
            cached
        }
    }

    /** Resets the in-memory premium flag on logout (the DataStore cache is cleared by logout()). */
    fun clearForLogout() {
        lastStatus = null
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
