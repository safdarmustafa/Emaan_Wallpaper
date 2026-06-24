package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.util.SecureLog

/**
 * Structured logs for mandate → trial → entitlement tracing (debug only).
 */
object MandateDebugLog {

    private const val TAG = "MANDATE_DEBUG"

    fun entitlementCheck(
        source: String,
        subscriptionStatus: String?,
        trialPaid: Boolean?,
        trialEnd: String?,
        hasPremiumAccess: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        SecureLog.i(
            TAG,
            "source=$source status=${subscriptionStatus ?: "null"} " +
                "trial_paid=${trialPaid ?: "null"} hasPremium=$hasPremiumAccess",
        )
    }

    fun verifyMandatePoll(attempt: Int, maxAttempts: Int, subscriptionId: String, razorpayStatus: String?) {
        if (!BuildConfig.DEBUG) return
        SecureLog.d(
            TAG,
            "poll $attempt/$maxAttempts sub=${SecureLog.redactId(subscriptionId)} " +
                "status=${razorpayStatus ?: "null"}",
        )
    }

    fun verifyMandateResult(subscriptionId: String, success: Boolean, razorpayStatus: String?, error: String?) {
        if (!BuildConfig.DEBUG) return
        SecureLog.i(
            TAG,
            "mandateResult success=$success sub=${SecureLog.redactId(subscriptionId)} " +
                "status=${razorpayStatus ?: "null"}",
        )
    }

    fun activateTrialRequest(phone: String) {
        if (!BuildConfig.DEBUG) return
        SecureLog.i(TAG, "activateTrial phone=${SecureLog.redactPhone(phone)}")
    }

    fun activateTrialResponse(phone: String, success: Boolean, trialEnd: String?, error: String?) {
        if (!BuildConfig.DEBUG) return
        SecureLog.i(TAG, "activateTrial success=$success phone=${SecureLog.redactPhone(phone)}")
    }

    fun syncResult(phone: String, hasPremium: Boolean, subscriptionStatus: String?, trialEnd: String?) {
        if (!BuildConfig.DEBUG) return
        SecureLog.i(
            TAG,
            "sync hasPremium=$hasPremium phone=${SecureLog.redactPhone(phone)} " +
                "status=${subscriptionStatus ?: "null"}",
        )
    }

    fun note(message: String) {
        SecureLog.d(TAG, message)
    }
}
