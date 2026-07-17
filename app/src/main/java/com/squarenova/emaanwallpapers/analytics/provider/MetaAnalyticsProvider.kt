package com.squarenova.emaanwallpapers.analytics.providers

import android.content.Context
import com.facebook.appevents.AppEventsLogger
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsProvider
import android.os.Bundle
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

        logger.logEvent(
            event,
            bundle
        )
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
            // Invalid currency or SDK failure must not affect app functionality.
        }
    }

    override fun startTrial(props: Map<String, Any?>) {
        logger.logEvent(
            AnalyticsEvents.START_TRIAL,
            propsToBundle(props),
        )
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

        // Meta SDK does not support direct identify like Mixpanel
        // You can later use advanced matching if needed
    }

    override fun reset() {

        // No reset API needed for Meta currently
    }

    override fun flush() {

        logger.flush()
    }
}