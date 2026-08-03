package com.squarenova.emaanwallpapers.analytics.providers

import android.content.Context
import android.os.Bundle
import com.facebook.appevents.AppEventsLogger
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsProvider
import java.math.BigDecimal
import java.util.Currency

class MetaAnalyticsProvider(
    context: Context
) : AnalyticsProvider {

    private val logger = AppEventsLogger.newLogger(
        context.applicationContext
    )

    override fun track(
        event: String,
        props: Map<String, Any?>
    ) {
        // Intentionally no-op: Meta receives only Standard Events
        // (StartTrial via startTrial, Purchase via purchase, plus SDK
        // Activate/Deactivate App from AppEventsLogger.activateApp).
    }

    override fun purchase(
        amount: Double,
        currency: String,
        props: Map<String, Any?>,
    ) {
        if (amount <= 0.0 || currency.isBlank()) return

        val bundle = propsToBundle(props)

        try {
            logger.logPurchase(
                BigDecimal.valueOf(amount),
                Currency.getInstance(currency.trim().uppercase()),
                bundle,
            )
        } catch (_: Exception) {
            // Invalid currency or SDK failure must never affect app functionality.
        }
    }

    override fun startTrial(props: Map<String, Any?>) {
        try {
            logger.logEvent(
                AnalyticsEvents.START_TRIAL,
                propsToBundle(props),
            )
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }

    private fun propsToBundle(props: Map<String, Any?>): Bundle {
        val bundle = Bundle()

        props.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putFloat(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Long -> bundle.putLong(key, value)
            }
        }

        return bundle
    }

    override fun identify(userId: String) {
        // Meta SDK does not support direct identify like Mixpanel.
        // Advanced Matching can be added in the future if required.
    }

    override fun reset() {
        // No reset API required for Meta.
    }

    override fun flush() {
        logger.flush()
    }
}