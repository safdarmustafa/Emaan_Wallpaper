package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
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

    suspend fun fetchRow(phone: String): EntitlementRow? {
        val cleanPhone = phone.trim()
        if (cleanPhone.isBlank()) return null
        return try {
            SupabaseClient.client
                .postgrest["users"]
                .select { filter { eq("phone_number", cleanPhone) } }
                .decodeList<EntitlementRow>()
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun needsTrialActivation(row: EntitlementRow?): Boolean {
        if (row?.trial_paid != true) return false
        return row.subscription_status?.trim()?.lowercase() == "authenticated"
    }

    /**
     * If mandate is authenticated but DB is not yet "trial", call activate-trial.
     * @return latest row after optional activation
     */
    suspend fun ensureTrialActivatedIfNeeded(phone: String, row: EntitlementRow?): EntitlementRow? {
        if (!needsTrialActivation(row)) return row

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

        return if (result.isSuccess) fetchRow(phone) else row
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
