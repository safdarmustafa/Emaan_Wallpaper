package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.network.SubscriptionApi
import kotlinx.serialization.Serializable

/**
 * Resolves premium entitlement after mandate authentication.
 *
 * Razorpay/webhook/validate-subscription-status set [subscription_status] to "authenticated".
 * Premium is only granted for "trial" (future trial_end) or "active".
 * [SubscriptionApi.activateTrial] transitions authenticated → trial in the database.
 */
object MandateEntitlementResolver {

    @Serializable
    data class EntitlementRow(
        val is_subscribed: Boolean? = false,
        val subscription_status: String? = null,
        val trial_end: String? = null,
        val trial_paid: Boolean? = false,
    )

    sealed class RowLookup {
        data class Found(val row: EntitlementRow) : RowLookup()
        data object Missing : RowLookup()
        data object Unreachable : RowLookup()
    }

    suspend fun lookupRow(phone: String): RowLookup {
        return when (val result = UserAccountQueries.lookupByPhone<EntitlementRow>(phone)) {
            is UserAccountLookup.Found -> RowLookup.Found(result.row)
            UserAccountLookup.Missing -> RowLookup.Missing
            UserAccountLookup.Unreachable -> RowLookup.Unreachable
        }
    }

    suspend fun fetchRow(phone: String): EntitlementRow? =
        when (val result = lookupRow(phone)) {
            is RowLookup.Found -> result.row
            RowLookup.Missing, RowLookup.Unreachable -> null
        }

    fun needsTrialActivation(row: EntitlementRow?): Boolean {
        if (row?.trial_paid != true) return false
        return row.subscription_status?.trim()?.lowercase() == "authenticated"
    }

    /** Outcome of [ensureTrialActivatedIfNeeded] for confirmation-flow deduplication. */
    data class TrialActivationResult(
        val row: EntitlementRow?,
        /** True only when activate-trial returned success (idempotent server-side). */
        val activateTrialSucceeded: Boolean,
    )

    /**
     * If mandate is authenticated but DB is not yet "trial", call activate-trial.
     * @param skipActivateTrial when true, reads entitlement only — used when activate-trial already
     *   succeeded earlier in the same confirmation flow (avoids duplicate edge/Razorpay calls).
     */
    suspend fun ensureTrialActivatedIfNeeded(
        phone: String,
        row: EntitlementRow?,
        skipActivateTrial: Boolean = false,
    ): TrialActivationResult {
        if (skipActivateTrial || !needsTrialActivation(row)) {
            return TrialActivationResult(row, activateTrialSucceeded = false)
        }

        MandateDebugLog.note(
            "authenticated+trial_paid detected — calling activate-trial to set subscription_status=trial"
        )
        MandateDebugLog.activateTrialRequest(phone)

        val result = SubscriptionApi.activateTrial(phone)
        result.fold(
            onSuccess = { trialEnd ->
                MandateDebugLog.activateTrialResponse(phone, success = true, trialEnd = trialEnd, error = null)
            },
            onFailure = { e ->
                MandateDebugLog.activateTrialResponse(
                    phone,
                    success = false,
                    trialEnd = null,
                    error = e.message,
                )
            }
        )

        return TrialActivationResult(
            row = if (result.isSuccess) fetchRow(phone) else row,
            activateTrialSucceeded = result.isSuccess,
        )
    }

    fun hasPremium(row: EntitlementRow?): Boolean {
        val premium = SubscriptionEntitlement.hasPremiumAccess(
            subscriptionStatus = row?.subscription_status,
            trialEndIso = row?.trial_end,
        )
        MandateDebugLog.entitlementCheck(
            source = "MandateEntitlementResolver",
            subscriptionStatus = row?.subscription_status,
            trialPaid = row?.trial_paid,
            trialEnd = row?.trial_end,
            hasPremiumAccess = premium,
        )
        return premium
    }
}
