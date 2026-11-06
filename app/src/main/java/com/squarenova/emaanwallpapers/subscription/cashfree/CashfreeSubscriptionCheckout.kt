package com.squarenova.emaanwallpapers.subscription.cashfree

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSubscriptionSession
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.subscription.upi.CFSubsUpi
import com.cashfree.pg.core.api.subscription.upi.CFSubsUpiPayment
import com.cashfree.pg.core.api.utils.CFUPIUtil
import com.squarenova.emaanwallpapers.BuildConfig

/** Installed UPI app entry from Cashfree [CFUPIUtil.getInstalledUPIApps]. */
data class InstalledUpiApp(
    val packageName: String,
    val displayName: String,
    val iconBase64: String,
)

/**
 * Launches Cashfree Subscription **UPI Intent** using SDK 2.5.0 APIs.
 * Does NOT unlock Premium — callers must verify via subscription-status.
 */
object CashfreeSubscriptionCheckout {

    /** Fetches UPI apps that can handle `upi://pay`. Callback always on main thread. */
    fun fetchInstalledUpiApps(
        context: Context,
        onResult: (List<InstalledUpiApp>) -> Unit,
    ) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        CFUPIUtil.getInstalledUPIApps(appContext) { apps ->
            val mapped = apps
                .mapNotNull { app ->
                    val packageName = app.appId?.trim().orEmpty()
                    if (packageName.isEmpty()) return@mapNotNull null
                    InstalledUpiApp(
                        packageName = packageName,
                        displayName = app.displayName?.trim().orEmpty().ifBlank { packageName },
                        iconBase64 = app.base64Icon.orEmpty(),
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.displayName.lowercase() }
            mainHandler.post { onResult(mapped) }
        }
    }

    /** Opens the selected UPI app for Cashfree Subscription authorization via Intent. */
    fun launchUpiIntent(
        activity: Activity,
        subscriptionId: String,
        subscriptionSessionId: String,
        upiAppPackageName: String,
    ): Result<Unit> {
        val packageName = upiAppPackageName.trim()
        if (packageName.isEmpty()) {
            return Result.failure(IllegalArgumentException("UPI app package is empty"))
        }
        if (subscriptionId.isBlank() || subscriptionSessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Subscription session is incomplete"))
        }

        return try {
            val session = CFSubscriptionSession.CFSubscriptionSessionBuilder()
                .setEnvironment(resolveEnvironment())
                .setSubscriptionSessionID(subscriptionSessionId)
                .setSubscriptionId(subscriptionId)
                .build()

            val subsUpi = CFSubsUpi.CFSubsUpiBuilder()
                .setMode(CFSubsUpi.Mode.INTENT)
                .setUPIID(packageName)
                .build()

            val payment = CFSubsUpiPayment.CFSubsUpiPaymentBuilder()
                .setSubscriptionSession(session)
                .setSubsUpi(subsUpi)
                .build()

            CFPaymentGatewayService.getInstance().doSubscriptionPayment(activity, payment)
            Result.success(Unit)
        } catch (e: CFException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveEnvironment(): CFSubscriptionSession.Environment =
        when (BuildConfig.CASHFREE_ENV.trim().uppercase()) {
            "PRODUCTION", "PROD" -> CFSubscriptionSession.Environment.PRODUCTION
            else -> CFSubscriptionSession.Environment.SANDBOX
        }
}
