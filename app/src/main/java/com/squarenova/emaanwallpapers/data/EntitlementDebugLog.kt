package com.squarenova.emaanwallpapers.data

import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.util.SecureLog

/** Entitlement + navigation tracing (debug only). */
object EntitlementDebugLog {

    private const val TAG = "ENTITLEMENT_DEBUG"

    fun check(
        subscriptionStatus: String?,
        trialEnd: String?,
        hasPremiumAccess: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        log("status=${subscriptionStatus ?: "null"} hasPremium=$hasPremiumAccess")
    }

    fun navigation(
        source: String,
        destination: String,
        subscriptionStatus: String?,
        trialEnd: String?,
        hasPremiumAccess: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        log("source=$source navigate=$destination hasPremium=$hasPremiumAccess")
    }

    private fun log(message: String) {
        try {
            SecureLog.i(TAG, message)
        } catch (_: Throwable) {
            // JVM unit tests — no Android Log runtime
        }
    }
}
